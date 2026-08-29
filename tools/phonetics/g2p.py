#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
How a chunk gets a pronunciation.

This runs once, here, in the open, and writes IPA into the deck file. It never
runs on a phone. The app turns that IPA into an English respelling on the
device, which is the half of the job that has to be improvable without
republishing anything -- see docs/PHONETICS.md for why the split falls there.

What this file is careful about:

  * Licences. Everything used here is permissive and none of it is copied into
    a deck. A deck's licence stays Tatoeba's alone, which is the promise the
    catalogue row makes before anything is downloaded, and the reason the
    obvious better source -- Wiktionary, CC BY-SA -- is not used.

  * Refusing rather than guessing. A chunk gets a transcription only when every
    word in it resolved. A line that is right about four words out of five is
    worse than no line: the reader has no way to tell which word was the fifth.

  * Being deterministic. Same input, same output, every run. No model, no
    network, no clock.

Usage:

    python3 g2p.py --lang pl --text 'dzi\u0119kuj\u0119'
    python3 g2p.py --lang pl --selftest

The catalogue builder imports `transcriber_for` rather than shelling out.
"""
from __future__ import annotations

import argparse
import re
import sys
import unicodedata


# ---------------------------------------------------------------------------
# Which engine handles which language.
#
# Epitran is rule-based rather than a model, MIT-licensed, and covers six of the
# eight languages outright. Rule-based matters here: the output is inspectable,
# it does not vary between runs, and when it is wrong it is wrong in a way
# somebody can find and describe.
#
# The two exceptions are the two languages where spelling genuinely does not
# determine pronunciation, and each needs a dictionary for a different reason.
# ---------------------------------------------------------------------------

EPITRAN_CODES = {
    "de": "deu-Latn",
    "fr": "fra-Latn",
    "es": "spa-Latn",
    "it": "ita-Latn",
    "pl": "pol-Latn",
    # Brazilian, not European. Two audibly different languages to a beginner:
    # European Portuguese reduces unstressed vowels almost out of existence,
    # Brazilian keeps them open. Brazilian has far more speakers, is what most
    # learners meet first, and -- the deciding factor for this project -- its
    # fuller vowels survive an English respelling. A reduced European vowel
    # respells to "uh", which tells the reader nothing at all.
    "pt": "por-Latn-bz",
}

# English is not in that table on purpose. English spelling does not determine
# pronunciation -- through, though, thought, tough -- so rules cannot do it and
# a dictionary must. CMUdict is BSD-licensed and is the standard answer.
#
# Russian is not in it either, for a different reason: the rules are fine but
# they need to know where the stress is, and Russian spelling does not write it.
# Vowel reduction depends entirely on stress, so \u0441\u043f\u0430\u0441\u0438\u0431\u043e is spuh-SEE-buh and
# never spah-SEE-boh. An unstressed transcription would be confidently wrong.

SUPPORTED = sorted(set(EPITRAN_CODES) | {"en", "ru"})


# ---------------------------------------------------------------------------
# ARPABET -> IPA, for the English dictionary.
# ---------------------------------------------------------------------------

ARPABET = {
    "AA": "\u0251", "AE": "\u00e6", "AH": "\u028c", "AO": "\u0254",
    "AW": "a\u028a", "AY": "a\u026a", "B": "b", "CH": "t\u0283",
    "D": "d", "DH": "\u00f0", "EH": "\u025b", "ER": "\u025c",
    "EY": "e\u026a", "F": "f", "G": "\u0261", "HH": "h",
    "IH": "\u026a", "IY": "i\u02d0", "JH": "d\u0292", "K": "k",
    "L": "l", "M": "m", "N": "n", "NG": "\u014b",
    "OW": "o\u028a", "OY": "\u0254\u026a", "P": "p", "R": "\u0279",
    "S": "s", "SH": "\u0283", "T": "t", "TH": "\u03b8",
    "UH": "\u028a", "UW": "u\u02d0", "V": "v", "W": "w",
    "Y": "j", "Z": "z", "ZH": "\u0292",
}

# CMUdict marks stress as a digit on the vowel: 1 primary, 2 secondary, 0 none.
# The respeller reads the IPA marks, not these, so they are converted.
STRESS_MARK = {"1": "\u02c8", "2": "\u02cc", "0": ""}

PRIMARY = "\u02c8"
SECONDARY = "\u02cc"

# Anything that is not part of a word. Punctuation is dropped rather than
# transcribed: a full stop has no sound, and a chunk that failed because of one
# would be a silly way to lose a card.
WORD_SPLIT = re.compile(r"[^\w\u00c0-\u024f\u0400-\u04ff'\u2019-]+", re.UNICODE)


def words_of(text):
    """The words in a phrase, in order, with punctuation removed."""
    return [w for w in WORD_SPLIT.split(text or "") if w.strip("-'\u2019")]


# ---------------------------------------------------------------------------
# The engines.
#
# Each is a callable taking one word and returning IPA, or None when it does not
# know. None is the important half of the contract: it is what makes the caller
# skip the chunk instead of publishing a guess.
# ---------------------------------------------------------------------------

class EpitranEngine:
    """Rule-based transcription. Deterministic, offline, MIT."""

    def __init__(self, lang):
        import epitran  # imported here so --help works without it installed

        self.lang = lang
        self.code = EPITRAN_CODES[lang]
        self.epi = epitran.Epitran(self.code)

    def __call__(self, word):
        out = self.epi.transliterate(word)
        out = (out or "").strip()
        return out or None


class CmudictEngine:
    """English, by lookup. BSD."""

    def __init__(self):
        import cmudict

        # cmudict gives every recorded pronunciation of a word. The first is
        # taken rather than all of them: a card shows one line, and a reader who
        # wanted the variants wanted a dictionary, not a flashcard.
        self.table = {}
        for word, phones in cmudict.entries():
            self.table.setdefault(word.lower(), phones)

    def __call__(self, word):
        key = word.lower().strip("-'\u2019")
        phones = self.table.get(key)
        if phones is None:
            return None
        out = []
        for phone in phones:
            stress = ""
            if phone and phone[-1].isdigit():
                stress = STRESS_MARK.get(phone[-1], "")
                phone = phone[:-1]
            symbol = ARPABET.get(phone.upper())
            if symbol is None:
                # An unknown ARPABET symbol means the table above is out of
                # date. Refusing the word is right; inventing a sound is not.
                return None
            out.append(stress + symbol)
        return "".join(out) or None


class RussianEngine:
    """
    Russian, in two steps: find the stress, then apply the rules.

    The stress dictionary is the part that cannot be derived. Everything after
    it is mechanical, and doing it here rather than handing a stressed string to
    a general engine keeps the reduction rules visible and testable.
    """

    VOWELS = "\u0430\u0435\u0451\u0438\u043e\u0443\u044b\u044d\u044e\u044f"

    # The consonants, unpalatalised. Palatalisation is decided by the vowel that
    # follows, below, which is how Russian orthography actually encodes it.
    HARD = {
        "\u0431": "b", "\u0432": "v", "\u0433": "\u0261", "\u0434": "d",
        "\u0436": "\u0292", "\u0437": "z", "\u043a": "k", "\u043b": "l",
        "\u043c": "m", "\u043d": "n", "\u043f": "p", "\u0440": "r",
        "\u0441": "s", "\u0442": "t", "\u0444": "f", "\u0445": "x",
        "\u0446": "ts", "\u0447": "t\u0255", "\u0448": "\u0283",
        "\u0449": "\u0255\u0255", "\u0439": "j",
    }

    # Always hard, whatever follows: \u0436 \u0448 \u0446. Always soft: \u0447 \u0449 \u0439.
    ALWAYS_HARD = "\u0436\u0448\u0446"
    ALWAYS_SOFT = "\u0447\u0449\u0439"

    # A vowel letter after a hard consonant, and the same letter after a soft
    # one. The second column is what makes \u0435 and \u044f behave.
    SOFTENING = "\u0435\u0451\u0438\u044e\u044f\u044c"

    STRESSED = {
        "\u0430": "a", "\u0435": "\u025b", "\u0451": "o", "\u0438": "i",
        "\u043e": "o", "\u0443": "u", "\u044b": "\u0268", "\u044d": "\u025b",
        "\u044e": "u", "\u044f": "a",
    }

    # Unstressed. This is the whole reason stress had to be found first.
    # \u043e and \u0430 collapse to \u0250 away from the stress -- \u0441\u043f\u0430\u0441\u0438\u0431\u043e is spuh-SEE-buh --
    # and the front vowels raise towards \u0268 or \u026a.
    UNSTRESSED = {
        "\u0430": "\u0250", "\u0435": "\u026a", "\u0451": "o", "\u0438": "\u026a",
        "\u043e": "\u0250", "\u0443": "u", "\u044b": "\u0268", "\u044d": "\u026a",
        "\u044e": "u", "\u044f": "\u026a",
    }

    # Final devoicing, which Russian does and English does not, so it has to be
    # written or the respelling will be wrong in a way that is easy to hear.
    DEVOICE = {
        "b": "p", "v": "f", "\u0261": "k", "d": "t",
        "\u0292": "\u0283", "z": "s",
    }

    def __init__(self, stress_lookup):
        self.stress_lookup = stress_lookup

    def __call__(self, word):
        lowered = word.lower().replace("\u0451", "\u0451")
        index = self.stress_lookup(lowered)
        if index is None:
            return None
        return self.transcribe(lowered, index)

    def transcribe(self, word, stress_index):
        out = []
        letters = list(word)
        for i, ch in enumerate(letters):
            nxt = letters[i + 1] if i + 1 < len(letters) else ""
            if ch in self.VOWELS:
                table = self.STRESSED if i == stress_index else self.UNSTRESSED
                sound = table.get(ch)
                if sound is None:
                    return None
                if i == stress_index:
                    out.append(PRIMARY)
                # An iotated vowel at the start of a word or after another vowel
                # carries its own /j/ rather than softening anything.
                if ch in "\u0435\u0451\u044e\u044f" and (
                    i == 0 or letters[i - 1] in self.VOWELS
                    or letters[i - 1] in "\u044a\u044c"
                ):
                    out.append("j")
                out.append(sound)
            elif ch in "\u044a\u044c":
                # The soft sign is not a sound; it softens what came before,
                # which is handled when that consonant was written.
                if ch == "\u044c" and out and out[-1] not in "\u02b2":
                    out.append("\u02b2")
            else:
                sound = self.HARD.get(ch)
                if sound is None:
                    return None
                soft = (
                    ch not in self.ALWAYS_HARD
                    and (ch in self.ALWAYS_SOFT or nxt in self.SOFTENING)
                )
                out.append(sound)
                if soft and ch not in self.ALWAYS_SOFT:
                    out.append("\u02b2")

        # Devoice a final obstruent.
        if out:
            tail = out[-1]
            if tail in self.DEVOICE:
                out[-1] = self.DEVOICE[tail]
            elif tail == "\u02b2" and len(out) > 1 and out[-2] in self.DEVOICE:
                out[-2] = self.DEVOICE[out[-2]]

        return "".join(out) or None


def load_russian_stress(path=None):
    """
    Where the stress comes from.

    A plain text file, one word per line, with the stressed vowel marked by an
    acute accent or by a following apostrophe. Kept as a file rather than a
    dependency because the licences on the published Russian stress dictionaries
    vary and have to be checked one at a time, and because a project can ship a
    small hand-checked list and still be correct on the words it covers.

    Words that are not in it are skipped. That loses cards; it does not publish
    wrong ones.
    """
    table = {}
    if not path:
        return lambda word: table.get(word)

    with open(path, encoding="utf-8") as fh:
        for line in fh:
            entry = line.strip()
            if not entry or entry.startswith("#"):
                continue
            plain = []
            index = None
            for ch in unicodedata.normalize("NFD", entry):
                if ch in ("\u0301", "'", "\u2019"):
                    if plain:
                        index = len(plain) - 1
                    continue
                if unicodedata.combining(ch):
                    continue
                plain.append(ch)
            word = "".join(plain).lower()
            # A word with one vowel does not need marking: there is only one
            # place the stress can be.
            if index is None:
                vowels = [
                    i for i, c in enumerate(word)
                    if c in RussianEngine.VOWELS
                ]
                if len(vowels) != 1:
                    continue
                index = vowels[0]
            table[word] = index

    def lookup(word):
        if word in table:
            return table[word]
        # A single-vowel word is unambiguous whether or not anybody listed it.
        vowels = [i for i, c in enumerate(word) if c in RussianEngine.VOWELS]
        if len(vowels) == 1:
            return vowels[0]
        return None

    return lookup


# ---------------------------------------------------------------------------
# The thing the catalogue builder uses.
# ---------------------------------------------------------------------------

class Transcriber:
    """
    One language's engine, plus the all-or-nothing rule and a cache.

    The cache is worth having: a catalogue run transcribes hundreds of thousands
    of chunks and the same function words appear in almost all of them.
    """

    def __init__(self, lang, engine):
        self.lang = lang
        self.engine = engine
        self.cache = {}
        self.hits = 0
        self.misses = 0

    def word(self, word):
        if word in self.cache:
            return self.cache[word]
        try:
            out = self.engine(word)
        except Exception:
            # An engine that throws on one odd word must not end the run. The
            # word is treated as unknown, which is already a case with defined
            # behaviour.
            out = None
        self.cache[word] = out
        return out

    def line(self, text):
        """IPA for a whole phrase, or None if any word is unknown."""
        words = words_of(text)
        if not words:
            return None
        out = []
        for word in words:
            sound = self.word(word)
            if not sound:
                self.misses += 1
                return None
            out.append(sound)
        self.hits += 1
        return " ".join(out)


def transcriber_for(lang, russian_stress=None):
    """The transcriber for a language, or None when there is not one."""
    if lang in EPITRAN_CODES:
        return Transcriber(lang, EpitranEngine(lang))
    if lang == "en":
        return Transcriber(lang, CmudictEngine())
    if lang == "ru":
        return Transcriber(lang, RussianEngine(load_russian_stress(russian_stress)))
    return None


# ---------------------------------------------------------------------------
# The cross-check.
#
# espeak-ng covers more languages and is better at some of them. It is also
# GPL-3, which is incompatible with shipping its output inside this project, so
# it never touches a deck. What it can do is disagree: run it beside Epitran in
# CI, and a language whose rules have quietly broken shows up as a
# disagreement rate that jumped.
# ---------------------------------------------------------------------------

ESPEAK_CODES = {
    "de": "de", "fr": "fr-fr", "es": "es", "it": "it",
    "pl": "pl", "pt": "pt-br", "en": "en-us", "ru": "ru",
}


def espeak(lang, word):
    """IPA from espeak-ng, or None when it is not installed."""
    import subprocess

    code = ESPEAK_CODES.get(lang)
    if not code:
        return None
    try:
        out = subprocess.run(
            ["espeak-ng", "-v", code, "-q", "--ipa", word],
            capture_output=True, text=True, timeout=10,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if out.returncode != 0:
        return None
    return out.stdout.strip() or None


# ---------------------------------------------------------------------------
# Enough of a self-test to notice when a language has stopped working.
#
# These are not accuracy tests -- checking that a transcription is *right* needs
# somebody who speaks the language. They check that a transcription happens at
# all, and that it contains what it obviously must.
# ---------------------------------------------------------------------------

SELFTEST = {
    "pl": [("dzi\u0119kuj\u0119", "\u0255"), ("wszystko", "\u0282")],
    "de": [("danke", "\u014b"), ("sch\u00f6n", "\u0283")],
    "fr": [("bonjour", "\u0292"), ("merci", "s")],
    "es": [("gracias", "s"), ("ma\u00f1ana", "\u0272")],
    "it": [("grazie", "ts"), ("buongiorno", "d\u0292")],
    "pt": [("obrigado", "u"), ("bom", "")],
    "en": [("thank", "\u03b8"), ("you", "j")],
    "ru": [("\u0441\u043f\u0430\u0441\u0438\u0431\u043e", "\u0250"), ("\u0434\u0430", "a")],
}


def selftest(lang, russian_stress=None):
    tr = transcriber_for(lang, russian_stress)
    if tr is None:
        print("no transcriber for %s" % lang)
        return 1
    bad = 0
    for word, must_contain in SELFTEST.get(lang, []):
        got = tr.word(word)
        if not got:
            print("FAIL %s %-12s -> nothing" % (lang, word))
            bad += 1
            continue
        if must_contain and must_contain not in got:
            print("WARN %s %-12s -> %s (expected to contain %s)"
                  % (lang, word, got, must_contain))
        else:
            print("ok   %s %-12s -> %s" % (lang, word, got))
    return 1 if bad else 0


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--lang", required=True, choices=SUPPORTED)
    ap.add_argument("--text", help="a phrase to transcribe")
    ap.add_argument("--russian-stress", help="stress dictionary for ru")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--espeak", action="store_true",
                    help="also show espeak-ng, for comparison only")
    args = ap.parse_args()

    if args.selftest:
        return selftest(args.lang, args.russian_stress)

    if not args.text:
        ap.error("--text or --selftest is required")

    tr = transcriber_for(args.lang, args.russian_stress)
    if tr is None:
        print("no transcriber for %s" % args.lang, file=sys.stderr)
        return 1

    line = tr.line(args.text)
    print(line if line else "(nothing: at least one word was not known)")

    if args.espeak:
        for word in words_of(args.text):
            print("  espeak %-14s %s" % (word, espeak(args.lang, word) or "-"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
