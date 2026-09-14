#!/usr/bin/env python3
from __future__ import annotations

import gzip
import json
import tempfile
import zipfile
from pathlib import Path

from globalvoices_attribution import resolve, valid_globalvoices_url
from globalvoices_native import extract_native
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
        _write_zip(
            es_zip,
            "es/a.xml",
            '<doc><s id="1">Hola mundo.</s><s id="2">Esta es una alineación.</s></doc>',
        )
        native_report, samples = extract_native(
            str(xces), str(en_zip), str(es_zip), "en", "es",
            str(root / "native.en.txt.gz"), str(root / "native.es.txt.gz"),
            str(root / "native-map.jsonl.gz"), sample_limit=5,
        )
        assert native_report["summary"]["emittedRows"] == 2
        assert native_report["summary"]["emptySideSkipped"] == 1
        assert native_report["nativeResolutionRate"] == 1.0
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
