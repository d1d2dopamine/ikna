#!/usr/bin/env python3
"""
Build the ikna deck catalogue out of open corpora.

This is the whole "assembly" the app never does. It runs once per catalogue
release, on the build server (.github/workflows/catalog.yml), and what it leaves
behind is a folder of finished decks in the format the importer already reads,
plus one index.json listing them with their licence. The phone downloads two
static files and nothing else; docs/SOURCES.md is the contract this implements.

Where each field of a card comes from:

  Tatoeba    sentences.csv  id, language, sentence
             links.csv      sentence id -> translation id
             optional       sentences_CC0.csv, the public-domain subset

    The sentence a card is shown in, and the sentence in the other language that
    says what it means. Both come from one download, so one deck carries one
    licence and one line of credit.

  Wiktextract  <code>.jsonl  machine-readable Wiktionary

    Optional, and used only while this script runs: which written form belongs to
    which dictionary word, so "running" and "ran" are not taught as two cards. No
    Wiktionary text is copied into a deck, which is why a deck's licence is
    Tatoeba's alone.

The one trick worth naming: the phrase on a card is never written, it is cut out
of the sentence it appears in, and the offsets are stored beside it. The app's own
rule that the phrase must be inside its sentence therefore cannot be broken by
anything this emits: there is no step at which the two could drift apart.

The sieve is deliberately harsh, and every reason a candidate is dropped is
counted and printed. A catalogue smaller than the corpus is expected; a catalogue
that is silently empty is a bug.

Usage:

    python3 tools/catalog/build_catalog.py --tatoeba downloads/ --out out/catalog/
    python3 tools/catalog/build_catalog.py --tatoeba /tmp/sample --out /tmp/cat \\
        --learn en --meanings ru,pl --min-deck 5 --function-top 12

Nothing here reaches the network: whatever fetched the dumps has already done it.
"""

import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict
from datetime import date

# ---------------------------------------------------------------------------
# Languages
# ---------------------------------------------------------------------------
#
# Two lists, not one, and the difference is the honest part of this pipeline.
#
# A language can be LEARNED here only if a phrase can be cut out of its
# sentences, and the cut is made on word boundaries. Languages written without
# spaces - Chinese, Japanese - need a segmenter, which this script does not have
# and will not pretend to have. They are still available as the language the
# MEANINGS are in, because a translation is shown whole and never cut.
#
# Both lists are subsets of the app's own DECK_LANGS, so every deck emitted here
# lands on a language the app already knows.

# ISO 639-3, which is what Tatoeba writes, to the two-letter codes the app uses.
CODES = {
    "eng": "en",
    "rus": "ru",
    "pol": "pl",
    "spa": "es",
    "fra": "fr",
    "deu": "de",
    "ita": "it",
    "por": "pt",
    "cmn": "zh",
    "jpn": "ja",
}

#: Languages a deck can teach, because their words are separated by spaces.
LEARNABLE = ["en", "ru", "pl", "es", "fr", "de", "it", "pt"]

#: Languages the meanings can be in - everything, including the two that cannot
#: yet be taught.
MEANINGS = ["en", "ru", "pl", "es", "fr", "de", "it", "pt", "zh", "ja"]

NAMES = {
    "en": "English",
    "ru": "Russian",
    "pl": "Polish",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "pt": "Portuguese",
    "zh": "Chinese",
    "ja": "Japanese",
}

# ---------------------------------------------------------------------------
# Limits
# ---------------------------------------------------------------------------
#
# The first three are the importer's own maxima (SeedFormat, in
# app/src/main/java/dev/ikna/data/pack/SeedParser.kt). A deck that broke them
# would import with rows skipped and nobody would know why, so nothing that
# breaks them is written in the first place.

MAX_PHRASE = 80
MAX_SENTENCE = 300
MAX_TRANSLATION = 160

#: Below this a sentence carries no context worth reading.
MIN_SENTENCE = 12

#: A single letter is not a word anybody needs a card for.
MIN_PHRASE = 2

