import sys,json,sqlite3,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(sys.argv[1])/'tools/catalog'))
import selection_experiment as s
from selection_policy import select_target_ids,choose_contexts
from catalogue_core import LEVELS
out={}
with sqlite3.connect(sys.argv[2]) as db:
 for lang,meaning in [('pl','ko'),('ko','pl')]:
  targets,stats=s.pair_evidence(db,'everyday',lang,meaning,None,12,diverse=True)
  for level in LEVELS:
   bucket=[t for t in targets if t['level']==level];ordered,_=select_target_ids(bucket,max(1,len(bucket)))
   used=set();kept=[]
   for t in ordered:
    contexts,_=choose_contexts([c for c in t['contexts'] if str(c.get('contextId') or c.get('candidateId')) not in used],3,.85,diverse=True,lang=lang)
    if not contexts:continue
    ids=[str(c.get('contextId') or c.get('candidateId')) for c in contexts];used.update(ids)
    kept.append({'targetId':t['targetId'],'contextIds':ids,'fullTargetContextSha256':hashlib.sha256(json.dumps({'target':{k:v for k,v in t.items() if k!='contexts'},'contexts':contexts},sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()})
   out[lang+'->'+meaning+':'+level]=kept
Path(sys.argv[3]).write_text(json.dumps(out,indent=2)+'\n')
