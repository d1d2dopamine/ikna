# Anki

How the `.apkg` bridge works and what it refuses. The import is one way: there
is no `.apkg` writer, for the reasons at the end.

## The path through the code

```
AnkiImportScreen  → pick a file (SAF), pick the language it teaches
AnkiImportManager → owns the run, holds the report
AnkiImporter      → copy → unzip → open → read → map → commit
AnkiText          → card HTML → plain text
RestoreRepository → replay the imported answers through FSRS-6
```

The importer never touches the file the user picked. It copies the bytes out of
the content URI into `cacheDir` first, works there, and deletes what it made. The
original is opened read-only and is not modified even on success. Nothing in the
import path opens a socket.

## What is read

The package is a ZIP. The collection is the first member found of:

| Member | Notes |
| --- | --- |
| `collection.anki21b` | Zstandard-compressed; preferred when present |
| `collection.anki21` | |
| `collection.anki2` | in a modern package this is a decoy — see below |

Anki 2.1.50 and later ship a **fake** `collection.anki2` holding a single note
that says to update Anki and import again. Reading members in the order above is
therefore not a preference but a correctness requirement: taking the last one
would import that warning as a flashcard.

Inside, the bridge expects the classic schema — a `col` row carrying `crt`, `scm`
and JSON `models` / `decks`, plus `notes`, `cards` and `revlog`. Fields inside a
note are separated by `U+001F`.

Supported templates: Basic, reversed, cloze, and ordinary custom text. Card HTML
is converted to text by `AnkiText`: tags are dropped, entities decoded, `<br>` and
block ends become line breaks, and `script`, `style` and comment contents are
removed with their bodies. **No WebView is created anywhere in the import path,**
so an imported script has nothing to run in even if a future change let one
through as text.

## What is refused, and counted

A silent skip is a bug. Everything below appears in the report shown *before*
anything is written.

- **Suspended and buried cards** stay behind.
- **Media is not imported.** `[sound:…]` and `img` collapse to `[audio]` and
  `[image]` markers. A card whose only content is a picture or a sound has no text
  to train, so it is skipped and reported as skipped.
- **Unsupported collection schemas** are refused *before the first write*, with
  `UNSUPPORTED_COLLECTION` rather than a generic failure.
- **Ceilings**, all deliberate: 300 MiB package, 512 MiB extracted collection,
  20,000 ZIP entries, 50,000 active cards, 100,000 newest review rows, 120,000 ms
  per recorded answer. A single answer longer than two minutes is a phone left on
  a table, not a hard card.

Media is the one people ask about, so the reasoning is written down rather than
implied: a deck of images and audio is hand-built homework, individual to whoever
built it, and building it is exactly the work this app exists to remove. Importing
binary media would also mean carrying an asset store, a cache budget and a
rewrite path for every reference. The honest marker and the honest count are the
feature.

## History

Up to the newest 100,000 usable review events are mapped onto ikna's review log
and replayed through FSRS-6.

**The scheduler is not modified and no second scheduler is added.** The import
produces answers; the existing replay derives every schedule from them, exactly as
it does when restoring a backup. This is the same rule as everywhere else in the
app: the review log only ever gains rows.

What crosses over is the rating and the timing. Anki's internal scheduling state
does not, because it belongs to a different algorithm and inventing an equivalent
would be a guess wearing a number's clothes. The report says this plainly instead
of implying a perfect transplant.

## Identity, and importing twice

```
collectionKey = crt, or scm / 1000, or 1
pack id       = anki-<collectionKey>-deck-<deckId>
chunk id      = anki-<collectionKey>-card-<cardId>
```

Because the ids are derived rather than generated, importing the same collection
twice replaces its own decks instead of duplicating them — the failure mode Anki's
own importer had until 2023. Cleanup is scoped to packs carrying that prefix: a
deck that came from anywhere else is never touched, and neither are its answers.

The whole commit — pack replacement, imported answers, replay, plan invalidation —
is one transaction. A package that fails halfway leaves nothing behind. There is
no partial import.

## Errors the user can actually see

`FILE_TOO_LARGE`, `NOT_APKG`, `NO_COLLECTION`, `UNSUPPORTED_COLLECTION`,
`UNREADABLE_DATABASE`, `NO_USABLE_CARDS`, `FAILED` — each with its own sentence in
all six locale tables. "Something went wrong" is not an acceptable outcome for
someone's decade of reviews.

## Why there is no export

ikna does not write `.apkg`, and the JSONL backup is not labelled as an Anki
export.

The format is not documented for outside writers; upstream's own advice is to use
Anki's Python API. It changed inside a patch release, the modern collection is
Zstandard-compressed with a protobuf media map, and the compatibility decoy above
exists precisely because third-party tooling kept guessing wrong. Every working
writer in the wild is Python or Go. An exporter that produced a file which made
Anki ask to repair its database would be worse than no exporter, because it would
look like it worked.

Answers leave through the append-only export in `Documents/ikna/`, outside the app
sandbox, so they survive an uninstall. Anki officially imports tab-separated
plain text, which is the supported road out if one is wanted later.

## Testing

`AnkiBridgeContractTest` pins the identity contract and the ceilings;
`AnkiTextTest` pins the HTML conversion, including the cases where a script tag
must take its body with it. Both are plain JVM unit tests — no device, no fixture
download.