#: How many of the commonest forms in a language are treated as glue: articles,
#: prepositions, pronouns, the verb "to be". Measured rather than listed, so it
#: works the same in eight languages without eight hand-written word lists.
FUNCTION_TOP = 60

#: Where the levels are cut, by how common the taught word is.
LEVEL_BEGINNER = 1500
LEVEL_MIDDLE = 5000

LEVELS = ["beginner", "middle", "advanced"]

#: Licences, spelled the way the catalogue screen shows them.
LICENCE_BY = "CC BY 2.0 FR"
LICENCE_CC0 = "CC0 1.0"

ATTRIBUTION_BY = "Sentences by Tatoeba contributors, tatoeba.org"
ATTRIBUTION_CC0 = "Sentences from the Tatoeba public-domain set, tatoeba.org"

SOURCES_BY = ["Tatoeba, CC BY 2.0 FR", "Wiktionary via Wiktextract, word forms only"]
SOURCES_CC0 = ["Tatoeba, public-domain set", "Wiktionary via Wiktextract, word forms only"]

#: Appended to a translation so the card itself says where it came from. It
#: travels with the deck, including through an export. Same mark the seed parser
#: uses, so a catalogue card and a hand-made one look alike.
SOURCE_MARK = "\n\u2014 "

WORD = re.compile(r"[^\W\d_]+(?:['\u2019\-][^\W\d_]+)*", re.UNICODE)


def words(text):
    """The words of a sentence, in order, as they are written."""
    return [match.group(0) for match in WORD.finditer(text)]


# ---------------------------------------------------------------------------
# Reading the dumps
# ---------------------------------------------------------------------------


def read_sentences(path, wanted):
    """
    Tatoeba's sentences, as {code: {id: text}}.

    Tab-separated despite the file name, three columns, and big enough that it is
    read a line at a time: everything in a language this catalogue does not serve
    is dropped before it is stored.
    """
    kept = defaultdict(dict)
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            code = CODES.get(parts[1])
            if code is None or code not in wanted:
                continue
            text = parts[2].strip()
            if text:
                kept[code][parts[0]] = text
    return kept


def read_ids(path):
    """The ids in a sentences-shaped file, as a set. Used for the CC0 subset."""
    ids = set()
    if not path or not os.path.exists(path):
        return ids
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            head = line.split("\t", 1)[0].strip()
            if head:
                ids.add(head)
    return ids


def read_links(path, known):
    """
    Which sentence is a translation of which, as {id: [id, ...]}.

    Only links with both ends among the sentences we kept are stored, which is
    what keeps this from being tens of millions of pairs.
    """
    links = defaultdict(list)
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            left, right = parts[0], parts[1]
            if left in known and right in known:
                links[left].append(right)
    return links


