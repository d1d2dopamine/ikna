#!/usr/bin/env python3
"""Turn a Catalogue v2 census into a release-readiness audit.

The expensive catalogue scan belongs to meta_info.py.  This tool consumes that
machine-readable census, classifies objective blockers separately from warnings,
and produces a compact report suitable for GitHub Actions and release review.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


RAW_CLIENT_CAP_BYTES = 24 * 1024 * 1024


def pct(part: int, whole: int) -> str:
    if not whole:
        return "0.0%"
    return f"{100.0 * part / whole:.1f}%"


def mib(value: int) -> str:
    return f"{value / 1048576:.1f} MiB"


def sum_values(mapping: dict[str, Any] | None) -> int:
    return sum(int(value) for value in (mapping or {}).values())


def add_check(checks: list[dict[str, str]], name: str, status: str, detail: str) -> None:
    checks.append({"name": name, "status": status, "detail": detail})


def evaluate(meta: dict[str, Any], samples_dir: Path | None = None) -> dict[str, Any]:
    catalogue = meta.get("catalogue") or {}
    metadata = meta.get("metadata") or {}
    inventory = meta.get("inventory") or {}
    input_info = meta.get("input") or {}
    targets = meta.get("targets") or {}
    build = meta.get("build") or {}
    decks = inventory.get("decks") or []

    blockers: list[str] = []
    warnings: list[str] = []
    information: list[str] = []
    checks: list[dict[str, str]] = []

    parse_errors = int(catalogue.get("parseErrors") or 0)
    duplicate_ids = int(catalogue.get("duplicateCardIds") or 0)
    bad_offsets = int(metadata.get("badTargetOffsets") or 0)
    missing_card_fields = sum_values(metadata.get("missingRequiredCardFields"))
    missing_context_fields = sum_values(metadata.get("missingRequiredContextFields"))
    missing_token_fields = sum_values(metadata.get("missingRequiredTokenFields"))
    duplicate_target_context = int(catalogue.get("duplicateTargetContextPairsWithinDecks") or 0)
    deck_mismatches = int(catalogue.get("deckDeclarationMismatches") or 0)
    missing_assets = len(input_info.get("missingIndexAssets") or [])
    unindexed_assets = len(input_info.get("unindexedAssets") or [])

    integrity_problems = {
        "JSON parse errors": parse_errors,
        "duplicate card IDs": duplicate_ids,
        "bad target offsets": bad_offsets,
        "missing required card fields": missing_card_fields,
        "missing required context fields": missing_context_fields,
        "missing required token fields": missing_token_fields,
        "targetId identity mismatches": int(metadata.get("targetIdMismatches") or 0),
        "contextId namespace mismatches": int(metadata.get("contextIdNamespaceMismatches") or 0),
        "meaningId namespace mismatches": int(metadata.get("meaningIdNamespaceMismatches") or 0),
        "duplicate target+context pairs inside a deck": duplicate_target_context,
        "deck/index declaration mismatches": deck_mismatches,
        "indexed assets missing on disk": missing_assets,
        "unindexed deck assets": unindexed_assets,
    }
    nonzero_integrity = [f"{name}: {count:,}" for name, count in integrity_problems.items() if count]
    if nonzero_integrity:
        detail = "; ".join(nonzero_integrity)
        blockers.append("Integrity: " + detail)
        add_check(checks, "Integrity", "FAIL", detail)
    else:
        add_check(checks, "Integrity", "PASS", "No structural/index/card integrity errors were found.")

    provenance_missing = int(metadata.get("provenanceMissing") or 0)
    if provenance_missing:
        detail = f"{provenance_missing:,} retained contexts have no auditable source identity."
        blockers.append("Provenance: " + detail)
        add_check(checks, "Provenance", "FAIL", detail)
    else:
        add_check(checks, "Provenance", "PASS", "Every retained context has an explicit or recoverable source identity.")

    catalogue_version = int(input_info.get("catalogueVersion") or input_info.get("indexVersion") or 0)
    if catalogue_version >= 2:
        if input_info.get("buildVerified"):
            add_check(checks, "BUILD cross-check", "PASS", "Census totals match BUILD.json for this exact Catalogue v2 build.")
        else:
            detail = "Catalogue v2 was inspected without a verified BUILD.json from the same build."
            warnings.append(detail)
            add_check(checks, "BUILD cross-check", "WARN", detail)
    else:
        add_check(checks, "BUILD cross-check", "PASS", "Not required for legacy Catalogue v1 inspection.")

    raw_cap = int(inventory.get("rawClientCapBytes") or RAW_CLIENT_CAP_BYTES)
    over_cap = [row for row in decks if int(row.get("rawBytes") or 0) > raw_cap]
    near_cap = [
        row for row in decks
        if int(row.get("rawBytes") or 0) <= raw_cap
        and int(row.get("rawBytes") or 0) >= int(raw_cap * 0.9)
    ]
    if over_cap:
        names = ", ".join(f"{row.get('id')} ({mib(int(row.get('rawBytes') or 0))})" for row in over_cap[:8])
        if len(over_cap) > 8:
            names += f", ... +{len(over_cap) - 8}"
        detail = f"{len(over_cap):,} deck(s) exceed the {mib(raw_cap)} raw client cap: {names}"
        blockers.append("Storage: " + detail)
        add_check(checks, "Storage", "FAIL", detail)
    elif near_cap:
        detail = f"{len(near_cap):,} deck(s) are at or above 90% of the {mib(raw_cap)} raw client cap."
        warnings.append(detail)
        add_check(checks, "Storage", "WARN", detail)
    else:
        add_check(checks, "Storage", "PASS", f"No deck is within the final 10% of the {mib(raw_cap)} raw client cap.")

    count_stats = inventory.get("cardCount") or {}
    thin = int(count_stats.get("below1000") or 0)
    tiny = int(count_stats.get("below100") or 0)
    max_deck_targets = int(inventory.get("maxDeckTargets") or (build.get("limits") or {}).get("maxDeckTargets") or 0)
    at_cap = int(inventory.get("decksAtTargetCap") or 0)
    if thin:
        thin_rows = sorted((row for row in decks if int(row.get("cards") or 0) < 1000), key=lambda row: (int(row.get("cards") or 0), str(row.get("id") or "")))
        names = ", ".join(f"{row.get('id')} ({int(row.get('cards') or 0):,})" for row in thin_rows[:8])
        if len(thin_rows) > 8:
            names += f", ... +{len(thin_rows) - 8}"
        detail = f"{thin:,} deck(s) contain fewer than 1,000 cards; {tiny:,} of them contain fewer than 100. Smallest: {names}. Thinness alone is not corruption."
        warnings.append(detail)
        add_check(checks, "Deck coverage", "WARN", detail)
    else:
        add_check(checks, "Deck coverage", "PASS", "Every generated deck contains at least 1,000 cards.")
    if max_deck_targets:
        cap_rows = sorted((row for row in decks if int(row.get("cards") or 0) >= max_deck_targets), key=lambda row: str(row.get("id") or ""))
        detail = f"{at_cap:,} deck(s) reached the configured {max_deck_targets:,}-target cap."
        if cap_rows:
            names = ", ".join(str(row.get("id")) for row in cap_rows[:12])
            if len(cap_rows) > 12:
                names += f", ... +{len(cap_rows) - 12}"
            detail += " " + names + "."
        information.append(detail)

    missing_lemma = int(metadata.get("tokensMissingLemma") or 0)
    if missing_lemma:
        detail = f"{missing_lemma:,} tokens are missing lemma metadata."
        blockers.append("Morphology/token contract: " + detail)
        add_check(checks, "Morphology", "FAIL", detail)
    else:
        informative = int(metadata.get("tokensWithNonIdentityLemma") or 0)
        tokens = int(metadata.get("tokens") or 0)
        add_check(
            checks,
            "Morphology",
            "PASS",
            f"{informative:,} of {tokens:,} tokens ({pct(informative, tokens)}) carry a lemma different from surface; identity lemmas remain valid conservative evidence.",
        )

    long_targets = int(metadata.get("targetsLongerThan80Chars") or 0)
    long_contexts = int(metadata.get("contextsLongerThan500Chars") or 0)
    empty_targets = int(metadata.get("emptyTargets") or 0)
    if empty_targets:
        detail = f"{empty_targets:,} cards have an empty normalized target."
        blockers.append("Target quality: " + detail)
        add_check(checks, "Target/context shape", "FAIL", detail)
    elif long_targets or long_contexts:
        detail = f"long targets (>80 chars): {long_targets:,}; long contexts (>500 chars): {long_contexts:,}. These are review candidates, not automatic data loss."
        warnings.append(detail)
        add_check(checks, "Target/context shape", "WARN", detail)
    else:
        add_check(checks, "Target/context shape", "PASS", "No empty targets or configured long-text review candidates were found.")

    overlap = int(targets.get("targetsAcrossMultipleLevels") or 0)
    unique = int(targets.get("uniqueExactTargets") or 0)
    information.append(f"Exact targets present across multiple levels: {overlap:,} of {unique:,} ({pct(overlap, unique)}).")

    source_rows = (meta.get("breakdown") or {}).get("bySourceFamily") or []
    if source_rows:
        source_summary = ", ".join(f"{row.get('key')}: {int(row.get('contexts') or 0):,} contexts" for row in source_rows)
        information.append("Source-family distribution: " + source_summary + ".")

    samples = {"present": False, "decks": 0, "rows": 0, "path": str(samples_dir) if samples_dir else None}
    if samples_dir:
        manifest_path = samples_dir / "manifest.json"
        if manifest_path.exists():
            try:
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                decks_manifest = manifest.get("decks") or {}
                samples = {
                    "present": True,
                    "decks": len(decks_manifest),
                    "rows": sum(int(value) for value in decks_manifest.values()),
                    "path": str(samples_dir),
                    "selection": manifest.get("selection"),
                    "perDeckLimit": manifest.get("perDeckLimit"),
                }
                if len(decks_manifest) != len(decks):
                    detail = f"Deterministic samples cover {len(decks_manifest):,} of {len(decks):,} decks."
                    warnings.append(detail)
                else:
                    information.append(f"Deterministic manual-review samples cover all {len(decks):,} decks ({samples['rows']:,} sampled cards).")
            except (OSError, ValueError, TypeError):
                warnings.append("Sample manifest exists but could not be parsed.")
        else:
            warnings.append("No deterministic sample manifest was produced for manual review.")

    overall = "FAIL" if blockers else ("WARN" if warnings else "PASS")
    return {
        "schemaVersion": 1,
        "overall": overall,
        "checks": checks,
        "blockers": blockers,
        "warnings": warnings,
        "information": information,
        "samples": samples,
        "summary": {
            "cardsInDecks": int(catalogue.get("cardsInDecks") or catalogue.get("targetDeckMemberships") or 0),
            "uniqueTargets": unique,
            "contexts": int(catalogue.get("contexts") or 0),
            "decks": len(decks),
            "compressedBytes": int(catalogue.get("deckAssetBytes") or 0),
            "rawBytes": int(catalogue.get("observedUncompressedBytes") or catalogue.get("declaredUncompressedBytes") or 0),
            "thinDecks": thin,
            "tinyDecks": tiny,
            "decksAtTargetCap": at_cap,
            "decksNearRawCap": len(near_cap),
            "decksOverRawCap": len(over_cap),
        },
    }


def markdown_report(audit: dict[str, Any], meta: dict[str, Any]) -> str:
    summary = audit["summary"]
    metadata = meta.get("metadata") or {}
    inventory = meta.get("inventory") or {}
    morphology = metadata.get("morphologyByLearningLanguage") or {}
    source_rows = (meta.get("breakdown") or {}).get("bySourceFamily") or []

    lines = [
        "# Catalogue v2 readiness",
        "",
        f"Overall: **{audit['overall']}**",
        "",
        "This report is a final Part 4.5 audit of an already-built catalogue. `FAIL` is reserved for structural/reproducibility problems; `WARN` records material that needs review but does not automatically block a release.",
        "",
        "## Snapshot",
        "",
        "| metric | value |",
        "| --- | ---: |",
        f"| cards in all decks | {summary['cardsInDecks']:,} |",
        f"| unique learning targets | {summary['uniqueTargets']:,} |",
        f"| retained contexts | {summary['contexts']:,} |",
        f"| decks | {summary['decks']:,} |",
        f"| compressed deck bytes | {mib(summary['compressedBytes'])} |",
        f"| raw JSONL bytes | {mib(summary['rawBytes'])} |",
        "",
        "## Checks",
        "",
        "| area | status | detail |",
        "| --- | :---: | --- |",
    ]
    for check in audit["checks"]:
        detail = str(check["detail"]).replace("|", "\\|")
        lines.append(f"| {check['name']} | **{check['status']}** | {detail} |")

    lines.extend(["", "## BLOCKERS", ""])
    if audit["blockers"]:
        lines.extend(f"- {item}" for item in audit["blockers"])
    else:
        lines.append("- None.")

    lines.extend(["", "## WARNINGS", ""])
    if audit["warnings"]:
        lines.extend(f"- {item}" for item in audit["warnings"])
    else:
        lines.append("- None.")

    lines.extend(["", "## INFORMATION", ""])
    if audit["information"]:
        lines.extend(f"- {item}" for item in audit["information"])
    else:
        lines.append("- None.")

    card_stats = inventory.get("cardCount") or {}
    lines.extend(
        [
            "",
            "## Deck coverage",
            "",
            "| measure | value |",
            "| --- | ---: |",
            f"| cards / deck minimum | {card_stats.get('min', 0):,} |",
            f"| cards / deck median | {card_stats.get('median', 0):,} |",
            f"| cards / deck p95 | {card_stats.get('p95', 0):,} |",
            f"| cards / deck maximum | {card_stats.get('max', 0):,} |",
            f"| decks below 1,000 cards | {summary['thinDecks']:,} |",
            f"| decks below 100 cards | {summary['tinyDecks']:,} |",
            f"| decks at configured target cap | {summary['decksAtTargetCap']:,} |",
            f"| decks at >=90% raw client cap | {summary['decksNearRawCap']:,} |",
            f"| decks over raw client cap | {summary['decksOverRawCap']:,} |",
            "",
            "## Morphology by learning language",
            "",
            "| language | tokens | non-identity lemma | share | missing lemma |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
    )
    for lang, row in morphology.items():
        tokens = int(row.get("tokens") or 0)
        informative = int(row.get("nonIdentityLemma") or 0)
        missing = int(row.get("missingLemma") or 0)
        lines.append(f"| `{lang}` | {tokens:,} | {informative:,} | {pct(informative, tokens)} | {missing:,} |")

    lines.extend(
        [
            "",
            "## Source distribution",
            "",
            "| source family | decks | cards | unique targets | contexts |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
    )
    for row in source_rows:
        lines.append(
            f"| `{row.get('key')}` | {int(row.get('decks') or 0):,} | {int(row.get('cards') or 0):,} | {int(row.get('uniqueTargets') or 0):,} | {int(row.get('contexts') or 0):,} |"
        )

    samples = audit.get("samples") or {}
    lines.extend(["", "## Manual samples", ""])
    if samples.get("present"):
        lines.extend(
            [
                f"Deterministic samples: **{int(samples.get('rows') or 0):,} cards across {int(samples.get('decks') or 0):,} decks**.",
                "",
                f"Selection rule: `{samples.get('selection') or '?'}`; per-deck limit: `{samples.get('perDeckLimit') or '?'}`.",
            ]
        )
    else:
        lines.append("No deterministic sample manifest was supplied to this audit.")

    lines.extend(
        [
            "",
            "## Part 4.5 decision rule",
            "",
            "- `FAIL`: do not freeze Catalogue v2; fix the blocker and rerun the audit.",
            "- `WARN`: inspect the named thin/long/near-cap material; warnings are not automatic release blockers.",
            "- `PASS`: the measured content foundation may be frozen and work can move to Part 5.",
            "",
        ]
    )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--meta-json", required=True, type=Path)
    parser.add_argument("--markdown", type=Path)
    parser.add_argument("--json", dest="json_out", type=Path)
    parser.add_argument("--samples-dir", type=Path)
    parser.add_argument("--no-fail", action="store_true", help="write the report but return zero even when blockers exist")
    args = parser.parse_args(argv)

    meta = json.loads(args.meta_json.read_text(encoding="utf-8"))
    audit = evaluate(meta, args.samples_dir)
    report = markdown_report(audit, meta)

    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(report, encoding="utf-8")
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not args.markdown and not args.json_out:
        print(report)

    if audit["overall"] == "FAIL" and not args.no_fail:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
