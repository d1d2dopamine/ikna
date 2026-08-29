# Phonetics

Turns a written phrase into IPA, once, here, so the app never has to.

Why the work is split that way, why the app shows an English respelling instead
of the IPA it stores, and what that respelling gets wrong: **`docs/PHONETICS.md`**.
This file is only about running the thing.

## Install

```sh
python3 -m pip install -r tools/phonetics/requirements.txt
```

Optional, and only for the cross-check below:

```sh
sudo apt-get install espeak-ng
```

## Use

```sh
# one phrase
python3 tools/phonetics/g2p.py --lang pl --text 'dziękuję'
# dʑɛŋkujɛ

# is this language still working at all
python3 tools/phonetics/g2p.py --lang pt --selftest

# what espeak-ng thinks, for comparison only
python3 tools/phonetics/g2p.py --lang de --text 'danke' --espeak
```

The catalogue builder imports `transcriber_for` rather than shelling out:

```sh
python3 tools/catalog/build_catalog.py --tatoeba corpus --out catalog --phonetics
```

## What handles what

| Language | Engine | Licence |
| --- | --- | --- |
| de, fr, es, it, pl | Epitran, rules | MIT |
| pt | Epitran, `por-Latn-bz` — **Brazilian** | MIT |
| en | CMUdict, lookup | BSD |
| ru | stress dictionary, then rules in this file | — |
| — | espeak-ng, cross-check only, never shipped | GPL-3 |

English and Russian are not rule-based for opposite reasons. English spelling
does not predict pronunciation, so it has to be looked up. Russian spelling does
predict it, but only once you know where the stress falls, and Russian does not
write the stress — and stress is what decides vowel reduction, so an unstressed
transcription is confidently wrong rather than approximately right.

## Russian stress

A plain text file, one word per line, stress marked with an acute accent or a
following apostrophe:

```
спаси́бо
пожа'луйста
да
```

A word with only one vowel needs no mark; there is nowhere else the stress could
be. Pass it with `--russian-stress`. Without it, only single-vowel Russian words
get transcribed and everything else is skipped — which is the intended failure,
not a bug.

## The rule that matters

**A chunk is transcribed only when every word in it resolved.** One unknown word
means no line for that chunk at all.

This throws away cards that were nearly fine, and it is still right: a
pronunciation line that is correct for four words out of five gives the reader
no way to tell which one was the fifth, and they will trust all five. A missing
line is honest. A partly wrong one is not.

The same reasoning is why an unknown ARPABET symbol makes `CmudictEngine` refuse
the word rather than skip the sound, and why an engine that throws on some odd
input is treated as not knowing that word instead of ending the run.

## Licences

Nothing here is copied into a deck. A deck carries Tatoeba's licence and only
Tatoeba's, which is what the catalogue row promises before anything is
downloaded.

That promise is the reason the best available source is not used. Wiktionary,
via `wiktextract`/`kaikki.org`, has hand-written IPA for far more words than any
of the engines above and would produce better decks. It is CC BY-SA. Using it
would make every deck a derivative work and force share-alike terms onto content
that does not carry them today. Better vowels are not worth changing what a
reader agrees to when they tap download.

espeak-ng is in the same position for a different reason: GPL-3. It runs in CI,
its output is compared against Epitran's, and it never touches a published deck.

## When a language breaks

`--selftest` checks that a transcription happens and that it contains the sound
it obviously must — `ż` in a Polish word, a nasal in a French one. It does not
check that the transcription is *right*; that needs somebody who speaks the
language.

So the practical signal is disagreement. Run a sample through both engines and
look at the rate:

```sh
python3 tools/phonetics/g2p.py --lang fr --text 'bonjour merci' --espeak
```

Two rule sets that used to mostly agree and now mostly do not is a language
worth looking at before a catalogue is published with it.
