#!/usr/bin/env python3
"""Fail CI before damaged human-maintained text can enter an APK or release."""
from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {
    ".kt", ".kts", ".md", ".txt", ".yml", ".yaml", ".json", ".jsonl",
    ".py", ".sh", ".xml", ".properties", ".toml", ".csv",
}
TEXT_NAMES = {".editorconfig", ".gitattributes", ".gitignore", "gradlew"}
SKIP_PARTS = {".git", ".gradle", "build", "out", "node_modules", "__pycache__"}
FORBIDDEN_FORMATS = {
    "\u200b": "zero-width space",
    "\u200c": "zero-width non-joiner",
    "\u200d": "zero-width joiner",
    "\u2060": "word joiner",
    "\u202a": "left-to-right embedding",
    "\u202b": "right-to-left embedding",
    "\u202c": "pop directional formatting",
    "\u202d": "left-to-right override",
    "\u202e": "right-to-left override",
    "\u2066": "left-to-right isolate",
    "\u2067": "right-to-left isolate",
    "\u2068": "first-strong isolate",
    "\u2069": "pop directional isolate",
    "\ufeff": "byte-order mark",
}
# These fragments are the visible result of UTF-8 punctuation/Latin letters
# being decoded as Windows-1252 and then saved again. They are specific enough
# not to reject ordinary Spanish, French, German, Polish or Russian text.
MOJIBAKE = (
    "\u00c3\u00a1", "\u00c3\u00a2", "\u00c3\u00a4", "\u00c3\u00a7",
    "\u00c3\u00a9", "\u00c3\u00a8", "\u00c3\u00aa", "\u00c3\u00ad",
    "\u00c3\u00b1", "\u00c3\u00b3", "\u00c3\u00b6", "\u00c3\u00ba",
    "\u00c3\u00bc", "\u00c3\u009f", "\u00c2\u00b7", "\u00c2\u00ab",
    "\u00c2\u00bb", "\u00c2\u00a0", "\u00e2\u20ac\u201d", "\u00e2\u20ac\u201c",
    "\u00e2\u20ac\u00a6", "\u00e2\u20ac\u2122", "\u00e2\u20ac\u0153",
    "\u00e2\u20ac\u009d", "\u00ef\u00bb\u00bf",
)
MAP_KEY = re.compile(r'"([^"]+)"\s+to\s+')
S_CALL = re.compile(r'S\.t\("([^"]+)"\)')


def is_noncharacter(code: int) -> bool:
    return 0xFDD0 <= code <= 0xFDEF or (code & 0xFFFF) in (0xFFFE, 0xFFFF)


def human_text_files() -> list[Path]:
    found = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in SKIP_PARTS for part in path.parts):
            continue
        if path.suffix.lower() in TEXT_SUFFIXES or path.name in TEXT_NAMES:
            found.append(path)
    return sorted(found)


def inspect_text(path: Path, problems: list[str]) -> str | None:
    relative = path.relative_to(ROOT)
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as error:
        problems.append(f"{relative}: invalid UTF-8 ({error})")
        return None
    if unicodedata.normalize("NFC", text) != text:
        problems.append(f"{relative}: text is not normalized as Unicode NFC")
    for number, line in enumerate(text.splitlines(), start=1):
        if "\ufffd" in line:
            problems.append(f"{relative}:{number}: U+FFFD replacement character")
        for marker, name in FORBIDDEN_FORMATS.items():
            if marker in line:
                problems.append(f"{relative}:{number}: hidden Unicode {name}")
        for marker in MOJIBAKE:
            if marker in line:
                problems.append(f"{relative}:{number}: likely mojibake fragment {marker!r}")
        for char in line:
            code = ord(char)
            if is_noncharacter(code) or 0xD800 <= code <= 0xDFFF:
                problems.append(f"{relative}:{number}: forbidden Unicode U+{code:04X}")
                break
            if unicodedata.category(char) == "Cc" and char not in "\t":
                problems.append(f"{relative}:{number}: control character U+{code:04X}")
                break
    return text


def inspect_localizations(texts: dict[Path, str], problems: list[str]) -> None:
    folder = ROOT / "app/src/main/java/dev/ikna/ui/text"
    tables = sorted(folder.glob("Strings??.kt"))
    key_sets: dict[str, set[str]] = {}
    for path in tables:
        text = texts.get(path)
        if text is None:
            continue
        keys = MAP_KEY.findall(text)
        duplicates = sorted(k for k, count in Counter(keys).items() if count > 1)
        if duplicates:
            problems.append(f"{path.relative_to(ROOT)}: duplicate keys: {', '.join(duplicates)}")
        key_sets[path.stem] = set(keys)
    reference = key_sets.get("StringsRu")
    if reference is None:
        problems.append("localization: StringsRu.kt is missing")
        return
    for name, keys in key_sets.items():
        missing = sorted(reference - keys)
        extra = sorted(keys - reference)
        if missing or extra:
            problems.append(
                f"localization {name}: missing={missing[:12]} extra={extra[:12]}"
            )
    used: set[str] = set()
    source = ROOT / "app/src/main/java"
    for path in source.rglob("*.kt"):
        text = texts.get(path)
        if text is not None:
            used.update(S_CALL.findall(text))
    missing_calls = sorted(used - reference)
    if missing_calls:
        problems.append(f"localization: undefined S.t keys: {', '.join(missing_calls)}")


def inspect_structured(path: Path, text: str, problems: list[str]) -> None:
    relative = path.relative_to(ROOT)
    try:
        if path.suffix.lower() in {".json", ".jsonl"}:
            if path.suffix.lower() == ".jsonl":
                for number, line in enumerate(text.splitlines(), 1):
                    if line.strip():
                        json.loads(line)
            else:
                json.loads(text)
        elif path.suffix.lower() == ".xml":
            ET.fromstring(text)
    except Exception as error:
        problems.append(f"{relative}: malformed {path.suffix.lower()} ({error})")


def main() -> int:
    problems: list[str] = []
    texts: dict[Path, str] = {}
    files = human_text_files()
    for path in files:
        text = inspect_text(path, problems)
        if text is not None:
            texts[path] = text
            inspect_structured(path, text, problems)
    inspect_localizations(texts, problems)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    table_count = len(list((ROOT / "app/src/main/java/dev/ikna/ui/text").glob("Strings??.kt")))
    print(
        f"Repository text is valid UTF-8/NFC; {len(files)} text files and "
        f"{table_count} localization tables passed."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
