#!/usr/bin/env python3
from __future__ import annotations
import gzip, json, tempfile
from pathlib import Path

from ingest.model import Candidate, Origin, write_jsonl
from selection_experiment import build_report, parser
from selection_policy import near_duplicate, select_target_ids


def main()->int:
    assert near_duplicate("I like this red apple today.","I really like this red apple today.")
    assert not near_duplicate("I like this red apple today.","Trains arrive at the northern station tomorrow.")
    targets=[
        {"targetId":"t1","freqRank":100,"evidenceContexts":5,"sourceFamilies":["a"],"lemmaKey":"lemma:x"},
        {"targetId":"t2","freqRank":101,"evidenceContexts":5,"sourceFamilies":["a"],"lemmaKey":"lemma:x"},
        {"targetId":"t3","freqRank":102,"evidenceContexts":1,"sourceFamilies":["a"],"lemmaKey":"lemma:y"},
    ]
    chosen,stats=select_target_ids(targets,2)
    assert [x["targetId"] for x in chosen]==["t1","t3"]
    assert stats["morphologyDeferred"]>=1

    with tempfile.TemporaryDirectory(prefix="ikna-selection-") as td:
        root=Path(td); inp=root/"in.jsonl.gz"
        records=[]
        contexts=[
            "We observe zebra yak xylophone nearby every morning.",
            "They discuss quartz velvet lantern calmly every evening.",
            "People carry amber violin baskets through the market daily.",
        ]
        for i,text in enumerate(contexts):
            records.append(Candidate(collection="everyday",lang="en",meaning_lang="es",context=text,
                meaning="Una traduccion humana suficientemente larga para la prueba.",
                origins=[Origin("tatoeba","fixture",f"t:{i}",f"s:{i}")]))
        write_jsonl(str(inp),records)
        args=parser().parse_args(["--candidates",str(inp),"--json",str(root/"r.json"),"--markdown",str(root/"r.md"),"--preview",str(root/"p.jsonl.gz"),"--staging",str(root/"s.db"),"--learn","en","--meanings","es","--function-top","0","--max-deck","2","--min-deck","1","--thin-deck","2"])
        report,preview=build_report(args)
        assert report["publicationSafe"] is False
        assert report["summary"]["budgetRejected"]>0
        assert preview
        assert all(row["deckId"].startswith("en-es-everyday-") for row in preview)

        limited_args=parser().parse_args(["--candidates",str(inp),"--json",str(root/"r2.json"),"--markdown",str(root/"r2.md"),"--preview",str(root/"p2.jsonl.gz"),"--staging",str(root/"s2.db"),"--learn","en","--meanings","es","--function-top","0","--max-deck","2","--min-deck","1","--thin-deck","2","--preview-limit-per-deck","1"])
        limited_report,limited_preview=build_report(limited_args)
        assert limited_report["summary"] == report["summary"]
        assert [(x["deckId"],x["selectedTargets"],x["decision"]) for x in limited_report["decks"]] == [(x["deckId"],x["selectedTargets"],x["decision"]) for x in report["decks"]]
        assert len(limited_preview) <= len(preview)
    print("Catalogue Part 10 selection contracts: OK"); return 0

if __name__=="__main__": raise SystemExit(main())
