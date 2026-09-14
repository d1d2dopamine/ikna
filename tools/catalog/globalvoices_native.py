#!/usr/bin/env python3
"""Read Global Voices XCES alignments against the native OPUS XML archives.

This deliberately avoids the tempting but unsafe assumption that row N in an
OPUS Moses export is link N in the XCES file.  XCES links carry document names
and sentence ids; those ids are resolved directly against the monolingual OPUS
XML archives.  1:n and n:1 links therefore remain attached to the correct
source documents.
"""
from __future__ import annotations

import argparse
import gzip
import html
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter, OrderedDict
from pathlib import Path
from typing import Any


def _open_binary(path: str):
    return gzip.open(path, "rb") if path.endswith(".gz") else open(path, "rb")


def _open_text_write(path: str):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    if path.endswith(".gz"):
        return gzip.open(path, "wt", encoding="utf-8", newline="", compresslevel=6)
    return open(path, "w", encoding="utf-8", newline="")


def _normalise_path(value: str) -> str:
    value = value.replace("\\", "/").strip()
    while value.startswith("./"):
        value = value[2:]
    while value.startswith("../"):
        value = value[3:]
    return re.sub(r"/+", "/", value).lstrip("/")


def _sentence_id(element: ET.Element) -> str | None:
    return element.attrib.get("id") or element.attrib.get("{http://www.w3.org/XML/1998/namespace}id")


def _sentence_text(element: ET.Element) -> str:
    # Raw OPUS XML normally stores untokenised text directly under <s>.  The
    # whitespace normalisation also behaves sensibly if inline markup exists.
    return " ".join("".join(element.itertext()).split())


_XML_INVALID_CONTROLS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f]")
_SENTENCE_BLOCK = re.compile(r"<s\b(?P<attrs>[^>]*)>(?P<body>.*?)</s\s*>", re.IGNORECASE | re.DOTALL)
_SENTENCE_ID_ATTR = re.compile(r"(?:^|\s)(?:xml:)?id\s*=\s*([\"'])(?P<id>.*?)\1", re.IGNORECASE | re.DOTALL)
_TAG = re.compile(r"<[^>]+>", re.DOTALL)


def _salvage_sentences(data: bytes) -> dict[str, str]:
    """Recover sentence ids/text from a malformed OPUS raw XML document.

    Some historical Global Voices raw files are not well-formed XML.  The
    fallback is deliberately narrow: it never changes the document identity or
    invents sentence ids; it only recovers literal <s id=...> blocks from the
    same archive member.  That keeps XCES provenance exact while allowing a
    broken character/entity elsewhere in the document to be non-fatal.
    """
    text = data.decode("utf-8", errors="replace")
    text = _XML_INVALID_CONTROLS.sub("", text)
    sentences: dict[str, str] = {}
    for match in _SENTENCE_BLOCK.finditer(text):
        id_match = _SENTENCE_ID_ATTR.search(match.group("attrs"))
        if not id_match:
            continue
        sid = html.unescape(id_match.group("id")).strip()
        if not sid:
            continue
        # Raw Global Voices sentences can contain lightweight inline markup or
        # HTML-ish entities.  Remove markup and decode entities only inside the
        # already identified sentence block.
        body = _TAG.sub(" ", match.group("body"))
        body = html.unescape(body)
        body = " ".join(body.split())
        if body:
            sentences[sid] = body
    return sentences


