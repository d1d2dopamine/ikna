#!/usr/bin/env python3
"""Normalized candidate records used between corpus adapters and deck selection."""

from __future__ import annotations

import gzip
import hashlib
import json
import unicodedata
from dataclasses import dataclass, field
from typing import Any, Iterable

CANDIDATE_VERSION = 1


def canonical_text(text: str) -> str:
    """Conservative text key for exact duplicate detection."""
    return " ".join(unicodedata.normalize("NFKC", text).split()).casefold()


def candidate_id(collection: str, lang: str, meaning_lang: str, context: str, meaning: str) -> str:
    parts = (
        collection.strip().lower(),
        lang.strip().lower(),
        meaning_lang.strip().lower(),
        canonical_text(context),
        canonical_text(meaning),
    )
    if not all(parts):
        raise ValueError("candidate identity fields must be non-empty")
    digest = hashlib.sha256("\0".join(parts).encode("utf-8")).hexdigest()[:20]
    return "c1:%s:%s-%s:%s" % (parts[0], parts[1], parts[2], digest)


@dataclass(frozen=True)
class Origin:
    source_family: str
    source_version: str
    context_ref: str
    meaning_ref: str
    alignment_score: float | None = None
    attribution: dict[str, Any] = field(default_factory=dict)

    def key(self) -> tuple[str, str, str, str]:
        return (self.source_family, self.source_version, self.context_ref, self.meaning_ref)

    def to_dict(self) -> dict[str, Any]:
        value: dict[str, Any] = {
            "sourceFamily": self.source_family,
            "sourceVersion": self.source_version,
            "contextRef": self.context_ref,
            "meaningRef": self.meaning_ref,
        }
        if self.alignment_score is not None:
            value["alignmentScore"] = self.alignment_score
        if self.attribution:
            value["attribution"] = self.attribution
        return value

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "Origin":
        return cls(
            source_family=value["sourceFamily"],
            source_version=value["sourceVersion"],
            context_ref=value["contextRef"],
            meaning_ref=value["meaningRef"],
            alignment_score=value.get("alignmentScore"),
            attribution=dict(value.get("attribution") or {}),
        )


@dataclass
class Candidate:
    collection: str
    lang: str
    meaning_lang: str
    context: str
    meaning: str
    origins: list[Origin]
    id: str | None = None

    def __post_init__(self) -> None:
        self.collection = self.collection.strip().lower()
        self.lang = self.lang.strip().lower()
        self.meaning_lang = self.meaning_lang.strip().lower()
        self.context = self.context.strip()
        self.meaning = self.meaning.strip()
        if not self.origins:
            raise ValueError("candidate has no provenance")
        if not all((self.collection, self.lang, self.meaning_lang, self.context, self.meaning)):
            raise ValueError("candidate has an empty required field")
        expected = candidate_id(self.collection, self.lang, self.meaning_lang, self.context, self.meaning)
        if self.id is None:
            self.id = expected
        elif self.id != expected:
            raise ValueError("candidate id does not match its content")

    def to_dict(self) -> dict[str, Any]:
        return {
            "candidateVersion": CANDIDATE_VERSION,
            "id": self.id,
            "collection": self.collection,
            "lang": self.lang,
            "meaningLang": self.meaning_lang,
            "context": self.context,
            "meaning": self.meaning,
            "origins": [origin.to_dict() for origin in self.origins],
        }

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "Candidate":
        if value.get("candidateVersion") != CANDIDATE_VERSION:
            raise ValueError("unsupported candidateVersion")
        return cls(
            id=value["id"],
            collection=value["collection"],
            lang=value["lang"],
            meaning_lang=value["meaningLang"],
            context=value["context"],
            meaning=value["meaning"],
            origins=[Origin.from_dict(item) for item in value["origins"]],
        )


def merge_candidates(records: Iterable[Candidate]) -> list[Candidate]:
    """Exact-deduplicate within a collection while preserving every origin.

    A sentence pair in two different user-facing collections intentionally remains
    two candidates. Collection choice is part of the content contract, not a
    source implementation detail.
    """
    merged: dict[str, Candidate] = {}
    for record in records:
        existing = merged.get(record.id)
        if existing is None:
            merged[record.id] = Candidate(
                id=record.id,
                collection=record.collection,
                lang=record.lang,
                meaning_lang=record.meaning_lang,
                context=record.context,
                meaning=record.meaning,
                origins=list(record.origins),
            )
            continue
        seen = {origin.key() for origin in existing.origins}
        for origin in record.origins:
            if origin.key() not in seen:
                existing.origins.append(origin)
                seen.add(origin.key())
    return list(merged.values())


