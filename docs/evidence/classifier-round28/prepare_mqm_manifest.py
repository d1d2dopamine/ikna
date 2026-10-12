"""Build evaluation-only ID/hash manifest, no text redistribution or inferred labels.
Usage: python prepare_mqm_manifest.py /tmp/mqm-ted-ende.tsv output.json
Input SHA-256 must match the pinned publisher file. No network or classifier use.
"""
import collections,csv,hashlib,json,pathlib,sys
EXPECTED='3952dc904997dda31c03421243862cb8a093f9a712fd90dc97b9709b4bb387b3'
p=pathlib.Path(sys.argv[1]); assert hashlib.sha256(p.read_bytes()).hexdigest()==EXPECTED
rows=list(csv.DictReader(p.open(encoding='utf-8'),delimiter='\t')); groups=collections.defaultdict(list)
for r in rows: groups[(r['system'],r['doc'],r['seg_id'])].append(r)
def clean(x): return x.replace('<v>','').replace('</v>','')
def sha(x):return hashlib.sha256(x.encode('utf-8')).hexdigest()
candidates=[]
for key,rs in groups.items():
    if len({clean(r['source']) for r in rs})!=1 or len({clean(r['target']) for r in rs})!=1:
        raise ValueError('Inconsistent annotated texts: '+str(key))
    if any(r['category']=='Non-translation' or (r['category'].startswith('Accuracy/') and r['severity']=='Major') for r in rs): label='major_accuracy_error'
    elif all(r['category']=='No-error' and r['severity']=='No-error' for r in rs): label='explicit_no_error'
    else: continue
    split='calibration' if key[1] in ('talk.1','talk.3') else 'held_out'
    candidates.append({'system':key[0],'doc':key[1],'seg_id':key[2],'split':split,'label':label,'source_sha256':sha(clean(rs[0]['source'])),'target_sha256':sha(clean(rs[0]['target'])),'raters':sorted({r['rater'] for r in rs}),'priority':sha('round28-mqm-v1\0'+'\0'.join(key))})
selected=[]; seen_sources=set()
for split,n in [('calibration',50),('held_out',100)]:
    for label in ['major_accuracy_error','explicit_no_error']:
        picked=[]
        for r in sorted(candidates,key=lambda x:x['priority']):
            if r['split']!=split or r['label']!=label or r['source_sha256'] in seen_sources:continue
            picked.append(r);seen_sources.add(r['source_sha256'])
            if len(picked)==n:break
        assert len(picked)==n,(split,label,len(picked))
        selected.extend(picked)
# Text-exact exclusion alone is not a proof of absence of paraphrase overlap.
result={'schema':'ikna-external-mqm-id-manifest-v1','role':'evaluation-only; never catalogue candidates','source_commit':'29acd6999aaea3586378c9dcb58d8cc4820cede3','source_path':'ted/ende/mqm_ted_ende.tsv','source_sha256':EXPECTED,'language_pair':'en-de','labels':'independent single-rater MQM annotations; no-error is observed label, not guaranteed correctness','split_rule':'talk.1/talk.3 calibration; talk.4/talk.5/talk.6 held out; hash-priority selection; globally unique source text; errors sampled first','counts':dict(collections.Counter(r['split']+'/'+r['label'] for r in selected)),'pairs':selected}
pathlib.Path(sys.argv[2]).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(result['counts'])
