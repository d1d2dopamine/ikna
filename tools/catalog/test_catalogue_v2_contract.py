#!/usr/bin/env python3
"""Deterministic contract checks for the Catalogue v2 specification fixtures."""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURES = HERE / "fixtures" / "v2"
SCHEMAS = HERE / "schema"
sys.path.insert(0, str(HERE))

from catalogue_v2 import TARGET_IDENTITY_METHOD, TARGET_IDENTITY_VERSION, target_id

V1_INDEX_FIELDS = {"version", "builtAt", "decks", "pairs"}
V1_DECK_FIELDS = {
    "id", "title", "lang", "meaningLang", "chunkCount", "file", "sizeBytes",
    "subject", "level", "licence", "attribution", "sources", "phonetics", "version",
}
V1_CARD_FIELDS = {
    "id", "text", "context", "translation", "targetStart", "targetEnd", "freqRank", "tokens",
}
V2_CARD_FIELDS = {"targetId", "contextId", "meaningId", "sourceFamily"}
V1_TOKEN_FIELDS = {"surface", "lemma", "pos", "isContent"}


def require_keys(value, required, where):
    missing = sorted(required - set(value))
    if missing:
        raise AssertionError("%s missing %s" % (where, ", ".join(missing)))


def utf16_slice(text, start, end):
    raw = text.encode("utf-16-le")
    return raw[start * 2:end * 2].decode("utf-16-le")


def main():
    for name in (
        "catalogue-v2-index.schema.json",
        "catalogue-v2-card.schema.json",
        "catalogue-v2-candidate.schema.json",
        "catalogue-v2-source-registry.schema.json",
        "catalogue-v2-morphology-manifest.schema.json",
        "catalogue-v2-morphology-source-registry.schema.json",
    ):
        schema = json.loads((SCHEMAS / name).read_text(encoding="utf-8"))
        if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
            raise AssertionError("%s does not declare JSON Schema 2020-12" % name)

    index = json.loads((FIXTURES / "index.json").read_text(encoding="utf-8"))
    require_keys(index, V1_INDEX_FIELDS, "index")
    if index.get("version") != 2 or index.get("catalogueVersion") != 2:
        raise AssertionError("v2 fixture must identify both schema and catalogue generation as 2")

    identity = index.get("targetIdentity") or {}
    if identity.get("version") != TARGET_IDENTITY_VERSION:
        raise AssertionError("target identity version disagrees with reference helper")
    if identity.get("method") != TARGET_IDENTITY_METHOD:
        raise AssertionError("target identity method disagrees with reference helper")

    collections = {row["id"] for row in index.get("collections", [])}
    sources = {row["id"] for row in index.get("sourceFamilies", [])}
    if len(collections) != len(index.get("collections", [])):
        raise AssertionError("collection ids must be unique")
    if len(sources) != len(index.get("sourceFamilies", [])):
        raise AssertionError("source-family ids must be unique")

    decks = index.get("decks") or []
    if not decks:
        raise AssertionError("fixture needs at least one deck")
    for deck in decks:
        require_keys(deck, V1_DECK_FIELDS, "deck %s" % deck.get("id", "?"))
        if deck.get("collection") not in collections:
            raise AssertionError("deck refers to unknown collection")
        if deck.get("sourceFamily") not in sources:
            raise AssertionError("deck refers to unknown source family")
        expected_file = deck["id"] + ".jsonl"
        if deck.get("file") != expected_file:
            raise AssertionError("deck file must be deck id plus .jsonl")
        if ("-" + deck["collection"] + "-") not in deck["id"]:
            raise AssertionError("v2 deck id must include its collection")

    cards_path = FIXTURES / decks[0]["file"]
    cards = [json.loads(line) for line in cards_path.read_text(encoding="utf-8").splitlines() if line]
    if len(cards) != decks[0]["chunkCount"]:
        raise AssertionError("fixture deck chunkCount does not match JSONL line count")

    seen_contexts = set()
    seen_target_ids = set()
    for card in cards:
        require_keys(card, V1_CARD_FIELDS | V2_CARD_FIELDS, "card %s" % card.get("id", "?"))
        if card["sourceFamily"] != decks[0]["sourceFamily"]:
            raise AssertionError("card sourceFamily disagrees with its deck")
        if not card["contextId"].startswith(card["sourceFamily"] + ":"):
            raise AssertionError("contextId must be namespaced by sourceFamily")
        if not card["meaningId"].startswith(card["sourceFamily"] + ":"):
            raise AssertionError("meaningId must be namespaced by sourceFamily")
        if card["targetId"] != target_id(decks[0]["lang"], card["text"]):
            raise AssertionError("targetId does not match the declared exact identity method")
        contexts = [card] + [row for row in (card.get("contexts") or []) if isinstance(row, dict)]
        for context in contexts:
            selected = utf16_slice(context["context"], context["targetStart"], context["targetEnd"])
            if target_id(decks[0]["lang"], selected) != card["targetId"]:
                raise AssertionError("stored target offsets do not select the declared exact target")
            for token in context["tokens"]:
                require_keys(token, V1_TOKEN_FIELDS, "token")
            if not context["contextId"].startswith(context["sourceFamily"] + ":"):
                raise AssertionError("contextId must be namespaced by sourceFamily")
            seen_contexts.add(context["contextId"])
        seen_target_ids.add(card["targetId"])

    if len(cards) != 1 or len(seen_target_ids) != 1 or len(seen_contexts) < 2:
        raise AssertionError("fixture must prove one target row can own multiple source contexts")
    if decks[0].get("contextCount") != len(seen_contexts):
        raise AssertionError("fixture deck contextCount must count primary plus alternative contexts")

    aggregate_pairs = {(p["lang"], p["meaningLang"]) for p in index.get("pairs", [])}
    for pair in index.get("collectionPairs", []):
        if pair.get("collection") not in collections:
            raise AssertionError("collectionPair refers to unknown collection")
        if (pair.get("lang"), pair.get("meaningLang")) not in aggregate_pairs:
            raise AssertionError("collectionPair must also exist in compatibility pairs")

    print("Catalogue v2 contract fixtures: OK")
    print("  decks: %d" % len(decks))
    print("  target rows: %d" % len(cards))
    print("  exact target ids: %d" % len(seen_target_ids))
    print("  distinct source contexts: %d" % len(seen_contexts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
