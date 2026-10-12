#!/usr/bin/env python3
"""Independent post-success comparison. Never opens SQLite or runs selection.

Only the exact reviewed policy fingerprint may differ in complete reports.
Original and candidate producer files are read-only; outputs are new sidecars.
"""
from __future__ import annotations
import argparse
import ast
import gzip
import hashlib
import json
import os
from pathlib import Path
import stat
import time

ORIGINAL_POLICY = '6100810b5bd7eca23b063f90dd0c1e81cb3a7f55ba3aeab76f02be84720fb3ae'
CANDIDATE_POLICY = '72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc'
PROTOTYPE = '36efac2461e102d1c0ae66282c5ea46facff6c1dcdd6b8bb376828e6e0525e1e'
HISTORICAL_LOGICAL = '7149ca35ebc44b0833434505a94d187e11194a2eca7d4f45b885b061fb267cd4'
POLICY_PATH = '/selectionPipelineSha256/selection_policy.py'
SELECTED_FIELDS = ('sha256','sizeBytes','logicalSha256','logicalSizeBytes','memberships')


def require(value, message):
    if not value: raise ValueError(message)


def identity(path):
    require(not path.is_symlink(), f'symlink evidence refused: {path}')
    s=path.stat(); require(stat.S_ISREG(s.st_mode),f'nonregular evidence: {path}')
    return (s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns)


def file_sha(path):
    before=identity(path)
    with path.open('rb') as f:
        s=os.fstat(f.fileno());require((s.st_dev,s.st_ino)==before[:2],'file replaced before hash')
        digest=hashlib.file_digest(f,'sha256').hexdigest()
    require(identity(path)==before,f'file changed while hashing: {path}')
    return digest


def canonical(value):
    return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()


def load(path, bindings):
    before=identity(path);raw=path.read_bytes()
    require(identity(path)==before,f'file changed while reading: {path}')
    def pairs(items):
        obj={}
        for key,value in items:
            require(key not in obj,'duplicate JSON key');obj[key]=value
        return obj
    value=json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda v: (_ for _ in ()).throw(ValueError('nonfinite JSON')))
    bindings[str(path)]={'identity':list(before),'sha256':hashlib.sha256(raw).hexdigest()}
    return value


def verify_bindings(bindings):
    for name,pin in bindings.items():
        path=Path(name)
        require(list(identity(path))==pin['identity'] and file_sha(path)==pin['sha256'],f'bound artifact changed: {path}')


def differences(left,right,path=''):
    result=[]
    if type(left) is not type(right):
        return [{'path':path,'before':left,'after':right}]
    if isinstance(left,dict):
        for key in sorted(set(left)|set(right)):
            next_path=path+'/'+key
            if key not in left or key not in right:
                result.append({'path':next_path,'beforePresent':key in left,'afterPresent':key in right,'before':left.get(key),'after':right.get(key)})
            else:result.extend(differences(left[key],right[key],next_path))
    elif isinstance(left,list):
        if len(left)!=len(right):result.append({'path':path+'/length','before':len(left),'after':len(right)})
        for i,(a,b) in enumerate(zip(left,right)):result.extend(differences(a,b,path+'/'+str(i)))
    elif left!=right:result.append({'path':path,'before':left,'after':right})
    return result


def require_report_equivalence(before,after,diffs):
    expected=[{'path':POLICY_PATH,'before':ORIGINAL_POLICY,'after':CANDIDATE_POLICY}]
    require(diffs==expected,'unexpected complete report difference; only reviewed policy fingerprint may change')


def stream_selected(path):
    """One descriptor, bounded reads, complete gzip CRC/trailer traversal."""
    before=identity(path)
    digest=hashlib.sha256();size=lines=0;last=None
    with path.open('rb') as f:
        s=os.fstat(f.fileno());require((s.st_dev,s.st_ino)==before[:2],'selected file replaced before read')
        raw_sha=hashlib.file_digest(f,'sha256').hexdigest();f.seek(0)
        with gzip.GzipFile(fileobj=f,mode='rb') as stream:
            while block:=stream.read(4*1024*1024):
                digest.update(block);size+=len(block);lines+=block.count(b'\n');last=block[-1:]
    require(identity(path)==before,'selected file changed during full read')
    require(size>0 and last==b'\n','selected material empty or missing final newline')
    return {'sha256':raw_sha,'sizeBytes':before[2],'logicalSha256':digest.hexdigest(),'logicalSizeBytes':size,'memberships':lines}


