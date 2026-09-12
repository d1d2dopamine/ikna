#!/usr/bin/env python3
"""Disk-backed morphology index and conservative resolver."""

from __future__ import annotations

import json
import sqlite3
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

from .manifest import MorphDataset, MorphManifest
from .model import MORPHOLOGY_POLICY, MORPHOLOGY_RULE_VERSION, canonical_feats, context_key, surface_key


SCHEMA = """
PRAGMA journal_mode=DELETE;
PRAGMA synchronous=OFF;
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE datasets (
    id TEXT PRIMARY KEY,
    source_family TEXT NOT NULL,
    kind TEXT NOT NULL,
    lang TEXT NOT NULL,
    source_version TEXT NOT NULL,
    source_url TEXT NOT NULL,
    licence_id TEXT NOT NULL,
    licence_name TEXT NOT NULL,
    licence_url TEXT NOT NULL,
    attribution TEXT NOT NULL
);
CREATE TABLE unimorph (
    lang TEXT NOT NULL,
    surface_key TEXT NOT NULL,
    lemma TEXT NOT NULL,
    raw_features TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    PRIMARY KEY (lang, surface_key, lemma, raw_features, dataset_id)
);
CREATE INDEX unimorph_lookup ON unimorph(lang, surface_key);
CREATE TABLE ud_form (
    lang TEXT NOT NULL,
    surface_key TEXT NOT NULL,
    lemma TEXT NOT NULL,
    upos TEXT,
    feats TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    occurrences INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (lang, surface_key, lemma, upos, feats, dataset_id)
);
CREATE INDEX ud_form_lookup ON ud_form(lang, surface_key);
CREATE TABLE ud_context (
    lang TEXT NOT NULL,
    context_key TEXT NOT NULL,
    analysis_json TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    sent_id TEXT NOT NULL,
    PRIMARY KEY (lang, context_key, analysis_json, dataset_id, sent_id)
);
CREATE INDEX ud_context_lookup ON ud_context(lang, context_key);
"""


def _open(path: Path):
    if path.suffix == ".gz":
        import gzip
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return path.open("r", encoding="utf-8", errors="replace")