def read_forms(directory, langs):
    """
    Written form -> dictionary word, per language, from Wiktextract.

    Optional. One file per language, named <code>.jsonl, one JSON object per line
    in the shape kaikki.org publishes. Only "word" and "forms" are read, and a
    file that is missing or malformed costs nothing but worse lemmas.
    """
    forms = {code: {} for code in langs}
    if not directory:
        return forms
    for code in langs:
        path = os.path.join(directory, code + ".jsonl")
        if not os.path.exists(path):
            continue
        table = forms[code]
        with open(path, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                except ValueError:
                    continue
                head = (entry.get("word") or "").strip().lower()
                if not head:
                    continue
                table.setdefault(head, head)
                for form in entry.get("forms") or []:
                    written = (form.get("form") or "").strip().lower()
                    # Wiktextract puts table headers and notes in "forms" too.
                    if written and " " not in written and len(written) <= 40:
                        table.setdefault(written, head)
    return forms


# ---------------------------------------------------------------------------
# Building
# ---------------------------------------------------------------------------


def frequency(texts):
    """
    The rank of every written form in a language, 1 being the commonest.

    Counted over the corpus actually being used rather than over an outside
    frequency list, so "how common is this word" means "how common is it in the
    sentences these decks are cut from" - which is the number the app orders new
    cards by.
    """
    counts = Counter()
    for text in texts:
        counts.update(word.lower() for word in words(text))
    ranks = {}
    for position, (form, _) in enumerate(counts.most_common(), start=1):
        ranks[form] = position
    return ranks


def token_list(sentence, ranks, forms):
    """
    The sentence as the app stores it: one token per word, in order.

    Which token is the taught one is not recorded here - the app works that out
    from the character offsets - so this stays a plain description of the
    sentence. `pos` is the coarse mark the shipped decks already use: WORD for
    something worth weighting, FUNC for the glue.
    """
    out = []
    for surface in words(sentence):
        low = surface.lower()
        rank = ranks.get(low, 10 ** 9)
        content = rank > FUNCTION_TOP and len(surface) > 1
        out.append(
            {
                "surface": surface,
                "lemma": forms.get(low, low),
                "pos": "WORD" if content else "FUNC",
                "isContent": content,
            }
        )
    return out


def pick_phrase(sentence, ranks, forms, used):
    """
    Which word this sentence is going to teach, or None.

    The rarest word in it that is not glue, so a card is one new word inside a
    sentence of words somebody is likelier to have already met. Three conditions,
    and all three are about being able to point at it later:

      - it appears exactly once, so the offsets are not ambiguous;
      - its dictionary word has not been taught in this deck already;
      - it is a word rather than glue or a single letter.
    """
    found = words(sentence)
    best = None
    for surface in found:
        low = surface.lower()
        rank = ranks.get(low)
        if rank is None or rank <= FUNCTION_TOP:
            continue
        if len(surface) < MIN_PHRASE or len(surface) > MAX_PHRASE:
            continue
        lemma = forms.get(low, low)
        if lemma in used:
            continue
        # Exactly once, compared without case, so "Book" opening a sentence and
        # "book" inside it count as the same word.
        if sum(1 for other in found if other.lower() == low) != 1:
            continue
        if best is None or rank > best[1]:
            best = (surface, rank, lemma)
    return best


def offsets(sentence, surface):
    """Where the phrase sits in its sentence, or None if it somehow does not."""
    start = sentence.find(surface)
    if start < 0:
        return None
    return start, start + len(surface)


def level_of(rank):
    if rank <= LEVEL_BEGINNER:
        return "beginner"
    if rank <= LEVEL_MIDDLE:
        return "middle"
    return "advanced"


def build_pair(lang, meaning, sentences, links, ranks, forms, cc0, stats, public_domain):
    """
    Every card this pipeline can make for one direction of one pair.

    Returned unsorted and unnumbered: which deck a card lands in is decided
    afterwards, from how common the word it teaches is.
    """
    target = sentences.get(lang) or {}
    other = sentences.get(meaning) or {}
    if not target or not other:
        return []

    used = set()
    cards = []

    # Easiest sentences first, so the commonest words get the plainest examples
    # and a deck's early cards are not sentences with three rare words in them.
    def difficulty(item):
        found = [ranks.get(word.lower(), 10 ** 9) for word in words(item[1])]
        return max(found) if found else 10 ** 9

    for sid, sentence in sorted(target.items(), key=difficulty):
        if public_domain and sid not in cc0:
            continue
        if len(sentence) < MIN_SENTENCE or len(sentence) > MAX_SENTENCE:
            stats["sentence length"] += 1
            continue

        translations = [
            other[tid]
            for tid in links.get(sid, ())
            if tid in other
            and 0 < len(other[tid]) <= MAX_TRANSLATION
            and (not public_domain or tid in cc0)
        ]
        if not translations:
            stats["no translation"] += 1
            continue
        # The shortest one that fits. A card is read in a second, and the longest
        # of five translations is the one somebody skips.
        translation = min(translations, key=len)

        chosen = pick_phrase(sentence, ranks, forms, used)
        if chosen is None:
            stats["nothing to teach"] += 1
            continue
        surface, rank, lemma = chosen

        span = offsets(sentence, surface)
        if span is None:
            # Cannot happen: the surface was taken out of this sentence. Counted
            # anyway, because the day it happens is the day this assumption is
            # worth knowing about.
            stats["phrase not in sentence"] += 1
            continue

        used.add(lemma)
        cards.append(
            {
                "rank": rank,
                "text": surface,
                "context": sentence,
                "translation": translation + SOURCE_MARK + "Tatoeba #" + str(sid),
                "targetStart": span[0],
                "targetEnd": span[1],
                "tokens": token_list(sentence, ranks, forms),
            }
        )

    return cards


def write_deck(out_dir, deck_id, cards):
    """One deck file, one card per line, in the format the importer already reads."""
    path = os.path.join(out_dir, deck_id + ".jsonl")
    with open(path, "w", encoding="utf-8") as handle:
        for position, card in enumerate(cards, start=1):
            handle.write(
                json.dumps(
                    {
                        "id": "%s-%04d" % (deck_id, position),
                        "text": card["text"],
                        "context": card["context"],
                        "translation": card["translation"],
                        "targetStart": card["targetStart"],
                        "targetEnd": card["targetEnd"],
                        "freqRank": card["rank"],
                        "tokens": card["tokens"],
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
    return os.path.getsize(path)


def tiers_markdown(pairs):
    """The table the README shows, generated rather than maintained by hand."""
    lines = [
        "| learning | meanings in | cards | decks | how well served |",
        "| --- | --- | --- | --- | --- |",
    ]
    for pair in sorted(pairs, key=lambda p: (-p["chunkCount"], p["lang"], p["meaningLang"])):
        lines.append(
            "| %s | %s | %d | %d | %s |"
            % (
                NAMES.get(pair["lang"], pair["lang"]),
                NAMES.get(pair["meaningLang"], pair["meaningLang"]),
                pair["chunkCount"],
                pair["deckCount"],
                pair["tier"],
            )
        )
    return "\n".join(lines) + "\n"


def main(argv=None):
    # Rebound from --function-top a few lines down.
    global FUNCTION_TOP

    parser = argparse.ArgumentParser(description="Build the ikna deck catalogue.")
    parser.add_argument("--tatoeba", required=True, help="folder holding the Tatoeba exports")
    parser.add_argument("--out", required=True, help="where the decks and index.json are written")
    parser.add_argument("--wiktextract", default=None, help="folder of <code>.jsonl extracts")
    parser.add_argument("--max-deck", type=int, default=3000, help="cards per deck at most")
    parser.add_argument("--min-deck", type=int, default=40, help="below this a deck is not published")
    parser.add_argument(
        "--full-threshold", type=int, default=3000, help="cards for a pair to count as full"
    )
    parser.add_argument(
        "--function-top",
        type=int,
        default=FUNCTION_TOP,
        help="how many of the commonest forms count as glue rather than as words",
    )
    parser.add_argument("--learn", default=",".join(LEARNABLE), help="languages to teach")
    parser.add_argument("--meanings", default=",".join(MEANINGS), help="languages meanings may be in")
    parser.add_argument("--public-domain", action="store_true", help="also build CC0-only decks")
    args = parser.parse_args(argv)

    # How much of a language is glue depends on how much of the language there
    # is. Sixty is right for a corpus of a million sentences and nonsense for the
    # hundred-line sample the smoke test runs on, which is the only reason this
    # is settable at all.
    FUNCTION_TOP = args.function_top

    learn = [code.strip() for code in args.learn.split(",") if code.strip()]
    meanings = [code.strip() for code in args.meanings.split(",") if code.strip()]
    wanted = set(learn) | set(meanings)

    sentences_path = os.path.join(args.tatoeba, "sentences.csv")
    links_path = os.path.join(args.tatoeba, "links.csv")
    cc0_path = os.path.join(args.tatoeba, "sentences_CC0.csv")
    for path in (sentences_path, links_path):
        if not os.path.exists(path):
            parser.error("missing " + path)

    os.makedirs(args.out, exist_ok=True)

    print("reading sentences", flush=True)
    sentences = read_sentences(sentences_path, wanted)
    for code in sorted(sentences):
        print("  %s %d" % (code, len(sentences[code])))

    known = set()
    for table in sentences.values():
        known.update(table.keys())
    print("reading links", flush=True)
    links = read_links(links_path, known)
    cc0 = read_ids(cc0_path) if args.public_domain else set()

    print("reading word forms", flush=True)
    forms = read_forms(args.wiktextract, learn)

    ranks = {}
    for code in learn:
        if code in sentences:
            ranks[code] = frequency(sentences[code].values())

    decks = []
    pairs = []
    stats = Counter()

    # One family under Tatoeba's own licence, and optionally a second built only
    # from the public-domain subset, which needs no credit at all. Never mixed:
    # a deck with two licences in it could not be described on one screen.
    families = [(False, LICENCE_BY, ATTRIBUTION_BY, SOURCES_BY, "")]
    if args.public_domain:
        families.append((True, LICENCE_CC0, ATTRIBUTION_CC0, SOURCES_CC0, "-pd"))

    print("building", flush=True)
    for lang in learn:
        if lang not in sentences or lang not in ranks:
            continue
        for meaning in meanings:
            if meaning == lang or meaning not in sentences:
                continue
            pair_cards = 0
            pair_decks = 0
            for public_domain, licence, attribution, sources, suffix in families:
                cards = build_pair(
                    lang,
                    meaning,
                    sentences,
                    links,
                    ranks[lang],
                    forms.get(lang, {}),
                    cc0,
                    stats,
                    public_domain,
                )
                if not cards:
                    continue
                by_level = defaultdict(list)
                for card in cards:
                    by_level[level_of(card["rank"])].append(card)
                for level in LEVELS:
                    group = sorted(by_level.get(level, []), key=lambda card: card["rank"])
                    if len(group) < args.min_deck:
                        continue
                    group = group[: args.max_deck]
                    deck_id = "%s-%s-%s%s" % (lang, meaning, level, suffix)
                    size = write_deck(args.out, deck_id, group)
                    decks.append(
                        {
                            "id": deck_id,
                            "title": "%s from %s \u00b7 %s"
                            % (NAMES.get(lang, lang), NAMES.get(meaning, meaning), level),
                            "lang": lang,
                            "meaningLang": meaning,
                            "chunkCount": len(group),
                            "file": deck_id + ".jsonl",
                            "sizeBytes": size,
                            "subject": "",
                            "level": level,
                            "licence": licence,
                            "attribution": attribution,
                            "sources": sources,
                            "version": 1,
                        }
                    )
                    pair_cards += len(group)
                    pair_decks += 1
            if pair_decks:
                pairs.append(
                    {
                        "lang": lang,
                        "meaningLang": meaning,
                        "tier": "full" if pair_cards >= args.full_threshold else "thin",
                        "deckCount": pair_decks,
                        "chunkCount": pair_cards,
                    }
                )
                print("  %s -> %s: %d cards in %d decks" % (lang, meaning, pair_cards, pair_decks))

    index = {
        "version": 1,
        "builtAt": date.today().isoformat(),
        "decks": decks,
        "pairs": pairs,
    }
    with open(os.path.join(args.out, "index.json"), "w", encoding="utf-8") as handle:
        json.dump(index, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    with open(os.path.join(args.out, "TIERS.md"), "w", encoding="utf-8") as handle:
        handle.write(tiers_markdown(pairs))

    print("")
    print("decks: %d" % len(decks))
    print("pairs: %d" % len(pairs))
    print("cards: %d" % sum(deck["chunkCount"] for deck in decks))
    if stats:
        print("dropped:")
        for reason, count in stats.most_common():
            print("  %s: %d" % (reason, count))
    if not decks:
        print("nothing was built", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