def completed_producer(root,bindings):
    # Guard is checked first: incomplete execution must not trigger artifact reads.
    guard=load(root/'guard/RESOURCE.json',bindings)
    require(guard.get('status')=='child-exited-zero' and guard.get('exitCode')==0,'producer guard is not successful')
    profile=load(root/'full-control/PROFILE.json',bindings)
    require(profile.get('status')=='complete-full-saved-input-diagnostic' and profile.get('publicationSafe') is False and profile.get('inputScope')=='full-saved-input','producer profile incomplete/unsafe')
    require(profile['settings']['runs']==1 and profile['settings']['max_deck']==8000 and profile['settings']['quality_policy']=='boundary-diversity-v2' and profile['settings']['measurement_mode']=='stages','unexpected full control settings')
    require(len(profile['runs'])==1 and profile['runs'][0]['run']==1,'expected exactly one completed run')
    report=load(root/'full-control/run-01/selection.json',bindings);run=profile['runs'][0]
    require(report.get('publicationSafe') is False and report.get('status')=='admitted-everyday-selection-review','unexpected selection status')
    require(canonical(report)==run['outputIdentity']['reportSha256'],'canonical report/profile identity mismatch')
    require(report['stage']==run['stageCounts'] and report['summary']==run['summary'],'profile/report counters differ')
    require(report['selectedMaterial']['sha256']==run['outputIdentity']['selectedSha256'] and report['selectedMaterial']['logicalSha256']==run['outputIdentity']['selectedLogicalSha256'],'profile/report selected identities differ')
    require(report['selectedMaterial']['sizeBytes']==run['selectedBytes'],'profile selected byte count differs')
    require(run['stageMetrics']['status']=='complete' and all(x['failedCalls']==0 for x in run['stageMetrics']['stages'].values()),'stage metrics incomplete or failed')
    require(profile['inputPins']=={'pool':report['admittedInput']['poolSha256'],'poolReport':report['admittedInput']['poolReportSha256'],'registry':report['admittedInput']['registrySha256']},'admitted pins/profile mismatch')
    for name,digest in report['selectionPipelineSha256'].items():require(profile['pipelineSha256'][name]==digest,'report/profile source mismatch')
    return guard,profile,report


def source_snapshot(repo,candidate,preparation,prototype,control_profile,candidate_profile):
    expected=preparation['originalSourceSha256'];require(expected['tools/catalog/selection_policy.py']==ORIGINAL_POLICY,'unexpected original policy')
    require(preparation['candidatePolicySha256']==CANDIDATE_POLICY and preparation['prototypeModuleSha256']==PROTOTYPE,'preparation policy/prototype mismatch')
    snapshots={}
    for root,is_candidate in ((repo,False),(candidate,True)):
        files={str(p.relative_to(root)) for p in (root/'tools/catalog').rglob('*') if p.is_file() and '__pycache__' not in p.parts}
        require(files==set(expected),'source/data file inventory changed')
        for name,digest in expected.items():
            wanted=CANDIDATE_POLICY if is_candidate and name=='tools/catalog/selection_policy.py' else digest
            path=root/name;actual=file_sha(path);require(actual==wanted,f'unexpected source/data change: {path}')
            snapshots[str(path)]={'identity':list(identity(path)),'sha256':actual}
    require(file_sha(prototype)==PROTOTYPE,'reviewed standalone prototype changed')
    snapshots[str(prototype)]={'identity':list(identity(prototype)),'sha256':PROTOTYPE}
    key='selection_policy.py';required=dict(control_profile['pipelineSha256']);required[key]=CANDIDATE_POLICY
    require(control_profile['pipelineSha256'][key]==ORIGINAL_POLICY and candidate_profile['pipelineSha256']==required,'profile pipeline differences exceed exact reviewed policy')
    for name,digest in control_profile['pipelineSha256'].items():require(expected['tools/catalog/'+name]==digest,'producer source pin differs from source checkpoint')
    def tree(path):return ast.parse(path.read_bytes())
    def chooser(t):return next(x for x in t.body if isinstance(x,ast.FunctionDef) and x.name=='choose_contexts')
    original=tree(repo/'tools/catalog/selection_policy.py');changed=tree(candidate/'tools/catalog/selection_policy.py');proto=tree(prototype)
    require(ast.dump(chooser(changed),include_attributes=False)==ast.dump(chooser(proto),include_attributes=False),'candidate function AST differs from reviewed prototype')
    original.body.remove(chooser(original));changed.body.remove(chooser(changed))
    require(ast.dump(original,include_attributes=False)==ast.dump(changed,include_attributes=False),'non-chooser policy AST changed')
    return snapshots


