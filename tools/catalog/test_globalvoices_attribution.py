#!/usr/bin/env python3
from __future__ import annotations

import gzip
import json
import tempfile
import zipfile
from pathlib import Path

from globalvoices_attribution import resolve, valid_globalvoices_url
from globalvoices_native import extract_native
from globalvoices_manifest import FetchResult, build_manifest, candidate_url, parse_article_metadata, resolve_document, shard_index_for_document
from globalvoices_manifest_merge import merge as merge_manifest_shards
from globalvoices_xces_map import extract
from ingest.model import Origin
from ingest.registry import SourceRegistry


def _write_zip(path: Path, member: str, xml: str, gzip_member: bool = False) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        data = xml.encode("utf-8")
        if gzip_member:
            data = gzip.compress(data)
        archive.writestr(member + (".gz" if gzip_member else ""), data)


def main() -> int:
    assert valid_globalvoices_url("https://globalvoices.org/2024/example/")
    assert valid_globalvoices_url("https://es.globalvoices.org/example/")
    assert not valid_globalvoices_url("https://globalvoices.org.evil.example/x")
    assert not valid_globalvoices_url("http://globalvoices.org/x")

    # OPUS document identity may propose exactly one date/slug URL, but it is
    # not admitted until a live page proves canonical URL + contributor data.
    spanish_doc = "es/2012_07_06_puerto-rico-escuelas-bilingues-reviven-el-debate-sobre-el-idioma_.xml"
    assert candidate_url(spanish_doc) == (
        "https://es.globalvoices.org/2012/07/06/"
        "puerto-rico-escuelas-bilingues-reviven-el-debate-sobre-el-idioma/"
    )
    assert candidate_url("jp/2018_01_02_example_.xml") == "https://jp.globalvoices.org/2018/01/02/example/"
    assert candidate_url("zhs/2018_01_02_example_.xml") == "https://zhs.globalvoices.org/2018/01/02/example/"
    assert candidate_url("es/not-a-date.xml") is None

    article_html = '''
    <html><head>
      <link rel="canonical" href="https://es.globalvoices.org/2012/07/06/example/">
      <script type="application/ld+json">
        {"@context":"https://schema.org","@type":"NewsArticle",
         "url":"https://es.globalvoices.org/2012/07/06/example/",
         "author":{"@type":"Person","name":"Ángel Carrión"},
         "translator":{"@type":"Person","name":"Traductora Ejemplo"}}
      </script>
    </head><body><a rel="author">Ángel Carrión</a></body></html>
    '''
    canonical, contributors = parse_article_metadata("https://es.globalvoices.org/x/", article_html)
    assert canonical == "https://es.globalvoices.org/2012/07/06/example/"
    assert contributors == ["Ángel Carrión", "Traductora Ejemplo"]

    def good_fetch(_url: str) -> FetchResult:
        return FetchResult("https://es.globalvoices.org/2012/07/06/example/", article_html)

    resolved, reason = resolve_document(spanish_doc, fetcher=good_fetch)
    assert reason == "resolved" and resolved is not None
    assert resolved["contributors"] == ["Ángel Carrión", "Traductora Ejemplo"]

    def evil_fetch(_url: str) -> FetchResult:
        return FetchResult("https://example.org/stolen/", article_html)

    assert resolve_document(spanish_doc, fetcher=evil_fetch)[1] == "redirect-outside-globalvoices"

    registry = SourceRegistry.load(str(Path(__file__).resolve().parent / "sources" / "catalogue-v2-sources.json"))
    bad_origin = Origin(
        "globalvoices",
        "v2018q4",
        "gv:c",
        "gv:m",
        attribution={
            "articleUrl": "https://globalvoices.org.evil.example/a",
            "contributors": ["Author"],
        },
    )
    try:
        registry.get("globalvoices").validate_origin_for_ingestion(bad_origin.to_dict())
    except ValueError as exc:
        assert "globalvoices.org" in str(exc)
    else:
        raise AssertionError("lookalike Global Voices host was accepted")

    with tempfile.TemporaryDirectory(prefix="ikna-gv-") as td:
        root = Path(td)

        manifest_map = root / "manifest-map.jsonl.gz"
        with gzip.open(manifest_map, "wt", encoding="utf-8") as handle:
            handle.write(json.dumps({
                "line": 1,
                "contextDocument": spanish_doc,
                "meaningDocument": "en/2012_07_06_example_.xml",
            }) + "\n")
        english_html = article_html.replace(
            "https://es.globalvoices.org/2012/07/06/example/",
            "https://globalvoices.org/2012/07/06/example/",
        ).replace("Ángel Carrión", "Example Author")
        def fixture_fetch(url: str) -> FetchResult:
            if url.startswith("https://es.globalvoices.org/"):
                return FetchResult("https://es.globalvoices.org/2012/07/06/example/", article_html)
            return FetchResult("https://globalvoices.org/2012/07/06/example/", english_html)
        built, built_report = build_manifest(str(manifest_map), workers=1, fetcher=fixture_fetch)
        assert len(built) == 2
        assert built_report["publicationSafe"] is True
        assert built_report["summary"]["resolvedDocuments"] == 2

        # Full provenance builds shard by a stable SHA-256 mapping and reuse a
        # JSONL cache. A second run must perform no live fetches for cached
        # verified documents.
        cache = root / "manifest-cache.jsonl"
        fetch_calls = 0
        def counted_fetch(url: str) -> FetchResult:
            nonlocal fetch_calls
            fetch_calls += 1
            return fixture_fetch(url)
        first, first_report = build_manifest(
            str(manifest_map), workers=1, fetcher=counted_fetch, cache_path=str(cache)
        )
        assert len(first) == 2 and fetch_calls == 2
        fetch_calls = 0
        second, second_report = build_manifest(
            str(manifest_map), workers=1, fetcher=counted_fetch, cache_path=str(cache)
        )
        assert second == first and fetch_calls == 0
        assert second_report["summary"]["cacheHits"] == 2
        assert second_report["summary"]["networkAttempts"] == 0

        assignments = {doc: shard_index_for_document(doc, 2) for doc in (spanish_doc, "en/2012_07_06_example_.xml")}
        assert set(assignments.values()) <= {0, 1}
        shard_rows = []
        shard_reports = []
        for index in range(2):
            rows, report = build_manifest(
                str(manifest_map), workers=1, fetcher=fixture_fetch, shard_count=2, shard_index=index
            )
            mp = root / f"shard-{index}.jsonl"
            rp = root / f"shard-{index}.json"
            mp.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")
            rp.write_text(json.dumps(report), encoding="utf-8")
            shard_rows.append(str(mp))
            shard_reports.append(str(rp))
        merged, merged_report = merge_manifest_shards(
            str(manifest_map), shard_rows, shard_reports, expected_shards=2
        )
        assert len(merged) == 2
        assert merged_report["completeScan"] is True
        assert merged_report["summary"]["verifiedAlignmentRows"] == 1

        xces = root / "pair.xml"
        xces.write_text(
            '<?xml version="1.0"?><cesAlign><linkGrp fromDoc="en/a.xml" toDoc="es/a.xml">'
            '<link id="l1" xtargets="1;1"/><link id="l2" xtargets="2 3;2"/><link xtargets="4;"/>'
            '</linkGrp></cesAlign>',
            encoding="utf-8",
        )
        mapped, map_stats = extract(str(xces), "auto", "en")
        assert len(mapped) == 2
        assert map_stats["emptySideSkipped"] == 1
        assert mapped[0]["contextDocument"] == "en/a.xml"

        # Native extraction must preserve 1:n XCES identity instead of trying
        # to match XCES link numbers to a derived Moses line number.
        en_zip = root / "en.zip"
        es_zip = root / "es.zip"
        _write_zip(
            en_zip,
            "GlobalVoices/raw/en/a.xml",
            '<doc><s id="1">Hello world.</s><s id="2">This is</s><s id="3">one alignment.</s></doc>',
            gzip_member=True,
        )
        # OPUS Global Voices contains a few historical raw XML files with
        # malformed text (for example an unescaped ampersand).  Native
        # extraction must salvage explicit sentence ids from the same member
        # rather than aborting the whole language pair or falling back to line
        # numbers.
        _write_zip(
            es_zip,
            "es/a.xml",
            '<doc><s id="1">Hola & mundo.</s><s id="2">Esta es una alineación.</s></doc>',
        )
        native_report, samples = extract_native(
            str(xces), str(en_zip), str(es_zip), "en", "es",
            str(root / "native.en.txt.gz"), str(root / "native.es.txt.gz"),
            str(root / "native-map.jsonl.gz"), sample_limit=5,
        )
        assert native_report["summary"]["emittedRows"] == 2
        assert native_report["summary"]["emptySideSkipped"] == 1
        assert native_report["nativeResolutionRate"] == 1.0
        assert native_report["nativeXml"]["strictParseFailures"] == 1
        assert native_report["nativeXml"]["salvagedMembers"] == 1
        assert native_report["nativeXml"]["unrecoverableMembers"] == 0
        assert samples[0]["meaning"] == "Hola & mundo."
        assert samples[1]["context"] == "This is one alignment."
        with gzip.open(root / "native.en.txt.gz", "rt", encoding="utf-8") as handle:
            assert handle.read().splitlines() == ["Hello world.", "This is one alignment."]

        amap = root / "map.jsonl.gz"
        manifest = root / "articles.jsonl"
        with gzip.open(amap, "wt", encoding="utf-8") as handle:
            handle.write(
                "\n".join(
                    [
                        json.dumps({"line": 1, "contextDocument": "en/a.xml", "meaningDocument": "es/a.xml"}),
                        json.dumps({"line": 2, "contextDocument": "en/b.xml", "meaningDocument": "es/b.xml"}),
                    ]
                )
                + "\n"
            )
        manifest.write_text(
            "\n".join(
                [
                    json.dumps(
                        {
                            "document": "en/a.xml",
                            "articleUrl": "https://globalvoices.org/a/",
                            "contributors": ["Author"],
                        }
                    ),
                    json.dumps(
                        {
                            "document": "es/a.xml",
                            "articleUrl": "https://es.globalvoices.org/a/",
                            "contributors": ["Translator"],
                        }
                    ),
                    json.dumps(
                        {
                            "document": "en/b.xml",
                            "articleUrl": "https://globalvoices.org/b/",
                            "contributors": ["Other Author"],
                        }
                    ),
                ]
            )
            + "\n",
            encoding="utf-8",
        )
        rows, report = resolve(str(amap), str(manifest))
        assert len(rows) == 1
        assert rows[0]["contributors"] == ["Author", "Translator"]
        assert report["status"] == "incomplete-attribution"
        assert report["summary"]["unresolvedRows"] == 1
        assert report["missingDocuments"][0]["document"] == "es/b.xml"

    print("Global Voices Part 9 attribution/native-XCES contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
