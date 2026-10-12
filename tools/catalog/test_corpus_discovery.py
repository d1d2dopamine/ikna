import csv
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import corpus_discovery as d

class DiscoveryTest(unittest.TestCase):
    def opus(self, **kw):
        return dict(corpus='NewSource',version='v1',source='ko',target='pl',url='https://example.invalid/data',alignment_pairs=4,**kw)
    def test_explicit_pair(self):
        r=d.normalize('opus',{'corpora':[self.opus()]})[0]
        self.assertEqual(r['pairEvidence'],'explicit-pair');self.assertFalse(r['productionAdmission'])
    def test_pair_metadata_outranks_broad_tags(self):
        a=d.normalize('opus',{'corpora':[dict(self.opus(),corpus='Tatoeba')]})[0]
        b=d.normalize('hf-tags',[{'id':'new/broad','tags':['language:pl','language:ko']}])[0]
        rr,_=d.rank([b,a]);self.assertEqual(rr[0]['dataset'],'Tatoeba')
    def test_live_byte_limit(self):
        class Response:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,n):return b' '*(d.MAX_BYTES+1)
        class Opener:
            def open(self,*args,**kw):return Response()
        with patch.object(d.urllib.request,'build_opener',return_value=Opener()):
            with self.assertRaisesRegex(ValueError,'metadata-byte-limit'):d.fetch(d.QUERIES[0][1])
    def test_revision_bound_false_positive_demotion(self):
        name='apple/hat';version=d.OBSERVATIONS[name][0]
        a=d.normalize('hf-tags',[{'id':name,'sha':version,'tags':['language:pl','language:ko']}])[0]
        self.assertEqual(a['humanTranslationEvidence'],'confirmed-MT-output');self.assertLess(d.rank([a])[0][0]['investigationPriority'],0)
    def test_new_revision_keeps_lead_without_old_demotion(self):
        a=d.normalize('hf-tags',[{'id':'apple/hat','sha':'NEW-SYNTHETIC-REVISION','tags':[]}])[0]
        self.assertIsNone(a['revisionBoundManualObservation']);self.assertEqual(len(d.rank([a])[0]),1)
    def test_other_pair_not_inferred(self):
        x=self.opus();x['target']='en';self.assertEqual(d.normalize('opus',{'corpora':[x]})[0]['pairEvidence'],'unverified')
    def test_hf_tags_not_direct_pair(self):
        x={'id':'new/source','tags':['language:pl','language:ko','license:cc-by-4.0']}
        r=d.normalize('hf-tags',[x])[0]
        self.assertEqual(r['pairEvidence'],'both-language-tags');self.assertEqual(r['originalTextRights'],'requires-primary-evidence')
        self.assertFalse(r['sampleAuthorizedByMetadataAlone'])
    def test_unknown_remains_visible(self):
        r=d.normalize('opus',{'corpora':[self.opus()]})[0];self.assertEqual(d.rank([r])[0][0]['knownDecision'],'needs-investigation')
    def test_known_family_aliases(self):
        for name,f in [('Helsinki-NLP/open_subtitles','opensubtitles'),('openlanguagedata/flores_plus','flores'),('NeuLab-TedTalks','ted'),('Helsinki-NLP/opus-100','opus100'),('x/open_subtitles_pl_ko','opensubtitles'),('x/opus-100-pl-ko','opus100')]:self.assertEqual(d.family(name)[0],f)
    def test_no_substring_false_family(self):self.assertIsNone(d.family('deflorescence/new-text')[0])
    def test_versions_preserved(self):
        a=d.normalize('opus',{'corpora':[self.opus()]})[0];b=dict(a,version='v2');self.assertEqual(len(d.rank([a,b])[0]),2)
    def test_family_does_not_delete_subset(self):
        a=d.normalize('hf-search',[{'id':'x/flores_new','tags':[]}])[0];self.assertEqual(len(d.rank([a])[0]),1)
    def test_exact_dedup_preserves_discovery_routes(self):
        a=d.normalize('hf-search',[{'id':'x/data','tags':['language:pl']}])[0];b=d.normalize('hf-tags',[{'id':'x/data','tags':['language:pl','language:ko']}])[0]
        rr,n=d.rank([a,b]);self.assertEqual(n,1);self.assertEqual(rr[0]['pairEvidence'],'both-language-tags');self.assertEqual(len(rr[0]['discoveredBy']),2)
    def test_no_content_fields_emitted(self):
        r=d.normalize('hf-search',[{'id':'x/data','tags':[],'description':'SYNTHETIC FIXTURE TEXT','cardData':{'example':'SYNTHETIC EXAMPLE'}}]);self.assertNotIn('SYNTHETIC',json.dumps(r))
    def test_bad_schema(self):
        for obj in [{}, {'corpora':[None]}, {'corpora':[{}]}, {'corpora':[self.opus()]*501}]:
            with self.assertRaises(ValueError):d.normalize('opus',obj)
    def test_nonallowlisted_url_rejected_before_network(self):
        with self.assertRaises(ValueError):d.fetch('http://127.0.0.1/private')
    def test_redirect_stays_host_https(self):
        req=d.urllib.request.Request('https://opus.nlpl.eu/opusapi')
        for u in ['http://opus.nlpl.eu/a','https://other.invalid/a','https://opus.nlpl.eu/unbounded']:
            with self.assertRaises(ValueError):d.RestrictedRedirect().redirect_request(req,None,302,'',{},u)
    def fixture(self,root):
        p=root/'gaps.csv'
        with p.open('w',newline='') as f:
            w=csv.DictWriter(f,fieldnames=['deckId','lang','meaningLang']);w.writeheader()
            for a,b in [('pl','ko'),('ko','pl')]:
                for level in ['beginner','middle','advanced']:w.writerow({'deckId':f'{a}-{b}-{level}','lang':a,'meaningLang':b})
        return p
    def test_replay_complete_and_manifest(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);c=p/'cache';c.mkdir();g=self.fixture(p)
            for name,_ in d.QUERIES:d.write(c/(name+'.json'),{'corpora':[self.opus()]} if name=='opus' else [])
            s=d.run(c,p/'out',g,True);self.assertEqual(s['networkRequests'],0);self.assertEqual(s['distinctLeads'],1)
            m=json.loads((p/'out/MANIFEST.json').read_text())
            for n,x in m.items():self.assertEqual(d.sha((p/'out'/n).read_bytes()),x['sha256'])
    def test_partial_reports_failures(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);c=p/'cache';c.mkdir();s=d.run(c,p/'out',self.fixture(p),True)
            self.assertEqual(s['status'],'partial-metadata-discovery');self.assertEqual(s['successfulQueries'],0)
    def test_existing_output_untouched(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);(p/'out').mkdir()
            with self.assertRaises(ValueError):d.run(p/'c',p/'out',self.fixture(p))
    def test_input_shape_gate(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'gaps';p.write_text('deckId,lang,meaningLang\nx,en,ko\n')
            with self.assertRaises(ValueError):d.gaps(p)

if __name__=='__main__':unittest.main()
