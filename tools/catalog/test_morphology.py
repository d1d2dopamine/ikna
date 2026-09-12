#!/usr/bin/env python3
"""Deterministic tests for Catalogue v2 morphology enrichment."""
from __future__ import annotations
import json, shutil, subprocess, sys, tempfile
from pathlib import Path
from morphology.manifest import ALLOWED_LICENCES, load_manifest
from morphology.store import build_store, MorphologyResolver
from morphology.model import canonical_feats

HERE = Path(__file__).resolve().parent
FIX = HERE / "fixtures" / "morphology"

def main():
    assert canonical_feats("Tense=Past|Mood=Ind|Person=2") == "Mood=Ind|Person=2|Tense=Past"
    registry = json.loads((HERE / "sources" / "catalogue-v2-morphology-sources.json").read_text(encoding="utf-8"))
    assert set(registry["allowedLicences"]) == ALLOWED_LICENCES
    assert {row["id"] for row in registry["sources"]} == {"unimorph", "universal-dependencies"}
    production = json.loads((HERE / "sources" / "catalogue-v2-ud-production.json").read_text(encoding="utf-8"))
    assert production["udRelease"] == "r2.18"
    assert set(production["datasets"]) == {"en","ru","pl","es","fr","de","it","pt","zh","ja","ko"}
    assert all(row["licence"] == "CC-BY-SA-4.0" for row in production["datasets"].values())
    manifest = load_manifest(FIX / "manifest.json")
    tmp = Path(tempfile.mkdtemp(prefix="ikna-morph-"))
    try:
        db = tmp / "morph.sqlite3"
        stats = build_store(manifest, db)
        assert stats["datasets"] == 2
        cards = [json.loads(x) for x in (FIX / "cards-es.jsonl").read_text(encoding="utf-8").splitlines() if x]
        with MorphologyResolver(db) as resolver:
            out = [resolver.enrich_card(card, "es")[0] for card in cards]
        # Exact UD context disambiguates and emits canonical UPOS/FEATS.
        first = out[0]["tokens"][0]
        assert first["lemma"] == "hablar" and first["lemmaSource"] == "ud" and first["upos"] == "VERB"
        assert first["feats"] == "Mood=Ind|Number=Sing|Person=2|Tense=Past|VerbForm=Fin"
        # UniMorph-only unique form resolves lemma without inventing UD features.
        second = out[1]["tokens"][1]
        assert second["lemma"] == "hablar" and second["lemmaSource"] == "unimorph"
        assert "upos" not in second and "feats" not in second
        # Known ambiguous form remains identity when there is no exact-context match.
        third = out[2]["tokens"][1]
        assert third["lemma"] == "vino" and third["lemmaSource"] == "identity"
        # Exact UD context beats form-level disagreement because it observes this use.
        fourth = out[3]["tokens"][0]
        assert fourth["lemma"] == "ser" and fourth["lemmaSource"] == "ud" and fourth["upos"] == "AUX"
        # Away from the exact UD sentence, two agreeing sources may resolve the form.
        fifth = out[4]["tokens"][1]
        assert fifth["lemma"] == "hablar" and fifth["lemmaSource"] == "unimorph+ud"
        assert fifth["upos"] == "VERB" and fifth["feats"] == "Mood=Ind|Number=Sing|Person=2|Tense=Past|VerbForm=Fin"
        # Coarse legacy fields are never rewritten by morphology.
        assert out[0]["tokens"][0]["pos"] == "WORD" and out[0]["tokens"][0]["isContent"] is True
        # Part 4 can turn an audited UD checkout into a hash-pinned production manifest.
        prod_root = tmp / "production"
        repo = prod_root / "UD_Spanish-GSD"
        repo.mkdir(parents=True)
        shutil.copy2(FIX / "ud-es.conllu", repo / "es_fixture-ud-test.conllu")
        prod_manifest = tmp / "production-manifest.json"
        subprocess.run(
            [sys.executable, str(HERE / "prepare_ud_manifest.py"),
             "--root", str(prod_root), "--langs", "es", "--out", str(prod_manifest)],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        raw_prod = json.loads(prod_manifest.read_text(encoding="utf-8"))
        assert not Path(raw_prod["datasets"][0]["files"][0]["path"]).is_absolute()
        pinned = load_manifest(prod_manifest)
        assert len(pinned.datasets) == 1 and pinned.datasets[0].lang == "es"
        assert pinned.datasets[0].source_version == "r2.18"

        # NC is rejected before any data is indexed.
        bad = json.loads((FIX / "manifest.json").read_text(encoding="utf-8"))
        bad["datasets"][0]["licence"]["id"] = "CC-BY-NC-SA-4.0"
        bad_path = tmp / "bad.json"
        # keep fixture file path valid from tmp by making it absolute
        bad["datasets"][0]["files"][0]["path"] = str((FIX / "unimorph-es.tsv").resolve())
        bad["datasets"][1]["files"][0]["path"] = str((FIX / "ud-es.conllu").resolve())
        bad_path.write_text(json.dumps(bad), encoding="utf-8")
        try:
            load_manifest(bad_path)
        except ValueError as exc:
            assert "disallowed or unaudited licence" in str(exc)
        else:
            raise AssertionError("NC morphology dataset was accepted")
    finally:
        shutil.rmtree(tmp)
    print("Catalogue v2 morphology contracts: OK")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
