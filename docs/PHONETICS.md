# Phonetics

A chunk in a catalogue deck can carry how it is pronounced. This file says what
is stored, why it is stored in one notation and shown in another, which tools
produce it, what the result is not good enough for, and how to build a catalogue
that has it — because the one currently published does not.

## The problem this solves

A deck teaches a phrase, a sentence it lives in, and what it means. For a
language written in an alphabet the learner already reads, that is nearly
enough: a Russian speaker looking at `dziękuję` can see the letters. Seeing the
letters is not the same as knowing what they say. `dzię-` is not read the way
any of its letters would suggest to somebody who has only ever read Cyrillic or
English, and there is nothing on the card to tell them so.

The app already has one answer to this and it is not sufficient. Speech reads
the phrase aloud, correctly, in a real voice. But speech is off by default and
will stay that way, because a phone that starts talking in a quiet room is a
reason to put the phone down; it needs a voice pack installed for the language;
and it is gone the instant it finishes. A written pronunciation is silent, needs
nothing installed, and stays on screen for as long as the card does.

## Two notations, one stored

The deck file stores **IPA**. The screen shows, by default, an **English
respelling** computed from that IPA on the device.

This split is the central decision here, and both halves of it are load-bearing.

### Why IPA in the file

IPA is what the tools produce. Every grapheme-to-phoneme system worth using
emits it, it is unambiguous, and it is the only notation in which a Polish
transcription and a Portuguese one mean the same thing by the same symbol. If
the file stored a respelling, the deck would be frozen to whatever respelling
scheme was fashionable when it was built, and improving the scheme would mean
rebuilding and redownloading every deck. Storing IPA means the scheme is a
property of the app, not of the content: a better renderer ships in an APK and
improves decks that were downloaded a year earlier.

### Why not show the IPA

Because almost nobody can read it. IPA is a professional notation. `ɕ`, `ʑ`, `ɨ`
and `ɔ̃` are precise and they are also, to a person who wants to know how to say
*thank you* in Polish, four more things they cannot read on a card that already
had one thing they could not read. Replacing an unreadable string with a
different unreadable string is not help.

It is still offered, as one of the three settings, because the minority who *can*
read IPA are exactly the people it serves best, and they are easy to satisfy: the
data is already there.

### Why the respelling is English

This was the user's decision and it is the right one, though it deserves its
reasoning written down because it looks arbitrary at first.

The alternative was to respell into the language the deck's meanings are in — a
Russian respelling for a Polish-from-Russian deck, a Spanish one for
Polish-from-Spanish, and so on. That is more accurate for each reader. It is also
**seventy-two** respelling schemes for seventy-two pairs in the catalogue, each
needing somebody who knows both languages to design and check it, each with its
own conventions, and most of them with no published standard to copy. The Polish→
Russian one exists (Гиляревский & Старостин, 1985) and is genuinely good. The
Polish→Italian one does not exist and would have to be invented here.

English spelling is a bad phonetic notation — it is famously inconsistent — but
it has one property nothing else has: an enormous number of people can read it
approximately right, including people who do not speak English well. `KOO` is
read as *koo* by a Russian speaker, a Spanish speaker and a German speaker alike.
That approximate universality is worth more here than the extra precision of a
scheme only one of them can use.

So: one scheme, English-based, for all eight learnable languages, for all
seventy-two pairs.

### What the respelling costs

It should be said plainly, because the source that defines this style says it
first. Wikipedia's own Manual of Style holds that using an English respelling for
a **non-English** pronunciation is "inadequate and misleading", and it is right.
English has no `ʑ`, no `ɨ`, no nasal vowels and no trilled `r`. Every one of
those is approximated here, and an approximation of a sound the reader's language
does not contain is not the sound.

That objection is accepted rather than argued with. It applies to a project
writing an encyclopaedia, where a wrong pronunciation is published as fact. Here
the alternatives are: an IPA string the reader cannot use, a synthesised voice
they have switched off, or nothing at all. `jeng-KOO-yeh` is not how a Pole says
`dziękuję`. It is very much closer than what the reader would otherwise guess,
and it is honest about being a guide rather than a specification, which is why
the exact notation stays available one tap away.

## The respelling scheme

Modelled on Wikipedia's pronunciation respelling key, with one deliberate
departure and a rule Wikipedia does not need.

**Syllables are separated by hyphens; stressed syllables are in capitals.**
`ob-ree-GAH-doo`. Both strengths of stress are capitalised — Wikipedia does the
same, and inventing a third case would need explaining before it helped. A word
of one syllable is never capitalised, because with nothing to contrast against,
capitals carry no information: `kat`, not `KAT`.

