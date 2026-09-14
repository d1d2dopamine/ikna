#!/usr/bin/env python3
from __future__ import annotations
import json, tempfile
from pathlib import Path

from ingest.model import Candidate, Origin, read_jsonl, write_jsonl
from knowledge_rebuild import build_report, parser, pair_key


def candidate(score: float, text: str, lang: str="en", meaning: str="es") -> Candidate:
    return Candidate(
        collection="knowledge", lang=lang, meaning_lang=meaning,
        context=text, meaning="Una traduccion humana suficientemente larga.",
        origins=[Origin("wikimatrix", "v1", f"wm:{lang}:{score}:{text}", f"wm:{meaning}:{score}:{text}", alignment_score=score)],
    )


def main() -> int:
    assert pair_key("es", "en") == "en-es"
    with tempfile.TemporaryDirectory(prefix="ikna-knowledge-") as td:
        root = Path(td)
        inp = root / "in.jsonl.gz"
        write_jsonl(str(inp), [candidate(1.30, "Water freezes at zero degrees Celsius."), candidate(1.01, "A deliberately weaker aligned sentence remains measurable.")])
        policy = root / "policy.json"
        policy.write_text(json.dumps({
            "policyVersion":1,
            "default":{"action":"review","minScore":1.10},
            "pairs":{}
        }), encoding="utf-8")
        args = parser().parse_args(["--candidates",str(inp),"--policy",str(policy),"--pool",str(root/"pool.jsonl.gz"),"--json",str(root/"r.json"),"--markdown",str(root/"r.md"),"--samples",str(root/"s.md"),"--sample-per-pair","1"])
        report = build_report(args)
        assert report["status"] == "requires-manual-review"
        assert report["summary"]["retainedCandidates"] == 1
        assert len(read_jsonl(str(root/"pool.jsonl.gz"))) == 1

        policy.write_text(json.dumps({
            "policyVersion":1,
            "default":{"action":"review","minScore":1.04},
            "pairs":{"en-es":{"action":"reject","minScore":None,"note":"fixture rejection"}}
        }), encoding="utf-8")
        report = build_report(args)
        assert report["summary"]["retainedCandidates"] == 0
        assert report["rejectedPairs"] == ["en-es"]
    print("Knowledge Part 8 contracts: OK")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
