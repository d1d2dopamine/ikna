#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from globalvoices_attribution import resolve, valid_globalvoices_url
from globalvoices_xces_map import extract
from ingest.model import Origin
from ingest.registry import SourceRegistry


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
            '<link xtargets="1;1"/><link xtargets="2 3;2"/><link xtargets="4;"/>'
            '</linkGrp></cesAlign>',
            encoding="utf-8",
        )
        mapped, map_stats = extract(str(xces), "auto", "en")
        assert len(mapped) == 2
        assert map_stats["emptySideSkipped"] == 1
        assert mapped[0]["contextDocument"] == "en/a.xml"

        amap = root / "map.jsonl"
        manifest = root / "articles.jsonl"
        amap.write_text(
            "\n".join(
                [
                    json.dumps({"line": 1, "contextDocument": "en/a.xml", "meaningDocument": "es/a.xml"}),
                    json.dumps({"line": 2, "contextDocument": "en/b.xml", "meaningDocument": "es/b.xml"}),
                ]
            )
            + "\n",
            encoding="utf-8",
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

    print("Global Voices Part 9 attribution contracts: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
