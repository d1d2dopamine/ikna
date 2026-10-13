#!/usr/bin/env python3
"""Nonpublishing audit of the pinned 188-row Tatoeba pl/ko slice; emits no text."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import platform

import catalogue_core as core
from catalogue_v2 import canonical_target, target_id
from build_catalogue_v2 import phrase_choices
from ingest.model import Candidate
import selection_experiment as selection
from selection_policy import filter_unique_choices, quarantine_reason
from segmentation import prepare, word_spans, utf16_length
import replenishment_review

BASELINE_SHA = '7a861d648025196bbf507495d937664b675daff419722e58dcabd451c66c75b2'
RANKS_SHA = 'cdb3ac19359d7d111d6d931f59efcbe3b091a26f68d3cc1aec5b2bfdc7c9afb2'
VERSION = '2026-10-03-2d4eee105639ba24'
EXPECTED_SHA = '4fdc7a080701ef6a5c0e521a52c9dd48262e1b685cd0f3d22bdb1fb7842a8ada'

def sha(data): return hashlib.sha256(data).hexdigest()
def read_pinned(path, expected, limit):
    if Path(path).stat().st_size > 2*1024**2: raise ValueError('compressed-size-limit')
    data = Path(path).read_bytes()
    if sha(data) != expected: raise ValueError('input-hash-mismatch')
    with gzip.open(path,'rb') as f: raw=f.read(limit+1)
    if len(raw)>limit: raise ValueError('logical-size-limit')
    return raw

def token_trace(text, ranks, lang):
    surfaces = core.words(text,lang)
    lower = Counter(x.lower() for x in surfaces)
    canonical = Counter(canonical_target(x) for x in surfaces)
    choices = phrase_choices(text,ranks,lang,core.FUNCTION_TOP)
    kept, rejected, _ = filter_unique_choices(text,choices,lang)
    reasons = Counter(); trace=[]
    for surface in surfaces:
        rank=ranks.get(surface.lower()); flags=[]
        if rank is None: flags.append('rank-absent')
        elif rank<=core.FUNCTION_TOP: flags.append('function-top60')
        if len(surface)<core.minimum_phrase(lang): flags.append('target-short')
        if utf16_length(surface)>core.MAX_PHRASE: flags.append('target-long-utf16')
        if lower[surface.lower()]!=1: flags.append('lower-occurrence-ambiguous')
        if canonical[canonical_target(surface)]!=1: flags.append('canonical-occurrence-ambiguous')
        primary=flags[0] if flags else 'eligible'
        reasons[primary]+=1
        trace.append({'targetId':target_id(lang,surface),'surfaceSha256':sha(surface.encode()),'rank':rank,
                      'level':core.level_of(rank) if rank is not None else None,'scalarLength':len(surface),
                      'utf16Length':utf16_length(surface),'firstTokenReason':primary,'overlappingFlags':flags})
    return kept,trace,dict(reasons),dict(rejected)

def row_gate(context, meaning, lang, choices, origins):
    if len(context)<(6 if lang in core.CJK else core.MIN_SENTENCE):return 'source-short'
    if utf16_length(context)>core.MAX_SENTENCE:return 'source-long-utf16'
    if not meaning:return 'meaning-empty'
    if utf16_length(meaning)>core.MAX_TRANSLATION:return 'meaning-long-utf16'
    if not choices:return 'no-usable-target'
    if quarantine_reason(context,meaning,origins):return 'source-quarantine'
    return 'eligible-before-allocation'

def analysis_signature(text, choices, lang):
    return (tuple(canonical_target(s.surface) for s in word_spans(text,lang)),
            tuple((rank,tid,level) for rank,_,tid,level in choices))

def reused_analysis_problem(text, borrowed, borrowed_count, lang):
    tokens=word_spans(text,lang); canon=Counter(canonical_target(x.surface) for x in tokens)
    return {'tokenCountMismatch':len(tokens)!=borrowed_count,
            'borrowedChoicesWithoutUniqueCanonicalToken':sum(canon[canonical_target(x[1])]!=1 for x in borrowed)}

def run(baseline,ranks_path,expected_path,scratch,out):
    if Path(out).exists() or Path(scratch).exists():raise ValueError('destinations-must-be-new')
    protected=[Path(baseline).resolve(),Path(ranks_path).resolve(),Path(expected_path).resolve()]
    a,b=Path(out).resolve(),Path(scratch).resolve()
    if a==b or a in b.parents or b in a.parents or any(x in [a,b] or a in x.parents or b in x.parents for x in protected):raise ValueError('destinations-overlap-inputs-or-each-other')
    raw=read_pinned(baseline,BASELINE_SHA,2*1024**2); rows=[json.loads(x) for x in raw.splitlines()]
    passport=json.loads(read_pinned(ranks_path,RANKS_SHA,2*1024**2))
    if len(rows)!=188 or len({r['id'] for r in rows})!=188 or set(passport['ranks'])!={'pl','ko'} or passport['sourceVersion']!=VERSION:raise ValueError('unexpected-saved-scope')
    env=prepare(['pl','ko'])
    if env['icuVersion']!='74.2':raise ValueError('historical-icu-required')
    if sha(Path(expected_path).read_bytes()) != EXPECTED_SHA:raise ValueError('expected-report-hash-mismatch')
    expected=json.loads(Path(expected_path).read_text())['measurements']['baseline']
    scratch,out=Path(scratch),Path(out);scratch.mkdir(parents=True);out.mkdir(parents=True)
    replay=replenishment_review.measure([Candidate.from_dict(r) for r in rows],passport['ranks'],scratch,'control')
    for pair,v in replay.items():
        if v['sieve']!=expected[pair]['sieve']:raise ValueError('baseline-sieve-drift')
        for level,x in v['levels'].items():
            for key in ['eligibleTargets','selectedTargets','contextCollisionTargetsRejected','retainedContexts','included']:
                if x[key]!=expected[pair]['levels'][level][key]:raise ValueError('baseline-level-drift')
    db=sqlite3.connect(scratch/'control.db'); details=[]; totals={}; variant_groups=0
    for lang in ['pl','ko']:
        rr=[r for r in rows if r['lang']==lang]; row_counts=Counter();token_counts=Counter();eligible_tokens=Counter();shadows=0
        for r in rr:
            choices,trace,counts,canonical_reject=token_trace(r['context'],passport['ranks'][lang],lang)
            gate=row_gate(r['context'],r['meaning'],lang,choices,r['origins']);row_counts[gate]+=1;token_counts.update(counts)
            if gate=='eligible-before-allocation':eligible_tokens.update(counts)
            key=selection._context_key('everyday',lang,r['context'])
            rep=db.execute('SELECT context FROM context WHERE collection=? AND lang=? AND context_key=?',('everyday',lang,key)).fetchone()[0]
            count,stored=db.execute('SELECT token_count,choices_json FROM context_analysis WHERE collection=? AND lang=? AND analysis_key=?',('everyday',lang,selection._analysis_key('everyday',lang,r['context']))).fetchone()
            stored=json.loads(stored);problem=reused_analysis_problem(r['context'],stored,count,lang)
            if rep!=r['context']:shadows+=1
            details.append({'candidateId':r['id'],'lang':lang,'meaningLang':r['meaningLang'],'sourceRefs':[{k:x[k] for k in ['contextRef','meaningRef','sourceFamily','sourceVersion']} for x in r['origins']],
                'contextSha256':sha(r['context'].encode()),'meaningSha256':sha(r['meaning'].encode()),'contextScalarLength':len(r['context']),'contextUtf16Length':utf16_length(r['context']),
                'meaningUtf16Length':utf16_length(r['meaning']),'rowGate':gate,'eligibleChoiceCountBeforeRowGate':len(choices),'tokenTrace':trace,
                'canonicalRejectCounts':canonical_reject,'rawVariantReusesRepresentative':rep!=r['context'],'cachedAnalysisInvariant':problem,
                'directChoicesEqualCached':choices==[tuple(x) for x in stored]})
        totals[lang]={'candidateRows':len(rr),'uniqueExactSourceTexts':len({r['context'] for r in rr}),'rowFirstReasons':dict(row_counts),
                      'tokenFirstReasonsAcrossAllCandidateRows':dict(token_counts),'tokenFirstReasonsOnRowEligibleCandidates':dict(eligible_tokens),'rawVariantReuseRows':shadows}
    db.close()
    for lang in ['pl','ko']:
        dr=[r for r in details if r['lang']==lang]
        accepted={t['targetId'] for r in dr if r['rowGate']=='eligible-before-allocation' for t in r['tokenTrace'] if t['firstTokenReason']=='eligible'}
        short={t['targetId']:t['level'] for r in dr if r['rowGate']=='source-short' for t in r['tokenTrace'] if t['firstTokenReason']=='eligible'}
        counts=Counter(canonical_target(k) for k in passport['ranks'][lang])
        totals[lang]['canonicalRankKeyCollisionGroups']=sum(n>1 for n in counts.values())
        totals[lang]['shortRowsWithOtherwiseEligibleChoices']=sum(r['rowGate']=='source-short' and r['eligibleChoiceCountBeforeRowGate']>0 for r in dr)
        totals[lang]['shortOnlyExactTargetsBeforeAnyAllocation']=dict(Counter(level for tid,level in short.items() if tid not in accepted))
        totals[lang]['shortOnlyTargetsAreNotAdmittedCards']=True
    report={'scope':'complete-saved-188-directed-row-slice-not-full-pool','environment':env,'python':platform.python_version(),
      'inputSha256':{'baseline':BASELINE_SHA,'ranks':RANKS_SHA,'expectedReport':sha(Path(expected_path).read_bytes())},'sourceVersion':VERSION,
      'baselineCountsReproduced':True,'exactHistoricalAssignmentsCompared':False,'summary':totals,'replay':replay,
      'limits':{'historicalCap':8000,'currentDefaultCap':12000,'functionTop':core.FUNCTION_TOP,'sourceMinimumNonCjk':core.MIN_SENTENCE,'sourceMinimumCjk':6,'sourceMaximumUtf16':core.MAX_SENTENCE,'meaningMaximumUtf16':core.MAX_TRANSLATION},
      'unsafeCachedRows':sum(bool(x['cachedAnalysisInvariant']['tokenCountMismatch'] or x['cachedAnalysisInvariant']['borrowedChoicesWithoutUniqueCanonicalToken']) for x in details),
      'productionPolicyChanged':False,'admittedCardDelta':0,'semanticAccuracyCertified':False,
      'countingNotes':['Row reasons are mutually exclusive in production order. Token flags overlap; first-token reasons do not overlap.','Token statistics count occurrences across directed candidate rows, not global unique learner targets.','EligibleChoiceCountBeforeRowGate is diagnostic only, including choices in rejected short rows; it is not admitted material.'],
      'sourceFiles':{n:sha((Path(__file__).parent/n).read_bytes()) for n in ['selection_filter_audit.py','selection_experiment.py','selection_policy.py','selection_analysis_cache.py','build_catalogue_v2.py','segmentation.py','catalogue_core.py']}}
    for name,obj in [('SUMMARY.json',report),('ROWS.json',details)]: (out/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
    (out/'MANIFEST.json').write_text(json.dumps({p.name:sha(p.read_bytes()) for p in out.glob('*.json')},indent=2)+'\n')
    return report

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['baseline','ranks','expected','scratch','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();r=run(a.baseline,a.ranks,a.expected,a.scratch,a.out);print(json.dumps({'baselineCountsReproduced':r['baselineCountsReproduced'],'summary':r['summary'],'unsafeCachedRows':r['unsafeCachedRows']},indent=2))
if __name__=='__main__':main()
