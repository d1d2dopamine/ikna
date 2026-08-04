#!/usr/bin/env python3
# Offline chunk pack generator for Ikna.
#
# Everything expensive happens here so the phone never does morphology:
# tokenisation, lemmatisation, part-of-speech guessing, content-word
# classification, target span resolution, frequency ranking and validation.
#
# Output: one JSONL file plus a manifest, both committed as build artefacts.

import argparse
import json
import os
import re
import sys

FUNCTION_WORDS = {
    "a", "an", "the", "and", "or", "but", "if", "then", "than", "as", "of", "to",
    "in", "on", "at", "by", "for", "with", "from", "into", "about", "over",
    "under", "out", "up", "down", "off", "is", "am", "are", "was", "were", "be",
    "been", "being", "do", "does", "did", "have", "has", "had", "will", "would",
    "can", "could", "shall", "should", "may", "might", "must", "i", "you", "he",
    "she", "it", "we", "they", "me", "him", "her", "us", "them", "my", "your",
    "his", "its", "our", "their", "this", "that", "these", "those", "there",
    "here", "not", "no", "so", "too", "very", "just", "also", "please",
}

IRREGULAR = {
    "took": "take", "taken": "take", "takes": "take", "taking": "take",
    "made": "make", "makes": "make", "making": "make",
    "got": "get", "gotten": "get", "gets": "get", "getting": "get",
    "went": "go", "gone": "go", "goes": "go", "going": "go",
    "came": "come", "comes": "come", "coming": "come",
    "saw": "see", "seen": "see", "sees": "see", "seeing": "see",
    "gave": "give", "given": "give", "gives": "give", "giving": "give",
    "kept": "keep", "keeps": "keep", "keeping": "keep",
    "found": "find", "finds": "find", "finding": "find",
    "left": "leave", "leaves": "leave", "leaving": "leave",
    "felt": "feel", "feels": "feel", "feeling": "feel",
    "told": "tell", "tells": "tell", "telling": "tell",
    "thought": "think", "thinks": "think", "thinking": "think",
    "knew": "know", "known": "know", "knows": "know", "knowing": "know",
    "won": "win", "wins": "win", "winning": "win",
    "ran": "run", "runs": "run", "running": "run",
    "began": "begin", "begun": "begin", "begins": "begin",
    "finished": "finish", "finishes": "finish", "finishing": "finish",
    "arrived": "arrive", "arrives": "arrive", "arriving": "arrive",
    "deleted": "delete", "deletes": "delete", "deleting": "delete",
    "stayed": "stay", "stays": "stay", "staying": "stay",
    "turned": "turn", "turns": "turn", "turning": "turn",
    "worked": "work", "works": "work", "working": "work",
    "looked": "look", "looks": "look", "looking": "look",
    "used": "use", "uses": "use", "using": "use",
    "closed": "close", "closes": "close", "closing": "close",
    "locked": "lock", "locks": "lock", "locking": "lock",
    "ended": "end", "ends": "end", "ending": "end",
    "depends": "depend", "depended": "depend", "depending": "depend",
    "men": "man", "women": "woman", "children": "child", "people": "person",
    "minutes": "minute", "books": "book", "keys": "key", "plants": "plant",
    "photos": "photo", "times": "time", "things": "thing", "days": "day",
}

TOKEN_RE = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")


def lemmatise(word):
    lower = word.lower()
    if lower in IRREGULAR:
        return IRREGULAR[lower]
    if lower in FUNCTION_WORDS:
        return lower
    if lower.endswith("ies") and len(lower) > 4:
        return lower[:-3] + "y"
    if lower.endswith("ing") and len(lower) > 5:
        stem = lower[:-3]
        if len(stem) > 2 and stem[-1] == stem[-2]:
            stem = stem[:-1]
        return stem if stem.endswith("e") else stem + ("e" if stem[-1] in "vz" else "")
    if lower.endswith("ed") and len(lower) > 4:
        stem = lower[:-2]
        if len(stem) > 2 and stem[-1] == stem[-2]:
            stem = stem[:-1]
        return stem
    if lower.endswith("es") and len(lower) > 4:
        return lower[:-2]
    if lower.endswith("s") and not lower.endswith("ss") and len(lower) > 3:
        return lower[:-1]
    return lower


def guess_pos(word, lemma):
    lower = word.lower()
    if lower in FUNCTION_WORDS:
        return "FUNC"
    if lower.endswith("ly"):
        return "ADV"
    if lower.endswith(("ing", "ed")) or lemma in IRREGULAR.values():
        return "VERB"
    if lower.endswith(("tion", "ment", "ness", "ity")):
        return "NOUN"
    return "WORD"


