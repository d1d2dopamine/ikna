"""Independent final-output integrity/control comparison; run only after success."""
import gzip,hashlib,json,pathlib
root=pathlib.Path(__file__).resolve().parent
repo=root.parent.parent/'ikna-work'
historical_path=repo/'docs/evidence/everyday-round12/EVERYDAY-SELECTION.json.gz'
profile_path=root/'full-control/PROFILE.json'
profile=json.loads(profile_path.read_text())
guard=json.loads((root/'guard/RESOURCE.json').read_text())
if guard['status']!='child-exited-zero' or guard['exitCode']!=0:raise ValueError('guard did not finish successfully')
if profile['status']!='complete-full-saved-input-diagnostic':raise ValueError('full profile incomplete')
old=json.load(gzip.open(historical_path))
selection_report_path=root/'full-control/run-01/selection.json'
new=json.loads(selection_report_path.read_text())
selected=root/'full-control/run-01/selected.jsonl.gz'
with selected.open('rb') as f:compressed=hashlib.file_digest(f,'sha256').hexdigest()
digest=hashlib.sha256();size=lines=0
with gzip.open(selected,'rb') as f:
 while block:=f.read(4*1024*1024):
  digest.update(block);size+=len(block);lines+=block.count(b'\n')
actual={'sha256':compressed,'sizeBytes':selected.stat().st_size,'logicalSha256':digest.hexdigest(),'logicalSizeBytes':size,'memberships':lines}
reported={k:new['selectedMaterial'][k] for k in actual}
if actual!=reported:raise ValueError('independent selected bytes disagree with report')
diffs=[]
def walk(a,b,path=''):
 if type(a)!=type(b):diffs.append({'path':path,'old':a,'new':b});return
 if isinstance(a,dict):
  for k in sorted(a.keys()|b.keys()):
   if k not in a or k not in b:diffs.append({'path':path+'/'+k,'old':a.get(k),'new':b.get(k)})
   else:walk(a[k],b[k],path+'/'+k)
 elif isinstance(a,list):
  if len(a)!=len(b):diffs.append({'path':path+'/length','old':len(a),'new':len(b)})
  for i,(x,y) in enumerate(zip(a,b)):walk(x,y,path+'/'+str(i))
 elif a!=b:diffs.append({'path':path,'old':a,'new':b})
walk(old,new)
metadata_prefixes=('/selectionPipelineSha256/','/selectionEnvironment/python','/selectionEnvironment/zlib','/selectedMaterial/sha256','/selectedMaterial/sizeBytes')
semantic=[x for x in diffs if not x['path'].startswith(metadata_prefixes)]
staging=root/'full-control/run-01/selection.sqlite3'
before=staging.stat()
with staging.open('rb') as f:staging_sha=hashlib.file_digest(f,'sha256').hexdigest()
after=staging.stat()
if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns,before.st_ctime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns,after.st_ctime_ns):raise ValueError('staging changed while hashing')
result={'profileFileSha256':hashlib.sha256(profile_path.read_bytes()).hexdigest(),'selectionReportFileSha256':hashlib.sha256(selection_report_path.read_bytes()).hexdigest(),'stagingSha256':staging_sha,'stagingSizeBytes':before.st_size,'status':'complete-independent-control-comparison','historicalReportSha256':hashlib.sha256(historical_path.read_bytes()).hexdigest(),'fullSelectedGzipIntegrityRead':True,'independentlyComputedSelected':actual,'reportedIntegrityMatches':True,'historicalLogicalIdentityMatches':actual['logicalSha256']==old['selectedMaterial']['logicalSha256'],'historicalCompressedIdentityMatches':actual['sha256']==old['selectedMaterial']['sha256'],'stageCountsEqual':new['stage']==old['stage'],'summaryEqual':new['summary']==old['summary'],'qualityCountsEqual':new['qualityPolicy']['counts']==old['qualityPolicy']['counts'],'deckInventoryEqual':new['decks']==old['decks'],'allReportDifferenceCount':len(diffs),'allReportDifferences':diffs,'explicitMetadataDifferencePrefixes':metadata_prefixes,'otherReportDifferenceCount':len(semantic),'otherReportDifferences':semantic,'limits':['Historical-policy8000cap diagnostic, not current12000scenario or publication','LocalPython/zlibversions differ from historical report; originalICUbinaryhash unknown','One fullrun only; OSfilesystemcache uncontrolled; no fullcachebenefit measured']}
(root/'CONTROL-COMPARISON.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['allReportDifferences','otherReportDifferences']},indent=2))
