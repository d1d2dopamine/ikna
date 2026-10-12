import datetime,hashlib,json,os,pathlib,subprocess
r=pathlib.Path(__file__).resolve().parent;root=r.parent.parent;icu=root/'ikna-toolchain/icu-74.2/runtime/icu/usr/local/lib'
env=dict(os.environ,LD_LIBRARY_PATH=str(icu),IKNA_ICU_LIBRARY=str(icu/'libicui18n.so.74.2'),IKNA_ICU_VERSION='74.2')
helper=r/'dense_pair_candidate_probe.py';candidate=r/'overlap_reuse.py'
assert hashlib.sha256(helper.read_bytes()).hexdigest()=='9c77cb25f6ca25eb5c62fd97751cf7b24e7840e264ef4cfa8872a2621f429975'
assert hashlib.sha256(candidate.read_bytes()).hexdigest()=='36efac2461e102d1c0ae66282c5ea46facff6c1dcdd6b8bb376828e6e0525e1e'
sequence=[('baseline-01','baseline'),('candidate-01','candidate'),('candidate-02','candidate'),('baseline-02','baseline'),('baseline-03','baseline'),('candidate-03','candidate'),('candidate-cprofile-01','candidate-cprofile')]
records=[]
for name,mode in sequence:
 args=[str(root/'ikna-venv/bin/python'),'-B',str(helper),'--repo',str(root/'ikna-work'),'--control-dir',str(root/'ikna-results/round30'),'--output-dir',str(r/name),'--mode',mode]
 if mode!='baseline':args+=['--reference-baseline',str(r/'baseline-01/PROBE.json')]
 if mode=='candidate-cprofile':args+=['--reference-candidate',str(r/'candidate-01/PROBE.json')]
 rec={'name':name,'mode':mode,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':args}
 with (r/(name+'.log')).open('x') as f:result=subprocess.run(args,env=env,stdout=f,stderr=subprocess.STDOUT)
 rec.update(exitCode=result.returncode,finishedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat());records.append(rec)
 (r/'REAL-SEQUENCE.json').write_text(json.dumps({'status':'incomplete','runs':records},indent=2)+'\n')
 if result.returncode:raise RuntimeError(name+' failed')
 p=json.loads((r/name/'PROBE.json').read_text());assert p['outputIdentity']=={'sha256':'f551785d003b9a8f0793bedd687829f0a3364ebf47cab80f83d2a1f052ee12bd','sizeBytes':31461064}
 print(name,p['pairMeasurement']['elapsedSeconds'],p['outputIdentity']['sha256'],flush=True)
(r/'REAL-SEQUENCE.json').write_text(json.dumps({'status':'complete-seven-serial-probes','runs':records,'limits':['Same exact prefix; no full-workflow speed claim','One sequential host; OS caches uncontrolled','Candidate repeats bind first completed same-helper baseline; alternating order is diagnostic, not randomized trial']},indent=2)+'\n')
