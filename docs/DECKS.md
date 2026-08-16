# Decks

A deck is a pack of chunks. A chunk is a short phrase, one natural sentence that
contains it, and what the phrase means — not a word and its translation.

| Deck | Chunks | Shipped |
| --- | --- | --- |
| `en-ru-core` — English core chunks | 121 | on |
| `pl-ru-core` — Polish core chunks | 121 | off |

A second language ships **off** on purpose: two active decks interleave two
languages inside one session, and the switch lives in the deck list. Everything
else about a Polish chunk is identical to an English one — same three levels, same
FSRS state, same governor, same component layer — because a chunk is content, and
none of the machinery knows what language it is looking at.

Turning a deck off only stops **new** chunks coming from it. Everything already
started keeps its schedule and its history, so switching decks is never a decision
with consequences — and a decision with consequences is a decision that gets
avoided.

## Making your own

The plus in the bottom bar opens a screen, not a file browser. On it:

| Button | What it does |
| --- | --- |
| **Copy the prompt** | Puts a written brief on the clipboard. Send it to any AI, tell it the language and the topic, and it answers with deck lines. |
| **Save the prompt as a file** | The same brief as `ikna-deck-prompt.txt`, for models that take an attachment. |
| **Add from text** | Paste the answer straight into the field and import it. No file involved. |
| **Pick a file** | For an answer that was saved instead of copied. |

The prompt is the point of that screen. Writing a deck by hand is the same
homework that makes Anki expensive to start; the app hands the routine to a model
and keeps the reading for the person.

## The prompt is filled in here, not in the chat

The prompt ends with the questions a deck depends on: the language being learned,
the language of the meanings, how many cards, the topic, the level. Those lines
used to be blank, and were meant to be typed into a chat window by hand -- which
is setup, on a phone, before a single card has been learned. Sent blank, they
come back as a deck in the wrong language or as a question instead of a deck.

The add-deck screen asks them in chips and writes the answers into the prompt
before it reaches the clipboard: the meanings default to the language the app is
in, the count to a hundred, the level to beginner, and the topic is one short
line. A question left alone stays blank rather than filled in with "none" -- a
model reads "none" as an instruction and a blank line as silence.

The prompt itself is an asset, in English, addressed to a model rather than to a
person, and nothing else in it is rewritten. If a question is ever renamed there,
the answer with nowhere to go is dropped and the prompt still goes out whole.

## The format

A deck is plain text. One line is one card, three parts separated by a bar:

```
get used to | It takes a while to get used to the noise. | to grow accustomed
```

The phrase has to appear inside the sentence, because the sentence *is* the card
and the phrase is the part of it being learned. Everything else — the token split,
the character offsets, the frequency order — is worked out on import.

Tabs are accepted instead of bars, for text pasted out of a spreadsheet.

What the importer forgives, because a language model writes it and it is not the
user's mistake to clean up by hand: code fences, `#` comments, `-` and `*`
bullets, `1.` and `2)` numbering, and the pipes and rule lines of a Markdown
table — including a number written inside the first cell of one.

What it refuses, per line, with the line number and the line quoted back: fewer or
more than three fields, an empty field, a phrase that does not occur in its own
sentence, a phrase over 80 characters, a sentence over 300, a translation over
160, and a phrase that already appeared earlier in the same file. A file that
produced nothing says why instead of failing silently.

A deck may hold up to ten thousand rows. The limit is a ceiling, not a target:
the governor still lets only a handful of new cards out per day, so a deck of ten
thousand is a supply that lasts years rather than a backlog that has to be faced.

`.jsonl` packs from `tools/genpack` still import unchanged; the format is
recognised from the text rather than from the file name. The file picker no longer
offers photos and video — it used to ask for `*/*`, which meant the app would
happily read a two-gigabyte video into a string and die. Anything above four
megabytes is refused before it is opened.

## What a pasted deck does not get

Only the words inside the trained phrase carry weight in the component layer. The
phone does no lemmatisation and no part-of-speech tagging: those need the tables
that live in the generator, and a guessed lemma is worse than no lemma, because it
merges unrelated words and hands credit to words that were never involved.

The surrounding sentence is still shown, still read aloud and still what makes the
phrase memorable. It just does not earn credit it cannot prove.

## The deck screen

Tapping a deck's name opens it. Tapping the rest of the row starts a session.

- **Language.** A deck you imported has no language until you set one here, and
  without a language there is no speech.
- **Add cards.** A second portion of the same course joins this deck instead of
  becoming another deck with the same name: the button, then the file. The deck
  keeps its own name and its own language, card ids continue from what it already
  holds rather than restarting, and rows whose phrase is already in the deck are
  skipped -- portions overlap, and a duplicate card is a card whose history is
  split in two.
- **Send.** Writes the deck back out as the same three-column text and hands it to
  the share sheet, so a deck made on one phone imports on another.
- **Delete.** Two taps on one button. The cards and the schedule go; the answers
  stay, because the review log is append-only and a deck deleted in a bad mood
  should not take four months of history with it.

## Building a pack offline

Both shipped packs are built by the same generator, from a three-column TSV
(`phrase`, `carrier sentence`, `translation`):

```
python3 tools/genpack/generate_pack.py \
  --seed tools/genpack/seed_chunks_pl.tsv \
  --out app/src/main/assets/packs \
  --pack-id pl-ru-core --lang pl --title "Polish core chunks" --inactive --strict
```

Polish is tokenised but deliberately **not** lemmatised, for the reason above.
Surface forms aggregate less, but they never lie.
