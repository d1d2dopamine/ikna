#!/usr/bin/env python3
"""Verify the shared language registry and every interface catalogue."""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
TEXT = ROOT / "shared/src/jvmShared/kotlin/dev/ikna/ui/text"
REGISTRY = TEXT / "UiLanguages.kt"
CATALOG_ENTRY = re.compile(r'"([^"\\]+)"\s+to\s+"((?:\\.|[^"\\])*)"')
CONSTANT = re.compile(r'const val (LANG_[A-Z]+)\s*=\s*"([a-z]+)"')
REGISTRY_ENTRY = re.compile(
    r'UiLanguage\(\s*(LANG_[A-Z]+),\s*"((?:\\.|[^"\\])*)",\s*'
    r'(STRINGS_[A-Z]+),\s*QuantityRule\.(SLAVIC|ONE_OTHER)\s*\)'
)
TOKEN = re.compile(r'\{[A-Za-z][A-Za-z0-9_]*\}')
REQUIRED_CODES = {"ru", "en", "pl", "es", "fr", "de", "pt"}


def duplicate_values(values: list[str]) -> list[str]:
    return sorted(value for value, count in Counter(values).items() if count > 1)


def catalogue_path(table: str) -> Path:
    suffix = table.removeprefix("STRINGS_").title()
    return TEXT / f"Strings{suffix}.kt"


def main() -> int:
    problems: list[str] = []
    strings_source = (TEXT / "Strings.kt").read_text(encoding="utf-8")
    constants = dict(CONSTANT.findall(strings_source))
    registry_source = REGISTRY.read_text(encoding="utf-8")
    raw_entries = REGISTRY_ENTRY.findall(registry_source)

    if not raw_entries:
        problems.append("UiLanguages.kt: registry contains no parseable UiLanguage entries")
        entries: list[tuple[str, str, str, str]] = []
    else:
        entries = []
        for constant, label, table, rule in raw_entries:
            code = constants.get(constant)
            if code is None:
                problems.append(f"UiLanguages.kt: unknown code constant {constant}")
                continue
            entries.append((code, label, table, rule))

    codes = [entry[0] for entry in entries]
    labels = [entry[1] for entry in entries]
    tables = [entry[2] for entry in entries]
    if duplicate_values(codes):
        problems.append(f"UiLanguages.kt: duplicate language codes {duplicate_values(codes)}")
    if duplicate_values(tables):
        problems.append(f"UiLanguages.kt: duplicate catalogue tables {duplicate_values(tables)}")
    if set(codes) != REQUIRED_CODES:
        problems.append(
            f"UiLanguages.kt: language codes differ; missing={sorted(REQUIRED_CODES - set(codes))} "
            f"extra={sorted(set(codes) - REQUIRED_CODES)}"
        )
    if any(not label.strip() for label in labels):
        problems.append("UiLanguages.kt: every language needs a nonblank native label")
    if ("pt", "PORTUGUÊS (BRASIL)") not in [(code, label) for code, label, _, _ in entries]:
        problems.append("UiLanguages.kt: pt must be named PORTUGUÊS (BRASIL)")

    expected_paths = {catalogue_path(table) for table in tables}
    actual_paths = set(TEXT.glob("Strings??.kt"))
    if expected_paths != actual_paths:
        missing = sorted(str(path.relative_to(ROOT)) for path in expected_paths - actual_paths)
        extra = sorted(str(path.relative_to(ROOT)) for path in actual_paths - expected_paths)
        problems.append(f"catalogue files differ from registry; missing={missing} extra={extra}")

    catalogues: dict[str, dict[str, str]] = {}
    for code, _, table, _ in entries:
        path = catalogue_path(table)
        if not path.is_file():
            continue
        source = path.read_text(encoding="utf-8")
        if re.search(rf'\binternal\s+val\s+{re.escape(table)}\b', source):
            problems.append(f"{path.relative_to(ROOT)}: {table} must be public across Gradle modules")
        pairs = CATALOG_ENTRY.findall(source)
        keys = [key for key, _ in pairs]
        duplicates = duplicate_values(keys)
        if duplicates:
            problems.append(f"{path.relative_to(ROOT)}: duplicate keys {duplicates[:12]}")
        blank = sorted(key for key, value in pairs if not value.strip())
        if blank:
            problems.append(f"{path.relative_to(ROOT)}: blank values {blank[:12]}")
        catalogues[code] = dict(pairs)

    reference = catalogues.get("en")
    if reference is None:
        problems.append("catalogue: English fallback is missing")
    else:
        for code, catalogue in sorted(catalogues.items()):
            missing = sorted(reference.keys() - catalogue.keys())
            extra = sorted(catalogue.keys() - reference.keys())
            if missing or extra:
                problems.append(f"catalogue {code}: missing={missing[:12]} extra={extra[:12]}")
            for key in sorted(reference.keys() & catalogue.keys()):
                expected_tokens = Counter(TOKEN.findall(reference[key]))
                actual_tokens = Counter(TOKEN.findall(catalogue[key]))
                if expected_tokens != actual_tokens:
                    problems.append(
                        f"catalogue {code} key {key}: tokens {dict(actual_tokens)} "
                        f"do not match English {dict(expected_tokens)}"
                    )

    for required in [
        "return uiLanguage(raw)?.code ?: LANG_EN",
        "val table = uiLanguage(lang)?.strings ?: STRINGS_EN",
    ]:
        if required not in strings_source:
            problems.append(f"Strings.kt: missing registry/fallback contract {required!r}")
    quantity = (TEXT / "QuantityText.kt").read_text(encoding="utf-8")
    if "uiLanguage(S.lang)?.quantityRule != QuantityRule.SLAVIC" not in quantity:
        problems.append("QuantityText.kt: quantity rules are not read from the language registry")

    selectors = [
        ROOT / "app/src/main/java/dev/ikna/ui/settings/SettingsScreen.kt",
        ROOT / "desktop/src/main/kotlin/dev/ikna/desktop/SettingsPane.kt",
    ]
    for path in selectors:
        source = path.read_text(encoding="utf-8")
        for required in ["UI_LANGUAGES.map { it.code }", "uiLanguageLabel(code)"]:
            if required not in source:
                problems.append(f"{path.relative_to(ROOT)}: selector does not use {required}")

    if reference is not None:
        for key in ["a11y.015", "progress.001", "progress.002", "browse.021", "inspector.002"]:
            if key not in reference:
                problems.append(f"catalogue: missing new UI contract key {key}")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    key_count = len(reference or {})
    print(
        f"Language registry OK: {len(entries)} languages, {key_count} keys each; "
        "English fallback, tokens, labels and selectors agree."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
