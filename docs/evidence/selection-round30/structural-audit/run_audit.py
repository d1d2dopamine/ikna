"""Local structural audit launcher; current-self-baseline, not historical comparison."""
import hashlib,json,os,pathlib,resource,runpy,shutil,signal,subprocess,sys,time,types
ROOT=pathlib.Path(__file__).resolve().parent
BASE=ROOT.parents[2]
REPO=BASE/'ikna-work'
TOOLS=REPO/'tools/catalog'
sys.path.insert(0,str(TOOLS))
import selection_profile_guard as guard

def sha(p):
 with pathlib.Path(p).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def child():
 guard.parent_death_guard(int(sys.argv[2]))
 resource.setrlimit(resource.RLIMIT_AS,(3*guard.GIB,3*guard.GIB))
 argv=json.loads((ROOT/'COMMAND.json').read_text())
 sys.argv=[str(TOOLS/'selection_evaluate.py'),*argv]
 runpy.run_path(str(TOOLS/'selection_evaluate.py'),run_name='__main__')

if sys.argv[1:2]==['--child']:
 child();raise SystemExit

current=ROOT.parent/'full-control/run-01'
pool=BASE/'ikna-input/admitted-pool/reports/EVERYDAY-POOL.json'
baseline=ROOT/'current-self-baseline'
baseline.mkdir()
sources={'reports/EVERYDAY-POOL.json':pool,'reports/EVERYDAY-SELECTION.json':current/'selection.json','review/everyday-selection-preview.jsonl.gz':current/'preview.jsonl.gz'}
pins={}
for name,source in sources.items():
 dest=baseline/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
 digest=sha(source)
 if sha(dest)!=digest:raise ValueError('baseline copy hash mismatch')
 pins[name]={'source':str(source),'sha256':digest,'sizeBytes':source.stat().st_size}
report_sha=pins['reports/EVERYDAY-SELECTION.json']['sha256']
scope={'status':'prepared-current-self-baseline','baselineKind':'current-self-baseline','publicationSafe':False,'semanticAccuracyCertified':False,'historicalPreviewComparison':False,'purpose':'Full selected-material structural/provenance/boundary audit using unchanged selection_evaluate.py. Baseline comparisons are self-consistency only; no quality gain or historical-preview inference.','baselineFiles':pins,'selectionReportSha256':report_sha,'evaluatorSha256':sha(TOOLS/'selection_evaluate.py')}
guard.atomic_json(ROOT/'SCOPE.json',scope)
argv=['--selected',str(current/'selected.jsonl.gz'),'--selection-report',str(current/'selection.json'),'--baseline-directory',str(baseline),'--baseline-report-sha256',report_sha,'--output-dir',str(ROOT/'results')]
guard.atomic_json(ROOT/'COMMAND.json',argv)
tmp=ROOT/'tmp';tmp.mkdir()
icu=BASE/'ikna-toolchain/icu-74.2/runtime/icu/usr/local/lib'
env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1','PYTHONUNBUFFERED':'1','IKNA_ICU_LIBRARY':str(icu/'libicui18n.so.74.2'),'IKNA_ICU_VERSION':'74.2','LD_LIBRARY_PATH':str(icu),**{k:str(tmp) for k in ('TMPDIR','TMP','TEMP','SQLITE_TMPDIR')}}
limits=types.SimpleNamespace(max_rss_bytes=2*guard.GIB,min_available_bytes=2*guard.GIB,min_disk_free_bytes=4*guard.GIB)
evidence={'status':'preflight','publicationSafe':False,'resumable':False,'childPid':None,'limits':{**vars(limits),'addressSpaceBytes':3*guard.GIB,'pollSeconds':1},'samples':0,'sampledPeakRssBytes':0,'minimumAvailableBytes':None,'minimumDiskFreeBytes':None,'limitations':['Cgroup visibility may be unavailable; hidden limits remain unknown.','Sampled resource thresholds may overshoot between polls.','Current-self-baseline supplies self-consistency only, never independent semantic or historical-preview evidence.']}
child_process=None
prior=signal.signal(signal.SIGTERM,guard.handle_sigterm)
start=time.monotonic()
try:
 sample=guard.resource_sample(os.getpid(),ROOT);evidence['preflight']=sample
 reason=guard.violation(sample,limits)
 if reason:raise RuntimeError('preflight refusal: '+reason)
 with (ROOT/'stdout.log').open('xb') as out,(ROOT/'stderr.log').open('xb') as err:
  child_process=subprocess.Popen([sys.executable,str(__file__),'--child',str(os.getpid())],stdout=out,stderr=err,env=env)
  evidence.update(status='running',childPid=child_process.pid)
  while child_process.poll() is None:
   try:sample=guard.resource_sample(child_process.pid,ROOT)
   except (OSError,ValueError):
    try:child_process.wait(timeout=.05)
    except subprocess.TimeoutExpired:raise
    break
   evidence['samples']+=1;evidence['latestSample']=sample
   evidence['sampledPeakRssBytes']=max(evidence['sampledPeakRssBytes'],sample['rssBytes'])
   for name,key in [('minimumAvailableBytes','availableBytes'),('minimumDiskFreeBytes','diskFreeBytes')]:
    evidence[name]=sample[key] if evidence[name] is None else min(evidence[name],sample[key])
   reason=guard.violation(sample,limits)
   if reason:raise RuntimeError('resource stop: '+reason)
   guard.atomic_json(ROOT/'RESOURCE.json',evidence);time.sleep(1)
  code=child_process.wait();evidence.update(status='child-exited-zero' if code==0 else 'child-failed',exitCode=code)
  if code:raise RuntimeError('evaluator exited '+str(code))
  for name,source in sources.items():
   if sha(source)!=pins[name]['sha256']:raise ValueError('source changed during audit')
  if sha(TOOLS/'selection_evaluate.py')!=scope['evaluatorSha256']:raise ValueError('evaluator source changed')
  result=json.loads((ROOT/'results/QUALITY.json').read_text())
  if result['status']!='complete-selected-material-contracts-passed':raise ValueError('unexpected evaluator status')
  scope.update(status='complete-current-self-baseline-structural-audit',evaluationReportSha256=sha(ROOT/'results/QUALITY.json'),memberships=result['memberships'],contexts=result['contexts'],origins=result['origins'],uniqueGlobalTargets=result['uniqueGlobalTargets'])
  guard.atomic_json(ROOT/'SCOPE.json',scope)
except BaseException as e:
 evidence.update(status='failed',error={'type':type(e).__name__,'message':str(e)})
 raise
finally:
 signal.signal(signal.SIGTERM,signal.SIG_IGN)
 try:
  if child_process is not None:guard.terminate_owned(child_process);evidence['exitCode']=child_process.returncode
  evidence['elapsedSeconds']=time.monotonic()-start
  guard.atomic_json(ROOT/'RESOURCE.json',evidence)
 finally:signal.signal(signal.SIGTERM,prior)
