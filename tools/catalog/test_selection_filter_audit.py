import gzip
from pathlib import Path
import tempfile
import unittest
import selection_filter_audit as a
from build_catalogue_v2 import phrase_choices

class FilterAuditTests(unittest.TestCase):
    def test_rank_boundaries(self):
        for rank,reason,level in [(60,'function-top60','beginner'),(61,'eligible','beginner'),(1500,'eligible','beginner'),(1501,'eligible','middle'),(5000,'eligible','middle'),(5001,'eligible','advanced')]:
            choices,trace,counts,_=a.token_trace('SENTINEL',{'sentinel':rank},'pl')
            self.assertEqual(trace[0]['firstTokenReason'],reason);self.assertEqual(trace[0]['level'],level)
    def test_row_first_reason_does_not_merge_token_reasons(self):
        self.assertEqual(a.row_gate('X','', 'pl',[],[]),'source-short')
        self.assertEqual(a.row_gate('X'*12,'', 'pl',[],[]),'meaning-empty')
        self.assertEqual(a.row_gate('X'*12,'Y', 'pl',[],[]),'no-usable-target')
    def test_utf16_max_not_codepoint_max(self):
        self.assertEqual(a.row_gate('X'*299+'\U0001f600','Y','pl',[1],[]),'source-long-utf16')
        self.assertEqual(a.row_gate('X'*12,'Y'*159+'\U0001f600','pl',[1],[]),'meaning-long-utf16')
    def test_minimum_is_scalar(self):
        self.assertEqual(a.row_gate('X'*10+'\U0001f600','Y','pl',[1],[]),'source-short')
    def test_lower_and_canonical_repeats(self):
        choices,trace,_,_=a.token_trace('ALPHA alpha',{'alpha':70},'pl');self.assertEqual(choices,[])
        choices,trace,_,rej=a.token_trace('Straße STRASSE',{'straße':70,'strasse':71},'pl');self.assertEqual(choices,[]);self.assertEqual(rej['canonical-target-ambiguous-deferred'],2)
    def test_no_unknown_rank_invention(self):
        choices,trace,_,_=a.token_trace('SENTINEL',{},'pl');self.assertEqual(choices,[]);self.assertIsNone(trace[0]['rank'])
    def test_diagnostic_contains_no_surface(self):
        _,trace,_,_=a.token_trace('SENTINEL',{'sentinel':70},'pl');self.assertNotIn('surface',trace[0]);self.assertEqual(len(trace[0]['surfaceSha256']),64)
    def test_unsafe_borrowed_variant_detected(self):
        text='ALPHA \u0130STANBUL OMEGA';variant=text.lower();ranks={'alpha':70,'i\u0307stanbul':71,'omega':72}
        borrowed=phrase_choices(text,ranks,'pl',60);p=a.reused_analysis_problem(variant,borrowed,3,'pl')
        self.assertTrue(p['tokenCountMismatch']);self.assertEqual(p['borrowedChoicesWithoutUniqueCanonicalToken'],1)
    def test_ordinary_case_variant_is_safe(self):
        borrowed=phrase_choices('ALPHA OMEGA',{'alpha':70,'omega':71},'pl',60);p=a.reused_analysis_problem('alpha omega',borrowed,2,'pl')
        self.assertFalse(p['tokenCountMismatch']);self.assertEqual(p['borrowedChoicesWithoutUniqueCanonicalToken'],0)
    def test_input_hash_and_logical_limit(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'fixture.gz';p.write_bytes(gzip.compress(b'SYNTHETIC'))
            with self.assertRaisesRegex(ValueError,'hash'):a.read_pinned(p,'0'*64,100)
            with self.assertRaisesRegex(ValueError,'logical-size'):a.read_pinned(p,a.sha(p.read_bytes()),2)
if __name__=='__main__':unittest.main()