class XmlArchive:
    """Resolve XCES document paths inside one OPUS monolingual ZIP archive."""

    def __init__(self, path: str, cache_size: int = 24):
        self.path = path
        self.archive = zipfile.ZipFile(path)
        self.cache_size = max(1, cache_size)
        self.cache: OrderedDict[str, dict[str, str]] = OrderedDict()
        self.parse_failures: dict[str, str] = {}
        self.salvaged_members: set[str] = set()
        self.unrecoverable_members: set[str] = set()
        self.exact: dict[str, str] = {}
        suffixes: dict[str, str | None] = {}
        for info in self.archive.infolist():
            if info.is_dir():
                continue
            name = _normalise_path(info.filename)
            variants = {name}
            if name.endswith(".gz"):
                variants.add(name[:-3])
            for variant in variants:
                self.exact.setdefault(variant, info.filename)
                parts = variant.split("/")
                for width in range(1, min(5, len(parts)) + 1):
                    suffix = "/".join(parts[-width:])
                    previous = suffixes.get(suffix, "__missing__")
                    if previous == "__missing__":
                        suffixes[suffix] = info.filename
                    elif previous != info.filename:
                        suffixes[suffix] = None
        self.suffixes = suffixes

    def close(self) -> None:
        self.archive.close()

    def resolve_member(self, document: str) -> str | None:
        normal = _normalise_path(document)
        variants = [normal]
        if normal.endswith(".gz"):
            variants.append(normal[:-3])
        else:
            variants.append(normal + ".gz")
        # Some OPUS alignment paths are rooted at the language directory while
        # ZIP members include a corpus prefix.  Prefer the longest unique suffix.
        for variant in variants:
            exact = self.exact.get(variant)
            if exact:
                return exact
            parts = variant.split("/")
            for width in range(min(5, len(parts)), 0, -1):
                hit = self.suffixes.get("/".join(parts[-width:]))
                if hit:
                    return hit
        return None

    def _load(self, member: str) -> dict[str, str]:
        cached = self.cache.get(member)
        if cached is not None:
            self.cache.move_to_end(member)
            return cached
        data = self.archive.read(member)
        if member.endswith(".gz"):
            data = gzip.decompress(data)
        sentences: dict[str, str] = {}
        try:
            root = ET.fromstring(data)
        except ET.ParseError as exc:
            # Real OPUS Global Voices contains a small number of malformed raw
            # XML documents.  Do not abort the whole pair and do not guess a
            # mapping: salvage only explicit sentence ids from this same member.
            self.parse_failures.setdefault(member, str(exc))
            sentences = _salvage_sentences(data)
            if sentences:
                self.salvaged_members.add(member)
            else:
                self.unrecoverable_members.add(member)
        else:
            for element in root.iter():
                if element.tag.rsplit("}", 1)[-1] != "s":
                    continue
                sid = _sentence_id(element)
                if not sid:
                    continue
                text = _sentence_text(element)
                if text:
                    sentences[sid] = text
        self.cache[member] = sentences
        self.cache.move_to_end(member)
        while len(self.cache) > self.cache_size:
            self.cache.popitem(last=False)
        return sentences

    def texts(self, document: str, sentence_ids: list[str]) -> tuple[list[str] | None, str | None]:
        member = self.resolve_member(document)
        if member is None:
            return None, "missing-document"
        sentences = self._load(member)
        values = []
        for sid in sentence_ids:
            text = sentences.get(sid)
            if text is None:
                if member in self.unrecoverable_members:
                    return None, "malformed-document"
                return None, "missing-sentence"
            values.append(text)
        return values, None

    def parse_report(self, side: str) -> list[dict[str, str | bool]]:
        return [
            {
                "side": side,
                "member": member,
                "error": error,
                "salvaged": member in self.salvaged_members,
            }
            for member, error in sorted(self.parse_failures.items())
        ]


def _lang_hint(document: str) -> str:
    normal = _normalise_path(document).lower()
    return normal.split("/", 1)[0] if "/" in normal else ""


def _choose_side(
    from_doc: str,
    to_doc: str,
    context_lang: str,
    meaning_lang: str,
    context_archive: XmlArchive,
    meaning_archive: XmlArchive,
) -> str:
    left = _lang_hint(from_doc)
    right = _lang_hint(to_doc)
    if left == context_lang.lower() and right == meaning_lang.lower():
        return "from"
    if right == context_lang.lower() and left == meaning_lang.lower():
        return "to"
    from_context = context_archive.resolve_member(from_doc) is not None
    to_meaning = meaning_archive.resolve_member(to_doc) is not None
    to_context = context_archive.resolve_member(to_doc) is not None
    from_meaning = meaning_archive.resolve_member(from_doc) is not None
    if from_context and to_meaning and not (to_context and from_meaning):
        return "from"
    if to_context and from_meaning and not (from_context and to_meaning):
        return "to"
    raise ValueError(
        "cannot infer XCES side from document paths/archive membership: "
        f"{from_doc!r} / {to_doc!r}"
    )


