#!/usr/bin/env python3
"""Real ICU and production catalogue regression tests on synthetic data only."""
from collections import Counter
import contextlib
import ctypes.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import build_catalog as catalog
import segmentation as segment

class SegmentationTests(unittest.TestCase):
    def setUp(self):
        self.saved = catalog.FUNCTION_TOP
    def tearDown(self):
        catalog.FUNCTION_TOP = self.saved

    def test_chinese_dictionary_words_not_characters(self):
        self.assertEqual(['我', '喜欢', '学习', '中文'], catalog.words('我喜欢学习中文。', 'zh'))
    def test_japanese_dictionary_words_and_particles(self):
        words = catalog.words('私は日本語を勉強しています。', 'ja')
        for word in ['日本語', '勉強', 'を']:
            self.assertIn(word, words)
        self.assertNotIn('。', words)
    def test_korean_eojeol_not_syllables(self):
        self.assertEqual(['저는', '한국어를', '배웁니다'], catalog.words('저는 한국어를 배웁니다.', 'ko'))
    def test_existing_languages_keep_their_tokenizer(self):
        text = "L'été don't well-known śniadaniem слово 123_test"
        for lang in ['en', 'ru', 'pl', 'es', 'fr', 'de', 'it', 'pt']:
            self.assertEqual([m.group(0) for m in segment.WORD.finditer(text)], catalog.words(text, lang))
    def test_non_cjk_does_not_load_icu(self):
        with patch.object(segment, '_engine', side_effect=AssertionError('unexpected ICU')):
            catalog.words('Read this sentence.', 'en')
            segment.prepare(['en', 'ru'])
    def test_missing_icu_is_not_a_character_fallback(self):
        with patch.object(ctypes.util, 'find_library', return_value=None):
            with self.assertRaises(segment.IcuUnavailable):
                segment._IcuWords()
    def test_utf16_after_emoji_and_supplementary_han(self):
        text = '𠀀🙂我喜欢学习中文。'
        self.assertEqual((9, 11), catalog.offsets(text, '中文', 'zh'))
        for span in segment.word_spans(text, 'zh'):
            self.assertEqual(span.surface, text[span.start:span.end])
            self.assertEqual(span.surface, segment.utf16_slice(text,
                segment.utf16_offset(text, span.start), segment.utf16_offset(text, span.end)))
    def test_split_surrogates_are_rejected(self):
        with self.assertRaises(UnicodeDecodeError):
            segment.utf16_slice('🙂中文', 1, 2)
    def test_target_is_not_an_earlier_substring(self):
        text = '大学生喜欢大学。'
        exact = [s for s in segment.word_spans(text, 'zh') if s.surface == '大学']
        self.assertEqual(1, len(exact))
        self.assertGreater(exact[0].start, text.find('大学'))
        self.assertEqual((exact[0].start, exact[0].end), catalog.offsets(text, '大学', 'zh'))
        self.assertEqual((17, 21), catalog.offsets('theatre near the shop theatre', 'shop', 'en'))
    def test_duplicates_are_not_ambiguous_targets(self):
        catalog.FUNCTION_TOP = 0
        self.assertIsNone(catalog.offsets('中文和中文。', '中文', 'zh'))
        chosen = catalog.pick_phrase('中文和中文。', {'中文': 100, '和': 1}, {}, set(), 'zh')
        self.assertNotEqual('中文', chosen[0] if chosen else None)
    def test_single_cjk_word_but_not_latin_letter(self):
        catalog.FUNCTION_TOP = 0
        self.assertEqual('本', catalog.pick_phrase('これは本です。', {'本': 100}, {}, set(), 'ja')[0])
        self.assertIsNone(catalog.pick_phrase('I am here.', {'i': 100}, {}, set(), 'en'))
    def test_frequencies_and_tokens_share_boundaries(self):
        catalog.FUNCTION_TOP = 1
        text = '我喜欢学习中文。'
        ranks = catalog.frequency([text], 'zh')
        tokens = catalog.token_list(text, ranks, {}, 'zh')
        self.assertEqual(catalog.words(text, 'zh'), [t['surface'] for t in tokens])
        self.assertEqual(set(ranks), {t['surface'].lower() for t in tokens})
        self.assertTrue(any(t['isContent'] for t in tokens))
    def test_limits_are_utf16_like_the_app(self):
        catalog.FUNCTION_TOP = 0
        text = '🙂' * 149 + '中文。'
        cards = catalog.build_pair('zh', 'en', {'zh': {'1': text}, 'en': {'2': 'A direct translation.'}},
            {'1': ['2']}, {'中文': 100}, {}, set(), Counter(), False)
        self.assertEqual([], cards)
    def test_direct_links_and_cc0_filter_remain(self):
        catalog.FUNCTION_TOP = 0
        data = {'zh': {'1': '我今天学习中文。'}, 'en': {'3': 'I learn Chinese today.'}}
        self.assertEqual([], catalog.build_pair('zh', 'en', data, {'1': ['2'], '2': ['3']},
            {'中文': 100}, {}, set(), Counter(), False))
        self.assertEqual([], catalog.build_pair('zh', 'en', data, {'1': ['3']},
            {'中文': 100}, {}, {'1'}, Counter(), True))
    def test_utf16_fixtures_for_the_kotlin_reader(self):
        for line in (Path(__file__).parent/'fixtures/cjk-offsets.jsonl').read_text(encoding='utf-8').splitlines():
            card = json.loads(line)
            self.assertTrue(card['synthetic'])
            self.assertEqual(card['text'], segment.utf16_slice(card['context'], card['targetStart'], card['targetEnd']))
    def test_pipeline_builds_all_three_cjk_languages(self):
        nouns = [('苹果','果物','사과','fruit'),('图书馆','図書館','도서관','library'),
            ('公园','公園','공원','park'),('学校','学校','학교','school'),
            ('音乐','音楽','음악','music'),('电影','映画','영화','film'),
            ('城市','都市','도시','city'),('家庭','家族','가족','family'),
            ('新闻','新聞','신문','newspaper'),('火车','電車','기차','train'),
            ('料理','料理','요리','cooking'),('医生','医者','의사','doctor')]
        with tempfile.TemporaryDirectory(prefix='ikna-synthetic-cjk-') as folder:
            base = Path(folder);rows=[];links=[]
            for i,(zh,ja,ko,en) in enumerate(nouns):
                sid=1000+i*4
                texts=[('eng',f'Today we talk about {en} together.'),('cmn',f'我今天想了解{zh}的事情。'),
                    ('jpn',f'今日は{ja}について話します。'),('kor',f'오늘 우리는 {ko} 이야기를 합니다.')]
                for n,(lang,text) in enumerate(texts):
                    rows.append(f'{sid+n}\t{lang}\t{text}\n')
                    if n: links += [f'{sid}\t{sid+n}\n',f'{sid+n}\t{sid}\n']
            (base/'sentences.csv').write_text(''.join(rows),encoding='utf-8')
            (base/'links.csv').write_text(''.join(links),encoding='utf-8')
            with contextlib.redirect_stdout(io.StringIO()):
                result=catalog.main(['--tatoeba',folder,'--out',str(base/'out'),
                    '--learn','zh,ja,ko','--meanings','en','--function-top','4','--min-deck','5'])
            self.assertEqual(0,result)
            index=json.loads((base/'out/index.json').read_text())
            self.assertEqual({'zh','ja','ko'},{d['lang'] for d in index['decks']})
            self.assertEqual('UTF-16',index['segmentation']['offsets'])
            self.assertIn('icuVersion',index['segmentation'])
            for deck in index['decks']:
                self.assertEqual('CC BY 2.0 FR',deck['licence'])
                cards=(base/'out'/deck['file']).read_text(encoding='utf-8').splitlines()
                self.assertGreaterEqual(len(cards),5)
                for line in cards:
                    card=json.loads(line)
                    self.assertEqual(card['text'],segment.utf16_slice(card['context'],card['targetStart'],card['targetEnd']))
                    self.assertIn(card['text'],[t['surface'] for t in card['tokens']])
                    self.assertIn('Tatoeba #',card['translation'])

if __name__ == '__main__':
    unittest.main(verbosity=2)