def _parse_unimorph(path: Path) -> Iterable[tuple[str, str, str]]:
    with _open(path) as handle:
        for number, line in enumerate(handle, start=1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 3:
                raise ValueError("%s:%d: UniMorph row needs lemma, form and features" % (path, number))
            lemma, form, features = (part.strip() for part in parts[:3])
            if not lemma or not form or not features:
                continue
            yield lemma, form, features


def _flush_ud_sentence(
    conn: sqlite3.Connection,
    dataset: MorphDataset,
    sent_id: str,
    text: str | None,
    rows: list[dict[str, Any]],
    has_multiword: bool,
) -> int:
    if not rows:
        return 0
    for row in rows:
        conn.execute(
            """
            INSERT INTO ud_form(lang,surface_key,lemma,upos,feats,dataset_id,occurrences)
            VALUES(?,?,?,?,?,?,1)
            ON CONFLICT(lang,surface_key,lemma,upos,feats,dataset_id)
            DO UPDATE SET occurrences=occurrences+1
            """,
            (dataset.lang, row["surfaceKey"], row["lemma"], row["upos"], row["feats"] or "", dataset.id),
        )

    # Exact-context evidence is used only when the original sentence text is
    # available and CoNLL-U did not split a multi-word token into synthetic rows.
    # Punctuation is omitted from the analysis because ikna PackToken stores words.
    lexical = [row for row in rows if row["upos"] != "PUNCT"]
    if text and lexical and not has_multiword:
        payload = json.dumps(lexical, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        conn.execute(
            "INSERT OR IGNORE INTO ud_context(lang,context_key,analysis_json,dataset_id,sent_id) VALUES(?,?,?,?,?)",
            (dataset.lang, context_key(text), payload, dataset.id, sent_id or "unknown"),
        )
    return len(rows)


def _load_ud(conn: sqlite3.Connection, dataset: MorphDataset, path: Path) -> tuple[int, int]:
    sentence_count = 0
    token_count = 0
    sent_id = ""
    text: str | None = None
    rows: list[dict[str, Any]] = []
    has_multiword = False

    def flush() -> None:
        nonlocal sentence_count, token_count, sent_id, text, rows, has_multiword
        if rows:
            token_count += _flush_ud_sentence(conn, dataset, sent_id, text, rows, has_multiword)
            sentence_count += 1
        sent_id = ""
        text = None
        rows = []
        has_multiword = False

    with _open(path) as handle:
        for number, raw in enumerate(handle, start=1):
            line = raw.rstrip("\n")
            if not line:
                flush()
                continue
            if line.startswith("#"):
                if line.startswith("# sent_id ="):
                    sent_id = line.split("=", 1)[1].strip()
                elif line.startswith("# text ="):
                    text = line.split("=", 1)[1].strip()
                continue
            parts = line.split("\t")
            if len(parts) != 10:
                raise ValueError("%s:%d: CoNLL-U row must have 10 columns" % (path, number))
            token_id, form, lemma, upos, _xpos, feats, _head, _deprel, _deps, _misc = parts
            if "-" in token_id:
                has_multiword = True
                continue
            if "." in token_id:
                continue
            if not token_id.isdigit():
                raise ValueError("%s:%d: invalid CoNLL-U ID %s" % (path, number, token_id))
            form = form.strip()
            lemma = lemma.strip()
            upos = upos.strip()
            if not form or lemma in ("", "_") or upos in ("", "_"):
                continue
            rows.append(
                {
                    "surface": form,
                    "surfaceKey": surface_key(form),
                    "lemma": lemma,
                    "upos": upos,
                    "feats": canonical_feats(feats),
                }
            )
    flush()
    return sentence_count, token_count


def build_store(manifest: MorphManifest, db_path: str | Path) -> dict[str, int]:
    path = Path(db_path)
    if path.exists():
        path.unlink()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    try:
        conn.executescript(SCHEMA)
        conn.execute("INSERT INTO meta(key,value) VALUES('ruleVersion',?)", (str(MORPHOLOGY_RULE_VERSION),))
        conn.execute("INSERT INTO meta(key,value) VALUES('policy',?)", (MORPHOLOGY_POLICY,))
        stats = Counter()
        for dataset in manifest.datasets:
            conn.execute(
                "INSERT INTO datasets VALUES(?,?,?,?,?,?,?,?,?,?)",
                (
                    dataset.id, dataset.source_family, dataset.kind, dataset.lang,
                    dataset.source_version, dataset.source_url, dataset.licence_id,
                    dataset.licence_name, dataset.licence_url, dataset.attribution,
                ),
            )
            if dataset.kind == "unimorph":
                for file in dataset.files:
                    for lemma, form, features in _parse_unimorph(file.path):
                        conn.execute(
                            "INSERT OR IGNORE INTO unimorph VALUES(?,?,?,?,?)",
                            (dataset.lang, surface_key(form), lemma, features, dataset.id),
                        )
                        stats["unimorphRows"] += 1
            elif dataset.kind == "ud":
                for file in dataset.files:
                    sentences, tokens = _load_ud(conn, dataset, file.path)
                    stats["udSentences"] += sentences
                    stats["udTokens"] += tokens
            else:
                raise AssertionError("validated manifest contains unsupported dataset kind")
        stats["datasets"] = len(manifest.datasets)
        conn.commit()
        return dict(stats)
    finally:
        conn.close()


class MorphologyResolver:
    def __init__(self, db_path: str | Path):
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        meta = dict(self.conn.execute("SELECT key,value FROM meta"))
        if int(meta.get("ruleVersion", "0")) != MORPHOLOGY_RULE_VERSION:
            raise ValueError("morphology DB ruleVersion does not match this code")
        if meta.get("policy") != MORPHOLOGY_POLICY:
            raise ValueError("morphology DB policy does not match this code")

    def close(self) -> None:
        self.conn.close()

    def __enter__(self) -> "MorphologyResolver":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def public_datasets(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT id,source_family,kind,lang,source_version,source_url,licence_name,licence_url,attribution FROM datasets ORDER BY id"
        ).fetchall()
        return [
            {
                "id": row["id"],
                "sourceFamily": row["source_family"],
                "kind": row["kind"],
                "lang": row["lang"],
                "sourceVersion": row["source_version"],
                "sourceUrl": row["source_url"],
                "licence": row["licence_name"],
                "licenceUrl": row["licence_url"],
                "attribution": row["attribution"],
            }
            for row in rows
        ]

    def _context_analysis(self, lang: str, context: str, tokens: list[dict[str, Any]]) -> list[dict[str, Any]] | None:
        rows = self.conn.execute(
            "SELECT analysis_json FROM ud_context WHERE lang=? AND context_key=?",
            (lang, context_key(context)),
        ).fetchall()
        if not rows:
            return None
        variants = {row["analysis_json"] for row in rows}
        if len(variants) != 1:
            return None
        analysis = json.loads(next(iter(variants)))
        if len(analysis) != len(tokens):
            return None
        for expected, token in zip(analysis, tokens):
            if expected["surfaceKey"] != surface_key(str(token.get("surface") or "")):
                return None
        return analysis

    def _form_evidence(self, lang: str, surface: str) -> dict[str, Any]:
        key = surface_key(surface)
        um_rows = self.conn.execute(
            "SELECT DISTINCT lemma FROM unimorph WHERE lang=? AND surface_key=? ORDER BY lemma",
            (lang, key),
        ).fetchall()
        um_lemmas = [row["lemma"] for row in um_rows]

        ud_rows = self.conn.execute(
            "SELECT lemma,upos,feats,SUM(occurrences) AS n FROM ud_form WHERE lang=? AND surface_key=? GROUP BY lemma,upos,feats ORDER BY lemma,upos,feats",
            (lang, key),
        ).fetchall()
        ud_lemmas = sorted({row["lemma"] for row in ud_rows})
        return {"unimorph": um_lemmas, "ud": [dict(row) for row in ud_rows], "udLemmas": ud_lemmas}

    @staticmethod
    def _unique_normalized(values: list[str]) -> str | None:
        by_key: dict[str, str] = {}
        for value in values:
            by_key.setdefault(surface_key(value), value)
        if len(by_key) == 1:
            return next(iter(by_key.values()))
        return None

    def _resolve_form(self, lang: str, token: dict[str, Any]) -> tuple[dict[str, Any], str]:
        surface = str(token.get("surface") or "").strip()
        if not surface:
            return dict(token), "invalid"
        out = dict(token)
        existing = str(out.get("lemma") or "").strip()
        existing_source = str(out.get("lemmaSource") or "").strip()
        identity = not existing or surface_key(existing) == surface_key(surface)

        evidence = self._form_evidence(lang, surface)
        um_values = evidence["unimorph"]
        ud_values = evidence["ud"]
        ud_lemma_values = evidence["udLemmas"]
        um_ambiguous = len({surface_key(v) for v in um_values}) > 1
        ud_ambiguous = len({surface_key(v) for v in ud_lemma_values}) > 1
        um_lemma = self._unique_normalized(um_values)
        ud_lemma = self._unique_normalized(ud_lemma_values)

        if not identity:
            chosen = existing
            source = existing_source or "legacy"
        elif um_ambiguous or ud_ambiguous:
            chosen = existing or surface.casefold()
            source = "identity"
        elif um_lemma and ud_lemma:
            if surface_key(um_lemma) != surface_key(ud_lemma):
                chosen = existing or surface.casefold()
                source = "identity"
            else:
                chosen = um_lemma
                source = "unimorph+ud"
        elif um_lemma:
            chosen = um_lemma
            source = "unimorph"
        elif ud_lemma:
            chosen = ud_lemma
            source = "ud"
        else:
            chosen = existing or surface.casefold()
            source = existing_source or "identity"

        out["lemma"] = chosen
        out["lemmaSource"] = source

        # UPOS may be emitted when all UD observations compatible with the chosen
        # lemma agree. FEATS is stricter: every compatible observation must carry
        # the same canonical bundle. Syncretic forms therefore get a lemma without
        # a fabricated tense/case/person bundle.
        compatible = [row for row in ud_values if surface_key(row["lemma"]) == surface_key(chosen)]
        upos_values = {row["upos"] for row in compatible if row["upos"]}
        if len(upos_values) == 1:
            out["upos"] = next(iter(upos_values))
        elif "upos" in out and out.get("upos") is None:
            out.pop("upos", None)
        feat_values = {row["feats"] for row in compatible}
        if compatible and len(feat_values) == 1:
            only = next(iter(feat_values))
            if only:
                out["feats"] = only
            else:
                out.pop("feats", None)
        elif identity:
            out.pop("feats", None)

        if source == "identity" and (um_values or ud_values):
            return out, "ambiguous"
        if source == "unimorph+ud":
            return out, "unimorph+ud"
        if source == "unimorph":
            return out, "unimorph"
        if source == "ud":
            return out, "ud-form"
        if source == "legacy":
            return out, "legacy"
        return out, "identity"

    def enrich_card(self, card: dict[str, Any], lang: str) -> tuple[dict[str, Any], Counter]:
        out = dict(card)
        tokens = [dict(token) for token in card.get("tokens") or []]
        stats = Counter(cards=1, tokens=len(tokens))
        exact = self._context_analysis(lang, str(card.get("context") or ""), tokens)
        if exact is not None:
            enriched = []
            for token, analysis in zip(tokens, exact):
                item = dict(token)
                item["lemma"] = analysis["lemma"]
                item["upos"] = analysis["upos"]
                if analysis.get("feats"):
                    item["feats"] = analysis["feats"]
                else:
                    item.pop("feats", None)
                item["lemmaSource"] = "ud"
                enriched.append(item)
                stats["udContextTokens"] += 1
            out["tokens"] = enriched
            return out, stats

        enriched = []
        for token in tokens:
            item, status = self._resolve_form(lang, token)
            enriched.append(item)
            stats[status] += 1
        out["tokens"] = enriched
        return out, stats
