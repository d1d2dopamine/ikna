import datetime,hashlib,json,os,pathlib,shutil,statistics,subprocess
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path(__file__).resolve().parent;probes=root/'ikna-results/round32-overlap-prototype';control=root/'ikna-results/round30';source=r/'candidate-source/tools/catalog'
sequence=json.loads((probes/'REAL-SEQUENCE.json').read_text());assert sequence['status']=='complete-seven-serial-probes'
baselines=[];candidates=[]
for run in sequence['runs']:
 p=json.loads((probes/run['name']/'PROBE.json').read_text());assert run['exitCode']==0 and p['outputIdentity']=={'sha256':'f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd','sizeBytes':31461064}
 if run['mode']=='baseline':baselines.append(p['pairMeasurement']['elapsedSeconds'])
 elif run['mode']=='candidate':candidates.append(p['pairMeasurement']['elapsedSeconds'])
assert len(baselines)==len(candidates)==3 and statistics.median(candidates)<statistics.median(baselines)*.8
pins=json.loads((control/'full-control/PROFILE.json').read_text())['pipelineSha256'];differences=[]
for n,h in pins.items():
 assert hashlib.sha256((root/'ikna-work/tools/catalog'/n).read_bytes()).hexdigest()==h
 if hashlib.sha256((source/n).read_bytes()).hexdigest()!=h:differences.append(n)
assert differences==['selection_policy.py'];assert hashlib.sha256((source/'selection_policy.py').read_bytes()).hexdigest()=='72783a30ada3995c9cc4e32f96a4fc5ff53940756ceb5895c9530ecd9af92edc'
old=json.loads((control/'guard/RESOURCE.json').read_text());peak=old['preflight']['diskFreeBytes']-old['minimumDiskFreeBytes'];floor=3*1024**3;reserve=100*1024**2;free=shutil.disk_usage(r).free
preflight={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'freeDiskBytes':free,'originalObservedAdditionalPeakDiskBytes':peak,'candidateDiskStopFloorBytes':floor,'reservedCompactEvidenceBytes':reserve,'projectedMinimumBytes':free-peak,'projectedMarginAboveFloorAndReserveBytes':free-peak-floor-reserve,'capacityProjectionPassed':free>=peak+floor+reserve,'retainedOriginalEvidence':True,'policyDifferenceOnly':differences,'originalMedianPairSeconds':statistics.median(baselines),'candidateMedianPairSeconds':statistics.median(candidates),'limits':['Capacity estimate uses one previous run; guard may stop if actual usage is larger','No user or original evidence deleted','No further disk-floor lowering on failure','Memory limits unchanged; output/quality acceptance unchanged','Experimental isolated source only; normal selector not integrated']}
(r/'PREFLIGHT.json').write_text(json.dumps(preflight,indent=2)+'\n');assert preflight['capacityProjectionPassed'],'insufficient projected disk headroom'
icu=root/'ikna-toolchain/icu-74.2/runtime/icu/usr/local/lib';env=dict(os.environ,LD_LIBRARY_PATH=str(icu),IKNA_ICU_LIBRARY=str(icu/'libicui18n.so.74.2'),IKNA_ICU_VERSION='74.2',PYTHONDONTWRITEBYTECODE='1')
args=[str(root/'ikna-venv/bin/python'),str(source/'selection_profile_guard.py'),'--evidence-dir',str(r/'guard'),'--min-disk-free-bytes',str(floor),'--','--pool',str(root/'ikna-input/admitted-pool/pool/everyday-candidates.jsonl.gz'),'--pool-report',str(root/'ikna-input/admitted-pool/reports/EVERYDAY-POOL.json'),'--output-dir',str(r/'full-control'),'--input-scope','full-saved-input','--measurement-mode','stages','--runs','1','--max-input-bytes','545718435','--max-deck','8000']
(r/'COMMAND.json').write_text(json.dumps(args,indent=2)+'\n');(r/'STARTED-UTC.txt').write_text(datetime.datetime.now(datetime.timezone.utc).isoformat()+'\n')
with (r/'SUPERVISOR.log').open('x') as f:result=subprocess.run(args,env=env,stdout=f,stderr=subprocess.STDOUT)
(r/'FINISHED-UTC.txt').write_text(datetime.datetime.now(datetime.timezone.utc).isoformat()+'\n');(r/'EXIT-CODE.txt').write_text(str(result.returncode)+'\n');print((r/'SUPERVISOR.log').read_text(),flush=True);raise SystemExit(result.returncode)