def _open_jsonl(path: str, mode: str = "rt"):
    if str(path).endswith(".gz"):
        return gzip.open(path, mode, compresslevel=6, encoding="utf-8", newline="" if "w" in mode else None)
    return open(path, mode.replace("t", ""), encoding="utf-8", newline="" if "w" in mode else None)


def read_jsonl(path: str) -> list[Candidate]:
    records: list[Candidate] = []
    with _open_jsonl(path, "rt") as handle:
        for number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                records.append(Candidate.from_dict(json.loads(line)))
            except Exception as exc:
                raise ValueError("%s:%d: %s" % (path, number, exc)) from exc
    return records


def write_jsonl(path: str, records: Iterable[Candidate]) -> int:
    count = 0
    with _open_jsonl(path, "wt") as handle:
        for record in records:
            handle.write(json.dumps(record.to_dict(), ensure_ascii=False, sort_keys=True) + "\n")
            count += 1
    return count


def merge_candidate_files(inputs: Iterable[str], output: str, db_path: str | None = None) -> tuple[int, int]:
    """Disk-backed exact deduplication for catalogue-scale candidate files.

    Returns (input_records, output_records). Exact duplicates are merged only when
    their candidate id matches, which already includes collection and language pair.
    Every distinct origin is retained.
    """
    import os
    import sqlite3
    import tempfile

    created_temp = db_path is None
    if db_path is None:
        fd, db_path = tempfile.mkstemp(prefix="ikna-catalogue-dedupe-", suffix=".sqlite3")
        os.close(fd)
    input_count = 0
    try:
        db = sqlite3.connect(db_path)
        try:
            db.execute("PRAGMA journal_mode=OFF")
            db.execute("PRAGMA synchronous=OFF")
            db.execute("PRAGMA temp_store=FILE")
            db.execute(
                "CREATE TABLE IF NOT EXISTS candidates (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL, base_json TEXT NOT NULL)"
            )
            db.execute(
                "CREATE TABLE IF NOT EXISTS origins (candidate_id TEXT NOT NULL, origin_key TEXT NOT NULL, origin_json TEXT NOT NULL, PRIMARY KEY(candidate_id, origin_key))"
            )
            for path in inputs:
                with _open_jsonl(path, "rt") as handle:
                    for number, line in enumerate(handle, start=1):
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            value = json.loads(line)
                            record = Candidate.from_dict(value)
                        except Exception as exc:
                            raise ValueError("%s:%d: %s" % (path, number, exc)) from exc
                        input_count += 1
                        base = record.to_dict()
                        origins = base.pop("origins")
                        db.execute(
                            "INSERT OR IGNORE INTO candidates(id, base_json) VALUES (?, ?)",
                            (record.id, json.dumps(base, ensure_ascii=False, sort_keys=True)),
                        )
                        for origin in origins:
                            key = "\0".join(
                                str(origin.get(name, ""))
                                for name in ("sourceFamily", "sourceVersion", "contextRef", "meaningRef")
                            )
                            db.execute(
                                "INSERT OR IGNORE INTO origins(candidate_id, origin_key, origin_json) VALUES (?, ?, ?)",
                                (record.id, key, json.dumps(origin, ensure_ascii=False, sort_keys=True)),
                            )
                        if input_count % 10000 == 0:
                            db.commit()
            db.commit()

            output_count = 0
            with _open_jsonl(output, "wt") as out:
                for candidate_id, base_json in db.execute("SELECT id, base_json FROM candidates ORDER BY seq"):
                    value = json.loads(base_json)
                    value["origins"] = [
                        json.loads(row[0])
                        for row in db.execute(
                            "SELECT origin_json FROM origins WHERE candidate_id = ? ORDER BY rowid", (candidate_id,)
                        )
                    ]
                    # Re-validate before emitting a canonical merged row.
                    record = Candidate.from_dict(value)
                    out.write(json.dumps(record.to_dict(), ensure_ascii=False, sort_keys=True) + "\n")
                    output_count += 1
            return input_count, output_count
        finally:
            db.close()
    finally:
        if created_temp and db_path and os.path.exists(db_path):
            os.remove(db_path)
