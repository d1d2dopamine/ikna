"""Offline ICU CJK boundaries. WordSpan uses Python indices; pack spans use UTF-16.

ICU is loaded only by catalogue tooling. Missing dictionaries are an error, not
character splitting. Korean uses written eojeol boundaries, not morphological
stemming. No ICU library or dictionary is added to the APK.
"""
import ctypes
import ctypes.util
from dataclasses import dataclass
from functools import lru_cache
import re
import sys

CJK = frozenset({'zh', 'ja', 'ko'})
WORD = re.compile(r"[^\W\d_]+(?:['\u2019\-][^\W\d_]+)*", re.UNICODE)

@dataclass(frozen=True)
class WordSpan:
    surface: str
    start: int
    end: int

def utf16_length(text):
    return len(text.encode('utf-16-le')) // 2

def utf16_offset(text, index):
    if not 0 <= index <= len(text):
        raise ValueError('code-point offset outside the sentence')
    return utf16_length(text[:index])

def utf16_slice(text, start, end):
    data = text.encode('utf-16-le')
    if not 0 <= start <= end <= len(data) // 2:
        raise ValueError('UTF-16 range outside the sentence')
    return data[start * 2:end * 2].decode('utf-16-le')

class IcuUnavailable(RuntimeError):
    pass

class _IcuWords:
    def __init__(self):
        library = ctypes.util.find_library('icui18n')
        if not library:
            raise IcuUnavailable('CJK segmentation needs ICU. On Ubuntu: sudo apt-get install libicu-dev')
        try:
            self.library = ctypes.CDLL(library)
            versions = re.findall(r'(?:\.so\.|\.)(\d+)', library)
            suffixes = [''] + ['_' + value for value in versions]
            self.suffix = next(s for s in suffixes if hasattr(self.library, 'ubrk_open' + s))
            ptr, i32 = ctypes.c_void_p, ctypes.c_int32
            u16 = ctypes.POINTER(ctypes.c_uint16)
            self.open = self._function('ubrk_open', ptr, [ctypes.c_int, ctypes.c_char_p, u16, i32, ctypes.POINTER(i32)])
            self.first = self._function('ubrk_first', i32, [ptr])
            self.next = self._function('ubrk_next', i32, [ptr])
            self.status = self._function('ubrk_getRuleStatus', i32, [ptr])
            self.close = self._function('ubrk_close', None, [ptr])
            version = (ctypes.c_uint8 * 4)()
            self._function('u_getVersion', None, [ctypes.POINTER(ctypes.c_uint8)])(version)
            self.version = '.'.join(str(n) for n in list(version)[:2])
        except (OSError, AttributeError, StopIteration) as error:
            raise IcuUnavailable('Could not load ICU word-break API from ' + library) from error

    def _function(self, name, result, args):
        fn = getattr(self.library, name + self.suffix)
        fn.restype, fn.argtypes = result, args
        return fn

    def spans(self, text, lang):
        raw = text.encode('utf-16-le' if sys.byteorder == 'little' else 'utf-16-be')
        length = len(raw) // 2
        if length == 0:
            return ()
        if length > 2 ** 31 - 1:
            raise ValueError("sentence exceeds ICU's index range")
        buffer = (ctypes.c_uint16 * length).from_buffer_copy(raw)
        status = ctypes.c_int32(0)
        iterator = self.open(1, lang.encode('ascii'), buffer, length, ctypes.byref(status))
        # Negative codes are success-with-warning; locale fallback is normal.
        if not iterator or status.value > 0:
            if iterator:
                self.close(iterator)
            raise RuntimeError('ICU word iterator failed: ' + str(status.value))
        python_at = {0: 0}
        units = 0
        for index, char in enumerate(text, 1):
            units += 2 if ord(char) > 0xffff else 1
            python_at[units] = index
        result = []
        try:
            start = self.first(iterator)
            while True:
                end = self.next(iterator)
                if end == -1:
                    break
                if not 0 <= start < end <= length or start not in python_at or end not in python_at:
                    raise RuntimeError('ICU returned a non-Unicode word boundary')
                # LETTER/KANA/IDEO, excluding punctuation and pure numbers.
                if self.status(iterator) >= 200:
                    a, b = python_at[start], python_at[end]
                    result.append(WordSpan(text[a:b], a, b))
                start = end
        finally:
            self.close(iterator)
        return tuple(result)

_ENGINE = None

def _engine():
    global _ENGINE
    if _ENGINE is None:
        _ENGINE = _IcuWords()
    return _ENGINE

@lru_cache(maxsize=8192)
def _cjk_spans(text, lang):
    return _engine().spans(text, lang)

def word_spans(text, lang=None):
    if lang in CJK:
        return _cjk_spans(text, lang)
    return tuple(WordSpan(m.group(0), m.start(), m.end()) for m in WORD.finditer(text))

def prepare(languages):
    wanted = sorted(set(languages) & CJK)
    if not wanted:
        return {'engine': 'legacy-unicode-word-regex', 'offsets': 'UTF-16'}
    engine = _engine()
    probes = {'zh': '我喜欢中文。', 'ja': '私は日本語を学びます。', 'ko': '저는 한국어를 배웁니다.'}
    expected = {"zh": {"喜欢", "中文"}, "ja": {"日本語"}, "ko": {"한국어를"}}
    for lang in wanted:
        surfaces = {span.surface for span in word_spans(probes[lang], lang)}
        if not expected[lang].issubset(surfaces):
            raise IcuUnavailable("ICU dictionary word segmentation is unavailable for " + lang)
    return {'engine': 'ICU word break', 'icuVersion': engine.version,
            'languages': wanted, 'offsets': 'UTF-16', 'koreanUnit': 'eojeol'}