def extract_native(
    xces: str,
    context_archive_path: str,
    meaning_archive_path: str,
    context_lang: str,
    meaning_lang: str,
    learn_out: str,
    meaning_out: str,
    alignment_map_out: str,
    sample_limit: int = 40,
    cache_size: int = 24,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    context_archive = XmlArchive(context_archive_path, cache_size=cache_size)
    meaning_archive = XmlArchive(meaning_archive_path, cache_size=cache_size)
    stats = Counter()
    missing_documents = Counter()
    missing_sentences = Counter()
    samples: list[dict[str, Any]] = []
    try:
        with _open_text_write(learn_out) as left_out, _open_text_write(meaning_out) as right_out, _open_text_write(alignment_map_out) as map_out:
            with _open_binary(xces) as handle:
                for _event, group in ET.iterparse(handle, events=("end",)):
                    if group.tag.rsplit("}", 1)[-1] != "linkGrp":
                        continue
                    from_doc = str(group.attrib.get("fromDoc") or "").strip()
                    to_doc = str(group.attrib.get("toDoc") or "").strip()
                    if not from_doc or not to_doc:
                        stats["groupsWithoutDocuments"] += 1
                        group.clear()
                        continue
                    stats["linkGroups"] += 1
                    side = _choose_side(
                        from_doc, to_doc, context_lang, meaning_lang,
                        context_archive, meaning_archive,
                    )
                    context_doc = from_doc if side == "from" else to_doc
                    meaning_doc = to_doc if side == "from" else from_doc
                    for link in list(group):
                        if link.tag.rsplit("}", 1)[-1] != "link":
                            continue
                        stats["alignmentLinks"] += 1
                        parts = [part.strip() for part in str(link.attrib.get("xtargets") or "").split(";")]
                        if len(parts) != 2 or not parts[0] or not parts[1]:
                            stats["emptySideSkipped"] += 1
                            continue
                        context_ids = (parts[0] if side == "from" else parts[1]).split()
                        meaning_ids = (parts[1] if side == "from" else parts[0]).split()
                        context_parts, context_error = context_archive.texts(context_doc, context_ids)
                        meaning_parts, meaning_error = meaning_archive.texts(meaning_doc, meaning_ids)
                        if context_error or meaning_error:
                            stats["unresolvedNativeRows"] += 1
                            if context_error in {"missing-document", "malformed-document"}:
                                missing_documents[context_doc] += 1
                            elif context_error == "missing-sentence":
                                missing_sentences[f"{context_doc}#{' '.join(context_ids)}"] += 1
                            if meaning_error in {"missing-document", "malformed-document"}:
                                missing_documents[meaning_doc] += 1
                            elif meaning_error == "missing-sentence":
                                missing_sentences[f"{meaning_doc}#{' '.join(meaning_ids)}"] += 1
                            continue
                        context_text = " ".join(context_parts or []).strip()
                        meaning_text = " ".join(meaning_parts or []).strip()
                        if not context_text or not meaning_text:
                            stats["emptyNativeTextSkipped"] += 1
                            continue
                        number = stats["emittedRows"] + 1
                        row = {
                            "line": number,
                            "contextDocument": context_doc,
                            "meaningDocument": meaning_doc,
                            "contextSentenceIds": context_ids,
                            "meaningSentenceIds": meaning_ids,
                            "alignmentId": link.attrib.get("id"),
                        }
                        left_out.write(context_text + "\n")
                        right_out.write(meaning_text + "\n")
                        map_out.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
                        stats["emittedRows"] += 1
                        if len(samples) < sample_limit:
                            samples.append({**row, "context": context_text, "meaning": meaning_text})
                    group.clear()
    finally:
        context_archive.close()
        meaning_archive.close()

    nonempty = stats["alignmentLinks"] - stats["emptySideSkipped"]
    unresolved = stats["unresolvedNativeRows"] + stats["emptyNativeTextSkipped"]
    parse_failures = context_archive.parse_report("context") + meaning_archive.parse_report("meaning")
    report = {
        "reportVersion": 1,
        "part": 9,
        "status": "pass" if stats["emittedRows"] and unresolved == 0 else "partial-native-resolution",
        "summary": dict(stats),
        "nonEmptyAlignmentLinks": nonempty,
        "nativeResolutionRate": (stats["emittedRows"] / nonempty) if nonempty else 0.0,
        "nativeXml": {
            "strictParseFailures": len(parse_failures),
            "salvagedMembers": sum(1 for row in parse_failures if row["salvaged"]),
            "unrecoverableMembers": sum(1 for row in parse_failures if not row["salvaged"]),
            "members": parse_failures[:200],
        },
        "missingDocuments": [
            {"document": key, "affectedRows": value}
            for key, value in sorted(missing_documents.items(), key=lambda item: (-item[1], item[0]))[:200]
        ],
        "missingSentences": [
            {"sentence": key, "affectedRows": value}
            for key, value in sorted(missing_sentences.items(), key=lambda item: (-item[1], item[0]))[:200]
        ],
    }
    return report, samples


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Global Voices native XCES extraction",
        "",
        "XCES document names and sentence ids are resolved directly against the native OPUS XML archives. Moses line numbers are not used.",
        "",
        f"Status: **{report['status']}**",
        "",
        f"- alignment links: **{s.get('alignmentLinks', 0):,}**",
        f"- empty-side links skipped: **{s.get('emptySideSkipped', 0):,}**",
        f"- native rows emitted: **{s.get('emittedRows', 0):,}**",
        f"- unresolved native rows: **{s.get('unresolvedNativeRows', 0):,}**",
        f"- native resolution rate: **{report['nativeResolutionRate']:.3%}**",
        f"- malformed native XML members seen: **{report['nativeXml']['strictParseFailures']:,}**",
        f"- malformed members safely salvaged by explicit sentence id: **{report['nativeXml']['salvagedMembers']:,}**",
        f"- malformed members not recoverable: **{report['nativeXml']['unrecoverableMembers']:,}**",
        "",
        "Malformed native XML is never mapped by line number. The fallback only recovers explicit `<s id=...>` blocks from the same archive member.",
        "",
        "1:n and n:1 alignments are preserved by joining their referenced sentence ids in order.",
        "",
    ]
    return "\n".join(lines)


