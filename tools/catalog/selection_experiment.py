#!/usr/bin/env python3
"""Part 10: deterministic, non-publishing Catalogue v2 selection experiment.

Unlike the historical builder pass, source order does not decide which targets
survive.  The tool first measures all eligible exact targets, then ranks targets
and contexts using an explicit inspectable policy.  It accepts mixed provenance
because cross-source evidence is useful for selection, but never publishes the
preview as release assets.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import catalogue_core as core
from build_catalogue_v2 import phrase_choices
from catalogue_v2 import target_id
from ingest.model import Candidate
from morphology.store import MorphologyResolver
from segmentation import IcuUnavailable, prepare, utf16_length
from selection_policy import choose_contexts, select_target_ids


def _open(path: str):
    return gzip.open(path, "rt", encoding="utf-8", errors="replace") if path.endswith(".gz") else open(path, encoding="utf-8", errors="replace")


def _context_key(collection: str, lang: str, context: str) -> str:
    text = " ".join(context.split()).casefold()
    return hashlib.sha256((collection+"\0"+lang+"\0"+text).encode("utf-8")).hexdigest()


SCHEMA = """
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
PRAGMA temp_store=FILE;
CREATE TABLE candidate(
 id TEXT PRIMARY KEY, collection TEXT, lang TEXT, meaning_lang TEXT,
 context TEXT, meaning TEXT, origins_json TEXT, max_alignment REAL
);
CREATE INDEX candidate_pair ON candidate(collection,lang,meaning_lang);
CREATE TABLE context(
 collection TEXT, lang TEXT, context_key TEXT, context TEXT,
 PRIMARY KEY(collection,lang,context_key)
);
CREATE INDEX context_group ON context(collection,lang);
"""


def stage(paths: Iterable[str], db_path: str) -> dict[str, int]:
    path = Path(db_path)
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    stats = Counter()
    try:
        db.executescript(SCHEMA)
        for source in paths:
            with _open(source) as handle:
                for number, line in enumerate(handle, start=1):
                    line=line.strip()
                    if not line: continue
                    try:
                        record=Candidate.from_dict(json.loads(line))
                    except Exception as exc:
                        raise ValueError(f"{source}:{number}: {exc}") from exc
                    stats["inputCandidates"] += 1
                    origins=[origin.to_dict() for origin in record.origins]
                    scores=[origin.alignment_score for origin in record.origins if origin.alignment_score is not None]
                    cursor=db.execute(
                        "INSERT OR IGNORE INTO candidate VALUES(?,?,?,?,?,?,?,?)",
                        (record.id,record.collection,record.lang,record.meaning_lang,record.context,record.meaning,
                         json.dumps(origins,ensure_ascii=False,sort_keys=True), max(scores) if scores else None)
                    )
                    if cursor.rowcount:
                        stats["uniqueCandidates"] += 1
                        db.execute("INSERT OR IGNORE INTO context VALUES(?,?,?,?)",
                                   (record.collection,record.lang,_context_key(record.collection,record.lang,record.context),record.context))
                    else:
                        stats["duplicateCandidates"] += 1
                    if stats["inputCandidates"] % 20000 == 0: db.commit()
        db.commit()
        return dict(stats)
    finally:
        db.close()


def build_ranks(db: sqlite3.Connection, collection: str, lang: str) -> dict[str,int]:
    counts=Counter()
    for (text,) in db.execute("SELECT context FROM context WHERE collection=? AND lang=? ORDER BY context_key",(collection,lang)):
        counts.update(word.lower() for word in core.words(text,lang))
    return {form:i for i,(form,_n) in enumerate(counts.most_common(),start=1)}


def _origin_families(origins_json: str) -> tuple[list[dict[str,Any]], list[str]]:
    origins=json.loads(origins_json)
    return origins, sorted({str(origin["sourceFamily"]) for origin in origins})


def _target_lemma(morphology: MorphologyResolver|None, lang: str, surface: str, tid: str) -> tuple[str,str]:
    if morphology is None:
        return tid, "unavailable"
    evidence=morphology.resolve_surface(lang,surface)
    lemma=str(evidence.get("lemma") or "").strip()
    source=str(evidence.get("lemmaSource") or "identity")
    if source == "identity" or not lemma:
        return tid, source
    return f"lemma:{lang}:{lemma.casefold()}", source


def pair_evidence(db: sqlite3.Connection, collection: str, lang: str, meaning_lang: str,
                  ranks: dict[str,int], function_top: int, morphology: MorphologyResolver|None,
                  evidence_context_limit: int) -> tuple[list[dict[str,Any]], Counter]:
    targets: dict[str,dict[str,Any]]={}
    stats=Counter()
    rows=db.execute("SELECT id,context,meaning,origins_json,max_alignment FROM candidate WHERE collection=? AND lang=? AND meaning_lang=? ORDER BY id",
                    (collection,lang,meaning_lang))
    for candidate_id,context,meaning,origins_json,max_alignment in rows:
        stats["candidateRows"] += 1
        if len(context) < (6 if lang in core.CJK else core.MIN_SENTENCE) or utf16_length(context) > core.MAX_SENTENCE:
            stats["sentenceLengthRejected"] += 1; continue
        if not meaning or utf16_length(meaning) > core.MAX_TRANSLATION:
            stats["translationLengthRejected"] += 1; continue
        choices=phrase_choices(context,ranks,lang,function_top)
        if not choices:
            stats["noUsableTarget"] += 1; continue
        origins,families=_origin_families(origins_json)
        token_count=len(core.words(context,lang))
        for rank,surface,tid,level in choices:
            item=targets.get(tid)
            if item is None:
                lemma_key,lemma_source=_target_lemma(morphology,lang,surface,tid)
                item={
                    "targetId":tid,"text":surface,"freqRank":rank,"level":level,
                    "lemmaKey":lemma_key,"lemmaSource":lemma_source,
                    "evidenceContexts":0,"sourceFamilies":set(),"contexts":[]
                }
                targets[tid]=item
            item["evidenceContexts"] += 1
            item["sourceFamilies"].update(families)
            evidence={
                "candidateId":candidate_id,"contextId":origins[0]["contextRef"],"meaningId":origins[0]["meaningRef"],
                "context":context,"meaning":meaning,"sourceFamilies":families,"origins":origins,
                "alignmentScore":max_alignment,"tokenCount":token_count,
            }
            # Preserve bounded best-evidence memory, deterministic by the later
            # context policy key; a target can occur in thousands of sentences.
            item["contexts"].append(evidence)
            if len(item["contexts"]) > evidence_context_limit * 3:
                chosen,_=choose_contexts(item["contexts"],evidence_context_limit,0.85)
                item["contexts"]=chosen
            stats["eligibleTargetOccurrences"] += 1
    out=[]
    for item in targets.values():
        item["sourceFamilies"]=sorted(item["sourceFamilies"])
        out.append(item)
    stats["eligibleExactTargets"]=len(out)
    return out,stats


def build_report(args: argparse.Namespace) -> tuple[dict[str,Any],list[dict[str,Any]]]:
    langs=sorted({x.strip().lower() for x in args.learn.split(",") if x.strip()})
    meanings=sorted({x.strip().lower() for x in args.meanings.split(",") if x.strip()})
    try: prepare(langs)
    except IcuUnavailable as exc: raise ValueError(str(exc)) from exc
    stage_stats=stage(args.candidates,args.staging)
    db=sqlite3.connect(args.staging)
    morphology=MorphologyResolver(args.morphology_db) if args.morphology_db else None
    decks=[]; preview=[]; summary=Counter(); rank_cache={}
    try:
        pairs=db.execute("SELECT DISTINCT collection,lang,meaning_lang FROM candidate ORDER BY collection,lang,meaning_lang").fetchall()
        for collection,lang,meaning_lang in pairs:
            if lang not in langs or meaning_lang not in meanings or lang==meaning_lang: continue
            key=(collection,lang)
            if key not in rank_cache: rank_cache[key]=build_ranks(db,collection,lang)
            ranks=rank_cache[key]
            targets,pair_stats=pair_evidence(db,collection,lang,meaning_lang,ranks,args.function_top,morphology,args.evidence_context_limit)
            levels={level:[] for level in core.LEVELS}
            for target in targets: levels[target["level"]].append(target)
            for level in core.LEVELS:
                # Build a complete deterministic target order first. The safety
                # budget is applied only after assigning distinct natural
                # contexts, so a target with no unused context cannot waste a
                # slot merely because it appeared early in a source file.
                ordered_targets,selection_stats=select_target_ids(levels[level], max(1, len(levels[level])))
                selected_rows=[]; near_rejected=0; context_collision_rejected=0; budget_rejected=0
                used_context_ids: set[str] = set()
                for index,target in enumerate(ordered_targets):
                    if len(selected_rows) >= args.max_deck:
                        budget_rejected = len(ordered_targets) - index
                        break
                    unused = [ctx for ctx in target["contexts"] if str(ctx.get("contextId") or ctx.get("candidateId")) not in used_context_ids]
                    if not unused:
                        context_collision_rejected += 1
                        continue
                    contexts,rejected=choose_contexts(unused,args.contexts_per_target,args.near_duplicate_threshold)
                    near_rejected += rejected
                    if not contexts:
                        context_collision_rejected += 1
                        continue
                    selected_rows.append({
                        "targetId":target["targetId"],"text":target["text"],"freqRank":target["freqRank"],
                        "lemmaKey":target["lemmaKey"],"lemmaSource":target["lemmaSource"],
                        "evidenceContexts":target["evidenceContexts"],"sourceFamilies":target["sourceFamilies"],
                        "contexts":contexts,
                    })
                    for context in contexts:
                        used_context_ids.add(str(context.get("contextId") or context.get("candidateId")))
                count=len(selected_rows)
                deck_id=f"{lang}-{meaning_lang}-{collection}-{level}"
                if count == 0: decision="omit-no-quality-supply"
                elif count < args.min_deck: decision="omit-below-minimum"
                elif count < args.thin_deck: decision="publish-thin"
                else: decision="publish"
                if decision.startswith("publish"):
                    for row in selected_rows:
                        preview.append({"deckId":deck_id,"collection":collection,"lang":lang,"meaningLang":meaning_lang,"level":level,**row})
                deck={
                    "deckId":deck_id,"collection":collection,"lang":lang,"meaningLang":meaning_lang,"level":level,
                    "eligibleTargets":len(levels[level]),"selectedTargets":count,"decision":decision,
                    "budget":args.max_deck,"budgetRejected":budget_rejected,
                    "morphologyDeferred":selection_stats["morphologyDeferred"],"nearDuplicateContextsRejected":near_rejected,
                    "contextCollisionTargetsRejected":context_collision_rejected,
                    "pairStats":dict(pair_stats),
                }
                decks.append(deck)
                summary[decision]+=1; summary["eligibleTargets"]+=len(levels[level]); summary["selectedTargets"]+=count
                summary["budgetRejected"]+=budget_rejected; summary["nearDuplicateContextsRejected"]+=near_rejected
                summary["contextCollisionTargetsRejected"]+=context_collision_rejected
        report={
            "reportVersion":1,"part":10,"status":"preview","publicationSafe":False,
            "policy":{
                "maxTargetsPerLevel":args.max_deck,"minDeck":args.min_deck,"thinDeck":args.thin_deck,
                "contextsPerTarget":args.contexts_per_target,"nearDuplicateJaccard":args.near_duplicate_threshold,
                "targetOrder":["frequency rank","context evidence","source diversity","stable target id"],
                "morphology":"confident lemma is diversity evidence only; exact targetId never changes",
                "smallDeck":"omit below minDeck; never pad with weak material",
            },
            "morphologyUsed":bool(args.morphology_db),"stage":stage_stats,"summary":dict(summary),"decks":decks,
        }
        return report,preview
    finally:
        db.close()
        if morphology: morphology.close()


def write_preview(path: str, rows: list[dict[str,Any]]) -> None:
    opener=gzip.open if path.endswith(".gz") else open
    kwargs={"encoding":"utf-8","newline":""}
    if path.endswith(".gz"): kwargs["compresslevel"]=6
    with opener(path,"wt",**kwargs) as handle:
        for row in rows: handle.write(json.dumps(row,ensure_ascii=False,sort_keys=True)+"\n")


def markdown(report: dict[str,Any]) -> str:
    s=report["summary"]
    lines=[
        "# Catalogue v2 selection experiment","",
        "This Part 10 output is a preview, not release assets.","",
        f"Eligible targets across deck levels: **{s.get('eligibleTargets',0):,}**",
        f"Selected by policy: **{s.get('selectedTargets',0):,}**",
        f"Rejected only by safety budget: **{s.get('budgetRejected',0):,}**",
        f"Near-duplicate alternate contexts suppressed: **{s.get('nearDuplicateContextsRejected',0):,}**",
        f"Targets skipped because every stored context was already used in that deck: **{s.get('contextCollisionTargetsRejected',0):,}**","",
        "| deck | eligible | selected | decision | budget rejected |",
        "| --- | ---: | ---: | --- | ---: |",
    ]
    for row in report["decks"]:
        lines.append(f"| `{row['deckId']}` | {row['eligibleTargets']:,} | {row['selectedTargets']:,} | {row['decision']} | {row['budgetRejected']:,} |")
    lines += ["","Small decks are never padded. `publish-thin` means the available material passed the same policy but remains genuinely scarce.",""]
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--candidates",nargs="+",required=True)
    ap.add_argument("--json",required=True); ap.add_argument("--markdown",required=True); ap.add_argument("--preview",required=True)
    ap.add_argument("--staging",required=True)
    ap.add_argument("--learn",default=",".join(core.LEARNABLE)); ap.add_argument("--meanings",default=",".join(core.MEANINGS))
    ap.add_argument("--max-deck",type=int,default=8000); ap.add_argument("--min-deck",type=int,default=40); ap.add_argument("--thin-deck",type=int,default=1000)
    ap.add_argument("--contexts-per-target",type=int,default=3); ap.add_argument("--evidence-context-limit",type=int,default=12)
    ap.add_argument("--near-duplicate-threshold",type=float,default=.85); ap.add_argument("--function-top",type=int,default=core.FUNCTION_TOP)
    ap.add_argument("--morphology-db")
    return ap


def main(argv=None)->int:
    args=parser().parse_args(argv)
    if min(args.max_deck,args.min_deck,args.thin_deck,args.contexts_per_target,args.evidence_context_limit)<1: raise SystemExit("selection limits must be positive")
    if args.min_deck>args.max_deck: raise SystemExit("--min-deck cannot exceed --max-deck")
    if not 0 < args.near_duplicate_threshold <= 1: raise SystemExit("near duplicate threshold must be in (0,1]")
    report,preview=build_report(args)
    Path(args.json).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    Path(args.markdown).write_text(markdown(report),encoding="utf-8")
    write_preview(args.preview,preview)
    print(json.dumps(report["summary"],ensure_ascii=False,indent=2)); return 0

if __name__=="__main__": raise SystemExit(main())