def tokenise(sentence):
    tokens = []
    for match in TOKEN_RE.finditer(sentence):
        surface = match.group(0)
        lemma = lemmatise(surface)
        pos = guess_pos(surface, lemma)
        tokens.append({
            "surface": surface,
            "lemma": lemma,
            "pos": pos,
            "isContent": lemma not in FUNCTION_WORDS,
        })
    return tokens


def build(seed_path, pack_id, version, lang, source_lang, title):
    chunks = []
    seen_phrases = set()
    errors = []

    with open(seed_path, encoding="utf-8") as handle:
        for line_no, raw in enumerate(handle, start=1):
            line = raw.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                errors.append("line %d: expected 3 tab separated columns" % line_no)
                continue
            phrase, context, translation = (p.strip() for p in parts)

            key = phrase.lower()
            if key in seen_phrases:
                errors.append("line %d: duplicate phrase %s" % (line_no, phrase))
                continue
            seen_phrases.add(key)

            # The carrier sentence must literally contain the phrase, otherwise
            # the target span cannot be resolved and cloze would be wrong.
            start = context.lower().find(phrase.lower())
            if start < 0:
                head = phrase.split()[0].lower()
                probe = context.lower().find(head)
                if probe < 0:
                    errors.append("line %d: phrase not found in carrier sentence" % line_no)
                    continue
                start = probe
                end = probe + len(phrase)
            else:
                end = start + len(phrase)
            end = min(end, len(context))

            if not translation:
                errors.append("line %d: empty translation" % line_no)
                continue
            if len(phrase.split()) > 8:
                errors.append("line %d: phrase longer than 8 words" % line_no)
                continue

            chunks.append({
                "id": "%s-%04d" % (pack_id, len(chunks) + 1),
                "text": phrase,
                "context": context,
                "translation": translation,
                "targetStart": start,
                "targetEnd": end,
                # Seed order is the frequency order: the selector uses this to
                # prefer common phrases when everything else is equal.
                "freqRank": len(chunks) + 1,
                "tokens": tokenise(context),
                "audioRef": None,
            })

    return chunks, errors


def main():
    parser = argparse.ArgumentParser(description="Build an Ikna chunk pack")
    parser.add_argument("--seed", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--pack-id", default="en-ru-core")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--lang", default="en")
    parser.add_argument("--source-lang", default="ru")
    parser.add_argument("--title", default="English core chunks")
    parser.add_argument("--strict", action="store_true", help="fail on any validation error")
    args = parser.parse_args()

    chunks, errors = build(
        args.seed, args.pack_id, args.version, args.lang, args.source_lang, args.title
    )

    for message in errors:
        sys.stderr.write("warning: " + message + "\n")
    if errors and args.strict:
        sys.stderr.write("aborting: %d validation errors\n" % len(errors))
        return 1
    if not chunks:
        sys.stderr.write("aborting: no chunks produced\n")
        return 1

    os.makedirs(args.out, exist_ok=True)
    pack_file = args.pack_id + ".jsonl"
    with open(os.path.join(args.out, pack_file), "w", encoding="utf-8") as handle:
        for chunk in chunks:
            handle.write(json.dumps(chunk, ensure_ascii=False))
            handle.write("\n")

    manifest_path = os.path.join(args.out, "manifest.json")
    manifest = {"packs": []}
    if os.path.exists(manifest_path):
        with open(manifest_path, encoding="utf-8") as handle:
            manifest = json.load(handle)

    entry = {
        "id": args.pack_id,
        "version": args.version,
        "lang": args.lang,
        "sourceLang": args.source_lang,
        "title": args.title,
        "chunkCount": len(chunks),
        "file": pack_file,
    }
    manifest["packs"] = [p for p in manifest.get("packs", []) if p.get("id") != args.pack_id]
    manifest["packs"].append(entry)

    with open(manifest_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    total_tokens = sum(len(c["tokens"]) for c in chunks)
    lemmas = set()
    for chunk in chunks:
        for token in chunk["tokens"]:
            if token["isContent"]:
                lemmas.add(token["lemma"])

    print("pack     : %s v%d" % (args.pack_id, args.version))
    print("chunks   : %d" % len(chunks))
    print("tokens   : %d" % total_tokens)
    print("lemmas   : %d distinct content lemmas" % len(lemmas))
    print("warnings : %d" % len(errors))
    print("written  : %s" % os.path.join(args.out, pack_file))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
