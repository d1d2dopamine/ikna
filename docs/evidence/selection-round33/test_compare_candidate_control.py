"""Tiny synthetic review-helper tests; never reads active candidate output/staging."""
import ast
import copy
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import compare_candidate_control as c

class ComparisonTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
    def reports(self):
        old={'selectionPipelineSha256':{'selection_policy.py':c.ORIGINAL_POLICY,'selection_output.py':'same'},'summary':{'selectedTargets':2},'decks':[{'x':1}],'selectedMaterial':{'sha256':'same'},'selectionEnvironment':{'python':'same'}}
        new=copy.deepcopy(old);new['selectionPipelineSha256']['selection_policy.py']=c.CANDIDATE_POLICY
        return old,new
    def test_only_exact_reviewed_policy_digest_allowed(self):
        old,new=self.reports();diff=c.differences(old,new)
        c.require_report_equivalence(old,new,diff)
        self.assertEqual(diff,[{'path':c.POLICY_PATH,'before':c.ORIGINAL_POLICY,'after':c.CANDIDATE_POLICY}])
    def test_other_code_metadata_bytes_counts_and_types_never_ignored(self):
        for edit in (lambda x:x['selectionPipelineSha256'].update({'selection_output.py':'different'}),lambda x:x['summary'].update(selectedTargets=3),lambda x:x['selectedMaterial'].update(sha256='different'),lambda x:x['selectionEnvironment'].update(python='different'),lambda x:x['decks'][0].update(x=1.0),lambda x:x['decks'].append({'x':2})):
            old,new=self.reports();edit(new)
            with self.assertRaisesRegex(ValueError,'unexpected complete report'):c.require_report_equivalence(old,new,c.differences(old,new))
    def test_unreviewed_policy_digest_or_no_change_fails(self):
        old,new=self.reports()
        for digest in (c.ORIGINAL_POLICY,'f'*64):
            new['selectionPipelineSha256']['selection_policy.py']=digest
            with self.assertRaises(ValueError):c.require_report_equivalence(old,new,c.differences(old,new))
    def test_streamed_raw_logical_crc_and_counts(self):
        raw=b'{"fixture":1}\n{"fixture":2}\n';p=self.root/'selected.gz';p.write_bytes(gzip.compress(raw,mtime=0))
        r=c.stream_selected(p)
        self.assertEqual(r,{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'sizeBytes':p.stat().st_size,'logicalSha256':hashlib.sha256(raw).hexdigest(),'logicalSizeBytes':len(raw),'memberships':2})
    def test_crc_failure_and_missing_final_newline_fail(self):
        p=self.root/'bad.gz';data=bytearray(gzip.compress(b'fixture\n',mtime=0));data[-8]^=1;p.write_bytes(data)
        with self.assertRaises((gzip.BadGzipFile,EOFError)):c.stream_selected(p)
        p.write_bytes(gzip.compress(b'fixture',mtime=0))
        with self.assertRaisesRegex(ValueError,'newline'):c.stream_selected(p)
    def test_incomplete_guard_refused_before_profile_read(self):
        (self.root/'guard').mkdir();(self.root/'guard/RESOURCE.json').write_text(json.dumps({'status':'running','exitCode':None}))
        original=c.load;calls=[]
        def recorded(path,bindings):calls.append(path);return original(path,bindings)
        with patch.object(c,'load',side_effect=recorded):
            with self.assertRaisesRegex(ValueError,'guard is not successful'):c.completed_producer(self.root,{})
        self.assertEqual(calls,[self.root/'guard/RESOURCE.json']);self.assertFalse((self.root/'full-control').exists())
    def test_json_duplicate_nonfinite_and_post_read_change_refused(self):
        p=self.root/'x.json'
        for value in ('{"a":1,"a":2}','{"a":NaN}'):
            p.write_text(value)
            with self.assertRaises(ValueError):c.load(p,{})
        p.write_text('{"a":1}');b={};c.load(p,b);p.write_text('{"a":2}')
        with self.assertRaisesRegex(ValueError,'bound artifact changed'):c.verify_bindings(b)
    def test_atomic_new_output_never_overwrites_prior_files(self):
        p=self.root/'report.json';c.write_new(p,{'ok':True})
        with self.assertRaises(ValueError):c.write_new(p,{'ok':False})
        self.assertTrue(json.loads(p.read_text())['ok'])
        q=self.root/'other.json';t=q.with_name(q.name+'.tmp');t.write_text('prior partial')
        with self.assertRaises(FileExistsError):c.write_new(q,{'ok':True})
        self.assertEqual(t.read_text(),'prior partial');self.assertFalse(q.exists())
    def test_source_snapshot_exact_changed_file_and_ast_only(self):
        old,new=self.root/'original',self.root/'copy';proto=self.root/'overlap_reuse.py'
        old_policy=b'def choose_contexts():\n return 1\ndef other():\n return 9\n';new_policy=b'def choose_contexts():\n return 2\ndef other():\n return 9\n';proto.write_bytes(b'def choose_contexts():\n return 2\n')
        for r,policy in ((old,old_policy),(new,new_policy)):
            (r/'tools/catalog').mkdir(parents=True);(r/'tools/catalog/selection_policy.py').write_bytes(policy);(r/'tools/catalog/other.py').write_bytes(b'pass\n')
        osh=hashlib.sha256(old_policy).hexdigest();nsh=hashlib.sha256(new_policy).hexdigest();psh=c.file_sha(proto);other=c.file_sha(old/'tools/catalog/other.py')
        prep={'originalSourceSha256':{'tools/catalog/selection_policy.py':osh,'tools/catalog/other.py':other},'candidatePolicySha256':nsh,'prototypeModuleSha256':psh}
        op={'pipelineSha256':{'selection_policy.py':osh,'other.py':other}};cp={'pipelineSha256':{'selection_policy.py':nsh,'other.py':other}}
        with patch.multiple(c,ORIGINAL_POLICY=osh,CANDIDATE_POLICY=nsh,PROTOTYPE=psh):
            pins=c.source_snapshot(old,new,prep,proto,op,cp);self.assertEqual(len(pins),5)
            (new/'tools/catalog/other.py').write_text('changed\n')
            with self.assertRaisesRegex(ValueError,'source/data change'):c.source_snapshot(old,new,prep,proto,op,cp)
    def test_stage_cpu_wall_rss_and_call_counts(self):
        metrics={'calls':2,'failedCalls':0,'elapsedSeconds':4.,'processCpuSeconds':3.,'exclusiveElapsedSeconds':4.,'exclusiveProcessCpuSeconds':3.,'sampledPeakRssBytes':123,'rssAttempts':7,'rssSamples':7}
        def profile(m):return {'runs':[{'stageMetrics':{'stages':{'pair_evidence':m},'timingSemantics':'timing','rssSemantics':'sampled'}}]}
        old=profile(metrics);new=copy.deepcopy(old);new['runs'][0]['stageMetrics']['stages']['pair_evidence']['elapsedSeconds']=2.
        self.assertEqual(c.stage_comparison(old,new)['stages'][0]['candidateOverControlElapsedRatio'],.5)
        new['runs'][0]['stageMetrics']['stages']['pair_evidence']['calls']=3
        with self.assertRaisesRegex(ValueError,'call count'):c.stage_comparison(old,new)

if __name__=='__main__':unittest.main(verbosity=2)
