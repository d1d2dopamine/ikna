# Sources

A deck in the catalogue is not written by a model. It is cut out of two open
corpora, and every chunk in it can be traced back to the sentence it came from,
by number, on a public website. That is the whole point of the catalogue: a model
that invents a sentence cannot be checked, and a sentence with an id can.

This file says which corpora, under which licence, how a chunk is built out of
them, which languages that works for, and what the app is allowed to do over the
network. The format the catalogue is written in is `PackChunk`, the same one the
shipped decks use — the catalogue adds no new reader to the app.

## Why not AnkiWeb

It was the obvious first idea and it does not survive contact with the site.

AnkiWeb has no licence field. A deck's terms are whatever prose the author typed
into the description, if anything, and the default in AnkiWeb's own terms is a
personal-use licence — extra rights exist only when the author wrote them out by
hand. "Show me only the freely licensed decks" would therefore mean guessing a
licence out of free text, and a wrong guess means this app redistributing someone
else's copyrighted work.

The site also asks not to be read by programs. Its `robots.txt` disallows exactly
three paths — `/shared/decks/`, `/shared/download`, `/shared/downloadDeck` — which
are exactly the three an in-app deck search would need. There is no public API,
by a deliberate decision of Anki's author, and downloads are gated behind a login
and rate-limited.

So AnkiWeb stays what it already is: a place a person can visit in a browser. If
a deck import from an `.apkg` file the user downloaded themselves is ever added,
it belongs in this file too — but it is not the catalogue.

## The two corpora