**Output is plain ASCII.** This is the departure. Wikipedia writes the reduced
vowel as `ə`; this writes it `uh`. The reason is the font picker. The app lets a
reader choose the typeface, some of the choices are display faces with small
character sets, and a schwa that renders as a tofu box turns the one line meant
to clear up a pronunciation into the most confusing thing on the card. `uh` costs
a little precision and cannot fail to render.

**A vowel is spelled differently depending on where it sits.** English spelling
has no fixed value for a bare vowel letter: `e` closed by a consonant reads as
*eh*, but `e` at the end of a syllable reads as *ee*. So every vowel in the table
carries two spellings — a closed form and an open one — and the renderer picks
by position. This is why `dʑɛŋˈkujɛ` comes out as `jeng-KOO-yeh`: the same `ɛ`
twice, written `e` when a consonant closes the syllable and `eh` when it does
not. Wikipedia does not need this rule because a human writes each respelling by
hand; a program does.

**Nasal vowels keep a trailing `ng`.** `bɔ̃` becomes `bong`. It is not accurate —
the nasal is in the vowel, not a separate consonant — but a reader who says
*bong* is much closer than one who says *bo*.

**Unknown symbols are dropped, never passed through.** The entire value of the
line is that it can be read aloud. A stray `ʢ` that survived because nothing knew
what to do with it defeats that more thoroughly than a slightly wrong vowel does.

The renderer lives in `domain/phonetics/Respell.kt`. It never throws: bad input
produces a short line or no line.

## Where it appears

On a card, under the phrase, in the muted text colour — smaller than the phrase
and plainly secondary to it.

Which side it appears on is decided by what would otherwise leak the answer:

| Card | Prompt shows | Answer shows |
| --- | --- | --- |
| Recognise | the sentence's transcription | the phrase's, only when there is no meaning to show |
| Gap | nothing | the phrase's |
| Produce | nothing | the sentence's |

A production card asks the learner to recall the phrase. Printing its
pronunciation above the blank would hand them most of the answer, so it is not
printed. This is the same reasoning the app already applies to the target
highlight, and it is why the transcription is chosen in `CardPresentation`, with
the rest of the card's shape, rather than being bolted onto the view.

## The setting

**Per deck, in that deck's settings**, between the language section and the look
section. Three chips: English, IPA, off. A live preview of the deck's language
sits under them, so the choice is made by looking at the result rather than by
reading the labels.

It is deliberately **not** in the app's settings screen. Somebody learning Polish
from Russian needs this on every card; the same person's English-from-Russian
deck, which they are half-way through and reading comfortably, does not need it
at all. One switch for both forces a choice that is wrong for one of them.

It is also deliberately not offered when the deck is downloaded, or when it is
created. A choice presented then is made before the first card has been seen —
before the person has any way of knowing whether they want it. The deck settings
screen is where somebody goes *after* running into the problem.

