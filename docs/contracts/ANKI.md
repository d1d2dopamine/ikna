# Anki

How the `.apkg` bridge works and what it refuses. The import is one way: there
is no `.apkg` writer, for the reasons at the end.

## The path through the code

```
AnkiImportScreen  → pick a file (SAF); nothing else is asked
AnkiImportManager → owns the run, holds the report
AnkiImporter      → copy → unzip → open → read → map → commit
AnkiCollection    → which schema the collection is in, and its notetypes
AnkiProto         → the protobuf blobs a modern schema keeps notetypes in
DeckLanguage      → what language each deck turned out to be in
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

Two collection shapes are read, and which one this is gets settled before the
first write.

- **Classic.** Everything is in `col`: `crt`, `scm` and JSON `models` / `decks`.
- **Schema 18**, what a current Anki writes. Those two columns are left empty and
  the same information lives in `notetypes`, `fields`, `templates` and `decks`,
  with the parts that are not columns encoded as protobuf blobs. Deck names nest
  with `U+001F` there, which is turned back into `::`.

`AnkiCollection` tries the JSON first, then the tables, and refuses with
`UNSUPPORTED_COLLECTION` when neither yields a notetype. `AnkiProto` reads exactly
two things out of the blobs: whether a notetype is cloze (`NotetypeConfig.kind`,
field 1) and the two sides of a template (`CardTemplateConfig.q_format` and
`a_format`, fields 1 and 2). It is not a protobuf library: it walks tag/value
pairs, skips what it was not asked for, and returns null on anything that does not
add up, which every caller already had to handle for a missing notetype.

`notes`, `cards` and `revlog` are the same in both shapes, and fields inside a
note are separated by `U+001F`.

## Which language a deck is in

Nothing is asked at import. `DeckLanguage` decides per deck, in this order:

1. A language named in the deck title wins, exam names included (JLPT, HSK,
   IELTS, TestDaF). Somebody typed that name on purpose.
2. Otherwise the cards decide: a non-Latin script answers by itself, and Latin
   script is settled by accented letters and by the short function words a
   language cannot write around.
3. Cards and meanings in the same language, and that language is the one the app
   is read in, means a subject rather than a language — `NO_LANG`, which keeps a
   voice from reading definitions aloud and keeps the ladder from demanding a
   definition back word for word.
4. Cards nothing can name, with meanings in the reader's own language, are a
   language deck with no name yet: `und`.
5. When nothing can be told apart, `NO_LANG`. It is the smaller mistake: it
   withholds one step of the ladder, while a wrong language puts a voice and the
   wrong alphabet behind every card in the deck.

The report lists what each deck was decided to be, and the deck's own page has
the language chips, so a wrong guess is one tap where the deck already is.

Supported templates: Basic, reversed, cloze, and ordinary custom text. Card HTML
is converted to text by `AnkiText`: tags are dropped, entities decoded, `<br>` and
block ends become line breaks, and `script`, `style` and comment contents are
removed with their bodies. **No WebView is created anywhere in the import path,**
so an imported script has nothing to run in even if a future change let one
through as text.

## When the notetypes cannot be read

A file can arrive in a shape this reader has never seen: a container named
something new, a protobuf field renumbered, a blob that lost a byte. In all of
them the notes themselves are still plain text in a table SQLite can read, so
the import does not stop. Every package member named like a collection is tried
in turn, newest container first, and the first one that opens as SQLite wins.
If neither the JSON columns nor the tables give up a notetype, each note is read
by the order of its own fields -- first field to the front, the rest to the back,
cloze recognised from the text itself -- and those cards are counted as recovered
from fields in the report. Only a file with no notes and no cards at all is
refused.

## What is refused, and counted

A silent skip is a bug. Everything below appears in the report shown *before*
anything is written.

- **Suspended and buried cards** stay behind.
- **Media is not imported.** `[sound:…]` and `img` collapse to `[audio]` and
  `[image]` markers. A card whose only content is a picture or a sound has no text
  to train, so it is skipped and reported as skipped.
- **Unsupported collection schemas** are refused *before the first write*, with
  `UNSUPPORTED_COLLECTION` rather than a generic failure.
- **A package that yielded nothing but Anki's own “please update” card** is
  refused with `PLACEHOLDER_COLLECTION`. Preferring `collection.anki21b` by name
  already avoids the decoy; this is the second line of defence.
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

## What an imported deck looks like afterwards

It arrives switched off, like any other new deck, and shows no progress. Progress
through a deck counts what has been answered in ikna since the deck arrived, not
how many card rows exist -- an imported deck has a card for every note from the
moment it lands, and counting rows made it look finished. The replayed schedule
is not affected by either: those cards come back when their own history says
they should, once the deck is switched on.

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