| Source | What it gives | Licence |
| --- | --- | --- |
| [Tatoeba](https://tatoeba.org/en/downloads) | one natural sentence, and its translation | CC BY 2.0 FR, with a CC0 subset |
| [Wiktionary](https://kaikki.org/dictionary/), extracted by Wiktextract | which written form belongs to which dictionary word — read at build time, never copied into a deck | CC BY-SA 4.0 and GFDL |
| the corpus itself | the order new chunks arrive in, counted over the sentences the decks are cut from | as above |

Tatoeba's stated goal is that someone else builds the application: the project
collects sentences, keeps them free, and expects them to be downloaded and
redistributed. Nobody has to be asked for permission. The obligations are the
ordinary ones — credit, licence, and a note that the material was changed.

Wiktionary is ShareAlike, and that is exactly why none of its text is put in a
deck. It is read while the deck is being built, to answer one question — is
"словами" the same word as "слово" — and the answer is a decision, not a sentence.
What ends up in the file is Tatoeba's text and only Tatoeba's text, so a deck
carries Tatoeba's licence alone: **CC BY 2.0 FR**, with credit and a note that the
material was rearranged. ShareAlike would bind a deck's content and never this
app's source code, but as things stand it does not arise.

**One deck, one licence.** Nothing is glued together across licences inside a
single deck. A deck built only from the CC0 sentence subset says CC0 and needs no
attribution at all; everything else says CC BY 2.0 FR and carries it. The line is
written into the index, shown before the download, and appended to every card's
meaning, so it survives being exported and sent on.

## How a chunk is built

A chunk is a short phrase, one sentence that contains it, and what the phrase
means. Each field has exactly one source:

| Field | Where it comes from |
| --- | --- |
| `text` | the phrase, **cut out of the sentence as it is spelled there** |
| `context` | a Tatoeba sentence in the deck's language |
| `translation` | the Tatoeba sentence linked to this one as a **direct** translation, plus a line naming the sentence by number |
| `targetStart`, `targetEnd` | where the cut was made |
| `freqRank` | how common the phrase is in the corpus this deck was cut from |
| `tokens` | the words of the sentence; the dictionary form comes from Wiktextract when it is available and from lowercasing when it is not |
| `audioRef` | nothing yet; Tatoeba audio has per-recording licences and is left alone |

The fourth row is the trick that makes the whole thing work without a morphology
engine on the phone. The pipeline does not look up a dictionary form and hope it
appears in the sentence; it takes the word **from the sentence** and stores the
offsets it took it at. A phrase that was cut out of a sentence is inside that
sentence by construction, so the import check that a phrase must occur in its
context cannot fail, in any language, ever.

Inflection is still needed, but only to find candidates: to build a chunk for
"слово" the pipeline needs to know that "словами" is the same word. Wiktextract
supplies those forms, offline, at build time.

## The sieve

Tatoeba is crowdsourced and says so. Sentences by non-native writers can be
unnatural, and translations can be wrong. The sieve is not taste, it is the
corpus's own metadata:

- **direct translations only** — no pairs assembled through a third language,
  which is where the drift comes from
- **native owner** — the sentence's author is marked as a native speaker of its
  language
- **not flagged unreliable**
- **length inside the app's limits**, and short by preference
- **the phrase occurs once** in the sentence, so the card has one answer
- **no duplicates**, and one sentence used for one chunk only

Everything the sieve drops is a phrase with no card. That is why a deck's size is
a result and not a promise, and why the language tiers below exist.

## Languages

The app already knows nothing about language, and the catalogue keeps it that
way: a pair is offered when the data supports it and absent when it does not. It
is a matrix and not a list, because a Spaniard learning English and a Pole
learning French are the ordinary case and not an afterthought.

The two sides of the matrix are not the same length, and the difference is not an
oversight:

- **learned:** English, Russian, Polish, Spanish, French, German, Italian,
  Portuguese. Eight, because a phrase is cut on word boundaries.
- **meanings in:** those eight plus Chinese and Japanese. Ten, because a
  translation is shown whole and never cut, so a language that cannot yet be
  taught can still be the language somebody already knows.

Two facts shape it, and both are properties of the corpora rather than of this
code:

1. **Tatoeba is English-centred.** English has by far the most links; a pair with
   English on either side has hundreds of thousands of direct translations, and a
   pair like Polish to French has a fraction of that.
2. **Only direct links count.** A pair assembled through a third language drifts,
   so a sentence with no direct translation in the wanted language is a dropped
   card even when the corpus has both languages. This is what makes a pair like
   Polish to Portuguese thin while both languages are well served through
   English.

So each pair lands in one of three tiers, and the tier is computed from the data
and published with the catalogue rather than decided here:

| Tier | What it means |
| --- | --- |
| Full | decks of any size; the sieve drops little |
| Thin | the pair works, but decks come out smaller than asked for |
| Not yet | the pair is not in the catalogue at all |

"Not yet" is, for the language being **learned**, every language a phrase cannot
be cut out of reliably: those written without spaces between words, such as
Chinese, Japanese and Thai, and those where the written form drops vowels, such as
Arabic. Cutting by character offsets there produces half a word, and half a word
is worse than no card. They come back when the pipeline gains a segmentation
step — and in the meantime Chinese and Japanese are already available as the
language the **meanings** are in, which costs nothing and needs no segmenter.

The tier of every pair is computed by the pipeline, written into the index and
printed as a table beside the catalogue release, so the README's table is
generated from a build rather than maintained by hand.

## Where the catalogue is built

Not on the phone. The raw material is hundreds of megabytes compressed and
gigabytes unpacked; a phone would spend minutes of processor time and a mobile
data plan to produce a few hundred cards, and every phone would produce them
again.

The pipeline runs in CI, on demand, in this repository:

| File | What it is |
| --- | --- |
| `tools/catalog/build_catalog.py` | the whole pipeline: reads the dumps, sieves them, writes the decks, the index and the tier table |
| `tools/catalog/make_sample.py` | a hundred and twenty made-up sentences in Tatoeba's shape, so the pipeline can be run end to end in a second |
| `.github/workflows/catalog.yml` | runs the sample first, then the real build, then attaches the result to one release called `catalog` |

The sample run is not decoration. It builds decks from invented sentences and then
checks that every card's phrase sits exactly at the offsets stored beside it, so a
mistake in the cutting fails in a minute instead of being found after a
forty-minute download — or, worse, on somebody's phone.

That release is deliberately not marked as the latest one: the app asks GitHub for
the latest release to find out whether a new version exists, and a catalogue
holding that title would offer people an update with no APK in it.

No server is maintained, no account exists, and the build is reproducible from a
log anybody can read.

## What the app does

The app reads static files over HTTPS and nothing else:

1. the **index** — one small file listing each deck's id, title, language pair,
   chunk count, subject, level, size, licence, and attribution line;
2. an optional **preview prefix** — three complete JSONL lines, requested with
   HTTP Range and stopped locally after 96 KiB even if the range is ignored;
3. the **deck** — one file, downloaded when a person taps it, with a progress bar
   and a percentage, the same download panel the updater uses.

One English-from-Russian beginner deck is copied into the APK at build time. It is
not fetched by the phone: both build workflows run
`tools/catalog/fetch-bundled-pack.sh`, which pins the release asset by byte size and
SHA-256 and validates every line before Gradle packages it.

Filters run over the index, on the device, so choosing among decks costs nothing
and works with the phone in a pocket of a train. The licence and the source of a
deck are shown **before** it is downloaded, not buried in an about screen, and
the deck's own file carries them afterwards so they survive an export.

The charter still holds: no account, no identifier, nothing about the person
leaves the phone. Downloading a deck is a request for a file, the same shape as
checking for an update.

## What is not claimed

The decks are not reviewed by us and are not certified as correct. The claim is
narrower and checkable: every chunk names the corpus, the contributor and the
sentence it came from, under a licence that allows it to be there. The session,
preview and local search all render that sentence id as a link. Marking a catalogue
card wrong can copy a report containing the public card and source, but never the
review state or device data. A card that looks wrong can therefore be looked up,
argued with, and fixed upstream — which is the part a generated deck can never
offer.