def stage_comparison(original,candidate):
    old=original['runs'][0]['stageMetrics'];new=candidate['runs'][0]['stageMetrics']
    require(set(old['stages'])==set(new['stages']),'stage inventory differs')
    rows=[]
    for name in sorted(old['stages']):
        a,b=old['stages'][name],new['stages'][name]
        require(a['calls']==b['calls'],'stage call count differs: '+name)
        fields=('calls','failedCalls','elapsedSeconds','processCpuSeconds','exclusiveElapsedSeconds','exclusiveProcessCpuSeconds','sampledPeakRssBytes','rssAttempts','rssSamples')
        rows.append({'stage':name,'control':{k:a[k] for k in fields},'candidate':{k:b[k] for k in fields},'candidateOverControlElapsedRatio':b['elapsedSeconds']/a['elapsedSeconds'] if a['elapsedSeconds'] else None})
    return {'stages':rows,'controlTimingSemantics':old['timingSemantics'],'candidateTimingSemantics':new['timingSemantics'],'controlRssSemantics':old['rssSemantics'],'candidateRssSemantics':new['rssSemantics']}


def write_new(path,result):
    require(not path.exists(),'comparison output already exists')
    temporary=path.with_name(path.name+'.tmp')
    created=False
    try:
        with temporary.open('x',encoding='utf-8') as f:
            created=True
            json.dump(result,f,ensure_ascii=False,indent=2,allow_nan=False);f.write('\n');f.flush();os.fsync(f.fileno())
        os.link(temporary,path)
    finally:
        if created and temporary.exists():temporary.unlink()


