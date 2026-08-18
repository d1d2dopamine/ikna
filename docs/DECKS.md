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

## The catalogue

Since `0.5.0 press` there is a third way to get a deck, and it is the one that
asks nothing of anybody: **plus → catalogue**. The screen fetches one small index,
lists finished decks, and imports the one that is tapped through the same path a
pasted deck goes through.

What is on a row before anything is downloaded: the title, the pair, how many
cards, how many megabytes, the licence, who is credited, and which corpora it was
cut out of. Nothing about a deck's terms is discovered afterwards, because a
licence found after a download is a licence somebody already broke.

The filters run on the phone over that one index — what you are learning, what the
meanings should be in, the topic, the level, and a search box — so trying six
combinations costs one request in total. A pair is marked `full` when the pipeline's
sieve dropped little, `thin` when decks came out smaller than asked for, and a pair
the catalogue has nothing for says so instead of showing an empty list.

Three things about it worth knowing:

- **A catalogue deck is a deck.** It lands in the decks list, can be switched off,
  exported, and deleted like any other. Its identifier starts with `catalog-`, so it
  can never overwrite something you imported yourself, and downloading it twice
  replaces it rather than making a second copy.
- **The phrase was cut, not written.** The card's phrase comes out of its own
  sentence, at stored offsets, which is why a catalogue deck cannot contain the one
  fault a pasted deck sometimes has — a phrase that is not in its sentence.
- **The credit travels inside the file.** Each meaning ends with a line naming the
  corpus and the sentence number, the same `— source` mark a hand-made deck's fourth
  column produces, so it survives an export and a share.

The corpora, the licences, the sieve, the tiers and why AnkiWeb is not used:
[`SOURCES.md`](SOURCES.md).

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

## Languages and subjects

A deck is either about a **language** or about a **subject**, and the add-deck
screen asks which before it asks anything else. The answer changes three things
and nothing more:

| | Language deck | Subject deck |
| --- | --- | --- |
| Prompt | `prompt/deck_prompt.txt` | `prompt/subject_prompt.txt` |
| Voice | the deck's language, and speech if a model is installed | none; nothing in it is meant to be pronounced |
| Levels | recognise, complete, **say it out loud** | recognise, complete |
| Order of new cards | frequency first, so common words come first | the order written in the file |

The core never needed to know. A card has always been a unit, one sentence that
carries it, and what it means -- which describes a definition in neuroscience as
well as a phrase in Polish. Only four things were language-shaped, and all four
are now decided by the deck: the prompt, the voice, the third level, and the
order.

The third level is the one that matters. It asks the person to produce the phrase
from its meaning, out loud, which is a question about a language. A definition has
no pronunciation to produce, so a subject deck stops at the second level and the
day's budget is not spent on a question that cannot be answered.

The order matters almost as much. In a language deck new chunks arrive by
frequency, because the commonest words are the ones worth knowing first. A subject
is a curriculum: line 40 may be meaningless before line 39, so a subject deck
introduces its cards in the order the file was written and the prompt says so in
plain words. When both kinds of deck are active, new cards are split between them
rather than taken from whichever happens to sort first, so switching to a subject
does not quietly stop a language.

What is deliberately **not** here: images, formulas that need rendering, LaTeX,
diagrams. A card is one line of plain text. Half of mathematics and a good part of
anatomy live in pictures, and pretending otherwise with ASCII art would make cards
that are wrong in a way that is hard to notice. The prompt says the same thing to
the model: if a concept only makes sense with a figure, write the definition it
rests on and point at the figure in the source column.

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

## The fourth column: where the card came from

A line may carry a fourth field:

```
term | a sentence that uses it | what it means | where it comes from
```

It is optional, it is capped at 60 characters, and it is the cheapest defence this
app has against a card that is simply wrong. ikna asks the network exactly one
question -- whether a newer release exists -- and nothing inside it can check a
claim about the world. A source cannot make a card
true either -- but it turns "is this right?" into somewhere to go and look, which is
the most an offline app can honestly offer.

Until the schema gains a column of its own, the source travels at the end of the
meaning, after a dash on its own line. That is a compromise and it is written down
as one: the alternative was to hold the whole idea back for a release that can
migrate the database.

## Pasting a deck of thousands of lines

Use **paste from clipboard**, the button under the field, rather than the
keyboard's own paste. A phone keyboard is built for messages: hand it a hundred
kilobytes of table and it hands back a shorter text with its line breaks gone.
The button reads the clipboard itself, so a ten-thousand-row deck arrives as it
was written.

The text is then folded away: how many lines, how many characters, and **SHOW THE
TEXT** to see the first forty rows. That is the whole preview by design -- nobody
corrects a generated deck with a thumb, and a megabyte of text laid out in an
editable field is what used to put the create button a minute of scrolling away.

If a deck came through the keyboard anyway and its line breaks were eaten, the app
tries the clipboard, then puts the breaks back itself, and only then says what
happened -- naming the flattening rather than blaming line 1.

The breaks are put back one line at a time, because a keyboard flattens in
patches: a deck of three hundred rows usually arrives as a handful of enormous
lines rather than as a single one, and a rescue that only looked at texts of one
line left that case untouched -- every glued line refused for having six hundred
fields, and the one line that happened to hold three becoming the whole import.
A line is only split when the result reads like cards, which is the rule the rest
of this file rests on: a phrase appears inside its own sentence. A line with a
stray bar in it fails that test, stays one line, and is reported as the mistake it
is instead of being quietly cut into halves of sentences.

## Lines worth reading twice

A deck can now be written by a model in half a minute, and a model pads. Four
things are counted on import and reported next to the number of cards added:

| Flag | What it means |
| --- | --- |
| the meaning repeats the term | "mitochondrion -- a mitochondrion in a cell". Teaches nothing. |
| a hedged wording | "probably", "I think". The model saying it does not know, in a card that will be learned as a fact. |
| this meaning already appeared | two terms, one definition. The commonest way a quota gets filled. |
| has numbers | three digits or more: a year, a constant, a dose. What models invent most confidently. |

None of them refuses a line -- the deck installs, all of it. They are the
difference between an import that says "200 cards" and one that says "200 cards,
and seven of them are worth reading before you learn them".

## When a card turns out to be wrong

Under a revealed card there is **this card is wrong**. One tap, no dialog, and:

- the card leaves the rotation -- today's session, the plan, and every future one;
- **nothing is written to the review log**, so the mistake does not count as a
  lapse, does not drag the accuracy the governor reads, and cannot cost you
  tomorrow's new cards;
- if it was introduced today, the day's count of new material is given back.

That last part is the reason the button exists at all. Before it, the only way to
say "this card is nonsense" was to answer *forgot* -- which told FSRS to show the
nonsense more often, pushed the day's accuracy down, and could close new material
for a week over a card the deck got wrong.

The rows themselves are not deleted. The list of marked cards lives in settings
under **Data**, it says how many there are, and one button puts them all back. A
one-tap action with no way back would be data loss in an app whose whole promise
is that nothing is lost.

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