def samples_markdown(samples: list[dict[str, Any]]) -> str:
    lines = ["# Global Voices native extraction samples", ""]
    for row in samples:
        lines += [
            f"## row {row['line']} · `{row['contextDocument']}` -> `{row['meaningDocument']}`",
            "",
            row["context"],
            "",
            row["meaning"],
            "",
        ]
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--xces", required=True)
    ap.add_argument("--context-archive", required=True)
    ap.add_argument("--meaning-archive", required=True)
    ap.add_argument("--context-lang", required=True, help="OPUS language code used by XCES/archive, e.g. jp or zhs")
    ap.add_argument("--meaning-lang", required=True)
    ap.add_argument("--learn-out", required=True)
    ap.add_argument("--meaning-out", required=True)
    ap.add_argument("--alignment-map", required=True)
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    ap.add_argument("--samples", required=True)
    ap.add_argument("--sample-limit", type=int, default=40)
    ap.add_argument("--cache-size", type=int, default=24)
    ap.add_argument("--max-unresolved-rate", type=float, default=0.01)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.sample_limit < 0 or args.cache_size < 1:
        raise SystemExit("sample limit must be non-negative and cache size positive")
    if not 0 <= args.max_unresolved_rate <= 1:
        raise SystemExit("--max-unresolved-rate must be between 0 and 1")
    report, samples = extract_native(
        args.xces, args.context_archive, args.meaning_archive,
        args.context_lang, args.meaning_lang,
        args.learn_out, args.meaning_out, args.alignment_map,
        sample_limit=args.sample_limit, cache_size=args.cache_size,
    )
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    Path(args.samples).write_text(samples_markdown(samples), encoding="utf-8")
    nonempty = int(report["nonEmptyAlignmentLinks"])
    unresolved = int(report["summary"].get("unresolvedNativeRows", 0)) + int(report["summary"].get("emptyNativeTextSkipped", 0))
    if not report["summary"].get("emittedRows"):
        raise SystemExit("Global Voices native XCES extraction emitted zero rows")
    if nonempty and unresolved / nonempty > args.max_unresolved_rate:
        raise SystemExit(
            f"Global Voices native XCES resolution lost {unresolved}/{nonempty} non-empty links "
            f"({unresolved/nonempty:.2%}), above safety limit {args.max_unresolved_rate:.2%}"
        )
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