def compare(args):
    started=time.monotonic();bindings={};result={'status':'incomplete','publicationSafe':False,'productionIntegrationApproved':False}
    candidate_root=args.candidate_dir.resolve();control_root=args.control_dir.resolve();repo=args.repo.resolve()
    output=candidate_root/'CANDIDATE-CONTROL-COMPARISON.json'
    require(not output.exists(),'completed comparison already exists')
    try:
        helper=Path(__file__).resolve()
        bindings[str(helper)]={'identity':list(identity(helper)),'sha256':file_sha(helper)}
        cg,cp,cr=completed_producer(candidate_root,bindings)
        og,op,orr=completed_producer(control_root,bindings)
        expected_limits={'max_rss_bytes':4*1024**3,'address_space_bytes':5*1024**3,'min_available_bytes':2*1024**3,'min_disk_free_bytes':3*1024**3,'poll_seconds':1.0}
        require(cg['limits']==expected_limits,'candidate guard limits differ from authorized limits')
        require(og['limits']==dict(expected_limits,min_disk_free_bytes=4*1024**3),'control guard limits differ from bound control')
        control=load(control_root/'CONTROL-COMPARISON.json',bindings)
        require(control['status']=='complete-independent-control-comparison' and control['historicalLogicalIdentityMatches'] is True and control['historicalCompressedIdentityMatches'] is True and control['reportedIntegrityMatches'] is True,'historical control comparison did not pass')
        require(control['profileFileSha256']==bindings[str(control_root/'full-control/PROFILE.json')]['sha256'] and control['selectionReportFileSha256']==bindings[str(control_root/'full-control/run-01/selection.json')]['sha256'],'historical comparison/control binding mismatch')
        prep=load(candidate_root/'PREPARATION.json',bindings)
        prototype=candidate_root.parent/'round32-overlap-prototype/overlap_reuse.py'
        sources=source_snapshot(repo,candidate_root/'candidate-source',prep,prototype,op,cp)
        result['allReportDifferences']=differences(orr,cr)
        result['allReportDifferenceCount']=len(result['allReportDifferences'])
        result['allowedDifference']={'path':POLICY_PATH,'before':ORIGINAL_POLICY,'after':CANDIDATE_POLICY}
        require_report_equivalence(orr,cr,result['allReportDifferences'])
        for key in ('inputPins','settings','environment','inputScope','inputBytes','measurementContract'):require(op[key]==cp[key],'control/candidate profile contract differs: '+key)
        actual=stream_selected(candidate_root/'full-control/run-01/selected.jsonl.gz')
        selected=candidate_root/'full-control/run-01/selected.jsonl.gz'
        bindings[str(selected)]={'identity':list(identity(selected)),'sha256':actual['sha256']}
        result['independentlyComputedCandidateSelected']=actual
        for source in (cr['selectedMaterial'],orr['selectedMaterial'],control['independentlyComputedSelected']):
            require(actual=={k:source[k] for k in SELECTED_FIELDS},'candidate complete output differs from report/control identity')
        require(actual['logicalSha256']==HISTORICAL_LOGICAL,'historical logical identity mismatch')
        history=repo/'docs/evidence/everyday-round12/EVERYDAY-SELECTION.json.gz'
        require(file_sha(history)==control['historicalReportSha256'],'historical report artifact changed')
        bindings[str(history)]={'identity':list(identity(history)),'sha256':control['historicalReportSha256']}
        with gzip.open(history,'rb') as f:historical=json.load(f)
        require(actual=={k:historical['selectedMaterial'][k] for k in SELECTED_FIELDS},'historical selected bytes/counts differ')
        for key in ('stage','summary','decks'):
            require(orr[key]==cr[key]==historical[key],'historical/control/candidate counters differ: '+key)
        require(orr['qualityPolicy']['counts']==cr['qualityPolicy']['counts']==historical['qualityPolicy']['counts'],'historical quality counts differ')
        result.update(stageComparison=stage_comparison(op,cp),evidenceFileSha256={k:v['sha256'] for k,v in bindings.items()},sourceFilesChecked=len(sources),candidateFunctionAstEqualsReviewedPrototype=True,normalSourceUnchanged=True,isolatedSourceOnlyPolicyChanged=True)
        run_old,run_new=op['runs'][0],cp['runs'][0]
        result['wholeRunComparison']={'controlElapsedSeconds':run_old['elapsedSeconds'],'candidateElapsedSeconds':run_new['elapsedSeconds'],'candidateOverControlElapsedRatio':run_new['elapsedSeconds']/run_old['elapsedSeconds'],'controlLifetimePeakRssBytes':run_old['processLifetimePeakRssBytes'],'candidateLifetimePeakRssBytes':run_new['processLifetimePeakRssBytes'],'controlGuard':{k:og[k] for k in ('limits','sampledPeakRssBytes','minimumAvailableBytes','minimumDiskFreeBytes')},'candidateGuard':{k:cg[k] for k in ('limits','sampledPeakRssBytes','minimumAvailableBytes','minimumDiskFreeBytes')}}
        verify_bindings(bindings);verify_bindings(sources)
        result.update(status='complete-independent-candidate-control-comparison',fullCandidateGzipCrcRead=True,exactCompressedSelectedEqual=True,exactLogicalSelectedEqual=True,historicalSelectedIdentityEqual=True,allCountsDecksQualityEqual=True,comparisonElapsedSeconds=time.monotonic()-started,helperSha256=file_sha(Path(__file__)),limitations=['One full control and one full candidate invocation; shared host and uncontrolled OS/filesystem/process cache conditions, not a calibrated repeat-run speedup.','Pair overlap reuse changes token-cache hit counts/recency; this is part of candidate execution cost.','Stage wall/CPU include instrumentation overhead; process CPU includes other threads; sampled whole-process stage RSS is not isolated allocation.','Resource guards used different explicit disk floors (control 4 GiB, candidate 3 GiB); hidden cgroup limits remain unknown.','No production integration, new admission, semantic quality gain, content freeze or publication.','No SQLite files opened or hashed by this comparison; selected-material bytes and complete report contracts are the compared outcome.'])
        write_new(output,result);return result
    except BaseException as exc:
        result.update(status='failed-independent-candidate-control-comparison',failure={'type':type(exc).__name__,'message':str(exc)},comparisonElapsedSeconds=time.monotonic()-started)
        failure=candidate_root/'CANDIDATE-CONTROL-COMPARISON.failed.json'
        if not failure.exists():write_new(failure,result)
        raise


def parser():
    here=Path(__file__).resolve().parent;p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--candidate-dir',type=Path,default=here)
    p.add_argument('--control-dir',type=Path,default=here.parent/'round30')
    p.add_argument('--repo',type=Path,default=here.parent.parent/'ikna-work')
    return p


if __name__=='__main__':
    outcome=compare(parser().parse_args())
    print(json.dumps({'status':outcome['status'],'selected':outcome['independentlyComputedCandidateSelected'],'wholeRunComparison':outcome['wholeRunComparison']},indent=2))