The section is **absent**, not disabled, when there is nothing behind it: a deck
with no transcription in it, or a deck whose language nothing can transcribe. A
greyed-out control is a question the reader has to answer ("why can't I use
this?"); an absent one is not.

**The default is on, in English respelling.** This differs from speech, which is
off by default, and the difference is the point: speech makes a noise, and a
feature that can embarrass somebody on a bus must be opted into. A line of small
grey text cannot. Decks with no transcription draw nothing regardless of the
setting, so the default costs nothing on any deck published before this release.

The choice is stored per pack id in one preference string, `packId=mode`, the
same shape the per-deck looks beside it use, and it travels in the settings
backup. A damaged entry costs one deck its setting and never more than that.

## Producing the transcription

The pipeline in `tools/phonetics/g2p.py`, called by the catalogue builder.
Nothing runs on the device except the respelling.

**Epitran** does most of the work. It is MIT-licensed, it is a rule-based
grapheme-to-phoneme system rather than a model, and it covers six of the eight
languages directly: German, French, Spanish, Italian, Portuguese and Polish. For
those, transcription is deterministic and needs no dictionary.

**Portuguese wants to be Brazilian, and cannot fully be.** European Portuguese
reduces unstressed vowels heavily (`obrigado` ends closer to a swallowed sound
than an open one) while Brazilian keeps them open; to a beginner the two are
audibly different languages. Brazilian is what this project wants: it has far
more speakers, it is what most learners meet first, and its fuller vowels
survive the trip through an English respelling, whereas a reduced European vowel
respells to `uh` and tells the reader almost nothing.

Epitran, however, ships exactly one Portuguese map, `por-Latn`, and it is a
compromise between the varieties rather than a Brazilian one. There is no
`por-Latn-bz`. This is worth stating plainly because the earlier version of this
file claimed that code, and asking Epitran for a map that does not exist is not
quietly ignored — it raises `FileNotFoundError` when the map loads, which failed
the catalogue run outright.

So the pipeline names the map that ships and keeps the Brazilian preference in
two places that cost nothing: `PREFERRED_EPITRAN_CODES` in `g2p.py`, which tries
a Brazilian code first and silently falls back, so the day Epitran adds one it is
picked up without a change here; and the espeak-ng cross-check, which really does
distinguish `pt-br`, so a drift between the compromise map and Brazilian
pronunciation shows up as a rising disagreement rate rather than as a silent
shipped mistake. The honest summary: Portuguese transcription is currently a
compromise variety, closer to Brazilian in the consonants than in the unstressed
vowels.

**English needs a dictionary**, not rules. English spelling does not determine
pronunciation, so `CMUdict` (BSD-licensed) is used for lookup, with the phrase
falling back to no transcription rather than to a guess when a word is missing.

**Russian needs stress**, which the spelling does not mark. Vowel reduction in
Russian depends entirely on where the stress falls — `спасибо` is *spuh-SEE-buh*
and not *spah-SEE-boh* — so a transcription without stress is worse than none.
Stress comes from a dictionary; words that are not in it are skipped, and a
chunk is only transcribed when every word in it resolved.

**espeak-ng is used in CI as a cross-check only.** It covers more languages and
it is GPL-3, which is incompatible with shipping. It never touches a deck; it
runs in the workflow, its output is compared against Epitran's, and a large
disagreement rate fails the build. That keeps the licence clean while still
catching a language whose rules have silently broken.

### What was rejected, and why

**Wiktionary**, via `wiktextract`/`kaikki`, has hand-written IPA for far more
words than any of the above and would be better data. It is CC BY-SA. Pulling it
into a deck would make that deck a derivative work and force share-alike terms
onto content that is currently plain Tatoeba. The catalogue's promise is that a
deck's licence is Tatoeba's alone, stated on the row before download, and that
promise is worth more than better vowels. `CC-CEDICT` was rejected for the same
reason.

**On-device generation** was considered and dropped. Android's `Transliterator`
has the machinery, but the transcription would then be recomputed on every phone
for every card forever, would differ between Android versions, and could not be
checked before publication. Generating once, in a pipeline, in the open, where
the output can be diffed, is strictly better.

**Japanese katakana** was considered for `ja` and dropped: there is no codified
standard for transcribing arbitrary foreign phrases into katakana, and inventing
one was out of scope. `zh` and `ja` are meaning languages in this catalogue, not
learnable ones, so neither needs transcription today.

## What it costs in a deck file

Measured against the current catalogue, not estimated:

| | Bytes per card | Change |
| --- | --- | --- |
| Today | 557 | — |
| Phrase only | 583 | +4.8% |
| Phrase and sentence | 634 | +13.9% |

A 3000-card deck — the largest the builder will produce — goes from roughly
1.6 MiB to 1.8 MiB, against a 24 MiB download ceiling. The token list, which
carries a lemma and a part of speech for every word, is already 65% of a record;
transcription is a thin layer next to it.

Both the phrase and its sentence are transcribed. Transcribing only the phrase
would have saved about half of the increase, and the increase is not worth
saving: the sentence is what a recognition card shows.

## The database

Two nullable columns on `chunks`, `ipa` and `ipaContext`, added by migration
4 → 5. Nullable is what makes every existing install a no-op — nothing is
rewritten, nothing is recomputed, and a deck installed before this release simply
has nothing in those columns until it is reinstalled from a newer catalogue.

The full-text index is deliberately **not** touched. Its four triggers name
`text`, `contextSentence` and `translation` explicitly, and `ALTER TABLE ADD
COLUMN` fires no row triggers, so the index stays consistent and searching for
`ˈkuj` finds nothing — which is correct. Nobody searches in IPA.

## Building a catalogue that has it

The published catalogue was built before any of this existed, so its decks carry
no transcription and the setting will not appear for them. Nothing about that is
broken — it is the empty-column case above — but a new catalogue has to be built
and published for the feature to be visible on catalogue decks.

The workflow is `.github/workflows/catalog.yml`, run by hand from the Actions
tab. It now installs the phonetics dependencies and passes `--phonetics` to the
builder. Run it with the same inputs as before; it publishes to the `catalog`
release tag, replacing the assets in place.

The swap is transparent to the app. There is no version pin on the catalogue —
the app fetches whatever `index.json` is at that tag — so a phone that opens the
catalogue after the run sees the new decks with no update of its own. Decks
already installed keep working untouched; reinstalling one is what picks up the
transcription.

The index gains one field per deck, `phonetics`, so the catalogue row can say
whether a deck has it before anything is downloaded. Older apps ignore the
unknown key, which is the behaviour `CatalogModels.kt` was written for.

Budget three hours for a full run; the transcription step is the slowest part of
it after the corpus download.
