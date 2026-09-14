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
PRAGMA locking_mode=EXCLUSIVE;
PRAGMA cache_size=-262144;
PRAGMA mmap_size=268435456;
CREATE TABLE candidate(
 id TEXT PRIMARY KEY, collection TEXT, lang TEXT, meaning_lang TEXT,
 context_key TEXT, context TEXT, meaning TEXT, origins_json TEXT, max_alignment REAL
);
CREATE TABLE context(
 collection TEXT, lang TEXT, context_key TEXT, context TEXT,
 PRIMARY KEY(collection,lang,context_key)
);
CREATE TABLE context_analysis(
 collection TEXT, lang TEXT, context_key TEXT, token_count INTEGER, choices_json TEXT,
 PRIMARY KEY(collection,lang,context_key)
);
"""

POST_STAGE_INDEXES = """
CREATE INDEX candidate_pair ON candidate(collection,lang,meaning_lang,id);
"""


def _flush_stage(db: sqlite3.Connection, candidates: list[tuple], contexts: list[tuple]) -> None:
    if candidates:
        db.executemany(
            "INSERT OR IGNORE INTO candidate VALUES(?,?,?,?,?,?,?,?,?)", candidates
        )
    if contexts:
        db.executemany(
            "INSERT OR IGNORE INTO context VALUES(?,?,?,?)", contexts
        )
    candidates.clear()
    contexts.clear()


def stage(paths: Iterable[str], db_path: str, batch_size: int = 50000) -> dict[str, int]:
    path = Path(db_path)
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    stats = Counter()
    candidate_batch: list[tuple] = []
    context_batch: list[tuple] = []
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
                    ckey=_context_key(record.collection,record.lang,record.context)
                    candidate_batch.append((
                        record.id,record.collection,record.lang,record.meaning_lang,ckey,
                        record.context,record.meaning,
                        json.dumps(origins,ensure_ascii=False,separators=(",", ":")),
                        max(scores) if scores else None,
                    ))
                    context_batch.append((record.collection,record.lang,ckey,record.context))
                    if len(candidate_batch) >= batch_size:
                        _flush_stage(db,candidate_batch,context_batch)
        _flush_stage(db,candidate_batch,context_batch)
        db.commit()
        # Building the pair index after the bulk load is materially faster than
        # maintaining it for every one of millions of inserts. The primary-key
        # indexes still preserve the exact same deduplication semantics.
        db.executescript(POST_STAGE_INDEXES)
        db.commit()
        stats["uniqueCandidates"] = db.execute("SELECT COUNT(*) FROM candidate").fetchone()[0]
        stats["duplicateCandidates"] = stats["inputCandidates"] - stats["uniqueCandidates"]
        stats["uniqueContexts"] = db.execute("SELECT COUNT(*) FROM context").fetchone()[0]
        return dict(stats)
    finally:
        db.close()


def build_ranks(db: sqlite3.Connection, collection: str, lang: str) -> dict[str,int]:
    counts=Counter()
    for (text,) in db.execute("SELECT context FROM context WHERE collection=? AND lang=? ORDER BY context_key",(collection,lang)):
        counts.update(word.lower() for word in core.words(text,lang))
    return {form:i for i,(form,_n) in enumerate(counts.most_common(),start=1)}


def build_context_analysis(
    db: sqlite3.Connection, collection: str, lang: str, ranks: dict[str,int], function_top: int,
    batch_size: int = 5000,
) -> None:
    # A context is shared by many meaning languages (especially MASSIVE).  The
    # historical experiment tokenised and ranked that same sentence once per
    # directed pair.  Precompute target choices once per exact context instead;
    # this changes no selection semantics, only the amount of repeated work.
    existing=db.execute(
        "SELECT COUNT(*) FROM context_analysis WHERE collection=? AND lang=?",
        (collection,lang),
    ).fetchone()[0]
    expected=db.execute(
        "SELECT COUNT(*) FROM context WHERE collection=? AND lang=?",
        (collection,lang),
    ).fetchone()[0]
    if existing == expected and expected:
        return
    db.execute("DELETE FROM context_analysis WHERE collection=? AND lang=?",(collection,lang))
    batch=[]
    for context_key,text in db.execute(
        "SELECT context_key,context FROM context WHERE collection=? AND lang=? ORDER BY context_key",
        (collection,lang),
    ):
        token_count=len(core.words(text,lang))
        choices=phrase_choices(text,ranks,lang,function_top)
        batch.append((
            collection,lang,context_key,token_count,
            json.dumps(choices,ensure_ascii=False,separators=(",", ":")),
        ))
        if len(batch) >= batch_size:
            db.executemany("INSERT INTO context_analysis VALUES(?,?,?,?,?)",batch)
            batch.clear()
    if batch:
        db.executemany("INSERT INTO context_analysis VALUES(?,?,?,?,?)",batch)
    db.commit()


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
                  morphology: MorphologyResolver|None, evidence_context_limit: int) -> tuple[list[dict[str,Any]], Counter]:
    targets: dict[str,dict[str,Any]]={}
    stats=Counter()
    rows=db.execute(
        "SELECT c.id,c.context,c.meaning,c.origins_json,c.max_alignment,a.token_count,a.choices_json "
        "FROM candidate c JOIN context_analysis a "
        "ON a.collection=c.collection AND a.lang=c.lang AND a.context_key=c.context_key "
        "WHERE c.collection=? AND c.lang=? AND c.meaning_lang=? ORDER BY c.id",
        (collection,lang,meaning_lang),
    )
    for candidate_id,context,meaning,origins_json,max_alignment,token_count,choices_json in rows:
        stats["candidateRows"] += 1
        if len(context) < (6 if lang in core.CJK else core.MIN_SENTENCE) or utf16_length(context) > core.MAX_SENTENCE:
            stats["sentenceLengthRejected"] += 1; continue
        if not meaning or utf16_length(meaning) > core.MAX_TRANSLATION:
            stats["translationLengthRejected"] += 1; continue
        choices=json.loads(choices_json)
        if not choices:
            stats["noUsableTarget"] += 1; continue
        origins,families=_origin_families(origins_json)
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
            if key not in rank_cache:
                rank_cache[key]=build_ranks(db,collection,lang)
                build_context_analysis(db,collection,lang,rank_cache[key],args.function_top)
            targets,pair_stats=pair_evidence(
                db,collection,lang,meaning_lang,morphology,args.evidence_context_limit
            )
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
                    evidence_rows = selected_rows if args.preview_limit_per_deck == 0 else selected_rows[:args.preview_limit_per_deck]
                    for row in evidence_rows:
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
                "previewLimitPerDeck":args.preview_limit_per_deck,
            },
            "morphologyUsed":bool(args.morphology_db),"stage":stage_stats,"summary":dict(summary),
            "previewRows":len(preview),"decks":decks,
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
    ap.add_argument("--preview-limit-per-deck",type=int,default=0,help="evidence rows kept per publishable deck; 0 keeps all")
    ap.add_argument("--morphology-db")
    return ap


def main(argv=None)->int:
    args=parser().parse_args(argv)
    if min(args.max_deck,args.min_deck,args.thin_deck,args.contexts_per_target,args.evidence_context_limit)<1: raise SystemExit("selection limits must be positive")
    if args.min_deck>args.max_deck: raise SystemExit("--min-deck cannot exceed --max-deck")
    if args.preview_limit_per_deck < 0: raise SystemExit("--preview-limit-per-deck must be non-negative")
    if not 0 < args.near_duplicate_threshold <= 1: raise SystemExit("near duplicate threshold must be in (0,1]")
    report,preview=build_report(args)
    Path(args.json).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    Path(args.markdown).write_text(markdown(report),encoding="utf-8")
    write_preview(args.preview,preview)
    print(json.dumps(report["summary"],ensure_ascii=False,indent=2)); return 0

if __name__=="__main__": raise SystemExit(main())
