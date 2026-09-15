#!/usr/bin/env python3
"""Build a verified Global Voices document -> article attribution manifest.

OPUS Global Voices document names encode the language, publication date and
article slug.  This tool uses that identity only to construct a *candidate* URL;
it does not trust the candidate until the live Global Voices page is fetched,
redirects remain inside globalvoices.org, a canonical article URL is recovered,
and at least one credited contributor is present in page metadata/markup.

Unverified documents are reported and omitted.  Nothing is guessed into the
publication manifest.
"""
from __future__ import annotations

import argparse
import gzip
import html
import json
import re
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Callable
from urllib.parse import urljoin, urlparse

from globalvoices_attribution import valid_globalvoices_url


HOSTS = {
    "en": "globalvoices.org",
    "de": "de.globalvoices.org",
    "es": "es.globalvoices.org",
    "fr": "fr.globalvoices.org",
    "it": "it.globalvoices.org",
    "jp": "jp.globalvoices.org",
    "ja": "jp.globalvoices.org",
    "ko": "ko.globalvoices.org",
    "pl": "pl.globalvoices.org",
    "pt": "pt.globalvoices.org",
    "ru": "ru.globalvoices.org",
    "zh": "zhs.globalvoices.org",
    "zhs": "zhs.globalvoices.org",
    "zht": "zht.globalvoices.org",
}

_DOC = re.compile(
    r"^(?P<lang>[a-z]{2,3})/(?P<year>20\d{2})_(?P<month>0[1-9]|1[0-2])_"
    r"(?P<day>0[1-9]|[12]\d|3[01])_(?P<slug>[^/]+?)_?\.xml(?:\.gz)?$",
    re.IGNORECASE,
)


def _open_text(path: str):
    return gzip.open(path, "rt", encoding="utf-8", errors="replace") if path.endswith(".gz") else open(path, encoding="utf-8", errors="replace")


def normalize_document(value: str) -> str:
    value = value.replace("\\", "/").strip()
    while value.startswith("./"):
        value = value[2:]
    while value.startswith("../"):
        value = value[3:]
    return re.sub(r"/+", "/", value).lstrip("/")


def candidate_url(document: str) -> str | None:
    """Construct the only URL candidate encoded by an OPUS document id.

    No fuzzy search and no title matching is performed.  The candidate still
    has to be fetched and verified by :func:`resolve_document`.
    """
    normal = normalize_document(document)
    match = _DOC.fullmatch(normal)
    if not match:
        return None
    host = HOSTS.get(match.group("lang").lower())
    if not host:
        return None
    slug = match.group("slug").rstrip("_")
    if not slug or any(ch.isspace() for ch in slug):
        return None
    return "https://{}/{}/{}/{}/{}/".format(
        host, match.group("year"), match.group("month"), match.group("day"), slug
    )


def _clean_name(value: str) -> str | None:
    value = html.unescape(" ".join(value.split())).strip(" |,-\t\r\n")
    if not value or len(value) > 160:
        return None
    parsed = urlparse(value)
    if parsed.scheme or parsed.netloc or value.startswith("@"):  # URL/account, not a display name
        return None
    return value


def _names_from_json(value: Any) -> list[str]:
    out: list[str] = []
    if isinstance(value, str):
        cleaned = _clean_name(value)
        if cleaned:
            out.append(cleaned)
    elif isinstance(value, dict):
        if isinstance(value.get("name"), str):
            cleaned = _clean_name(value["name"])
            if cleaned:
                out.append(cleaned)
    elif isinstance(value, list):
        for item in value:
            out.extend(_names_from_json(item))
    return out


class ArticleMetadataParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.canonical: str | None = None
        self.meta_authors: list[str] = []
        self.rel_author_depth = 0
        self.rel_author_text: list[str] = []
        self.rel_authors: list[str] = []
        self._json_depth = 0
        self._json_chunks: list[str] = []
        self.jsonld: list[Any] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        data = {k.lower(): (v or "") for k, v in attrs}
        tag = tag.lower()
        if tag == "link":
            rel = {item.lower() for item in data.get("rel", "").split()}
            if "canonical" in rel and data.get("href"):
                self.canonical = data["href"].strip()
        elif tag == "meta":
            key = (data.get("name") or data.get("property") or "").lower()
            if key in {"author", "article:author", "byl"} and data.get("content"):
                name = _clean_name(data["content"])
                if name:
                    self.meta_authors.append(name)
        elif tag == "a":
            rel = {item.lower() for item in data.get("rel", "").split()}
            if "author" in rel:
                self.rel_author_depth += 1
                self.rel_author_text = []
        elif tag == "script" and data.get("type", "").lower() == "application/ld+json":
            self._json_depth += 1
            self._json_chunks = []

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "a" and self.rel_author_depth:
            name = _clean_name("".join(self.rel_author_text))
            if name:
                self.rel_authors.append(name)
            self.rel_author_depth = 0
            self.rel_author_text = []
        elif tag == "script" and self._json_depth:
            raw = "".join(self._json_chunks).strip()
            self._json_depth = 0
            self._json_chunks = []
            if raw:
                try:
                    self.jsonld.append(json.loads(raw))
                except json.JSONDecodeError:
                    pass

    def handle_data(self, data: str) -> None:
        if self.rel_author_depth:
            self.rel_author_text.append(data)
        if self._json_depth:
            self._json_chunks.append(data)


def _article_json_objects(value: Any) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    if isinstance(value, dict):
        types = value.get("@type")
        type_values = [types] if isinstance(types, str) else types if isinstance(types, list) else []
        if any(str(t).lower() in {"article", "newsarticle", "blogposting", "reportagenewsarticle"} for t in type_values):
            out.append(value)
        for child in value.values():
            if isinstance(child, (dict, list)):
                out.extend(_article_json_objects(child))
    elif isinstance(value, list):
        for child in value:
            out.extend(_article_json_objects(child))
    return out


def parse_article_metadata(page_url: str, body: str) -> tuple[str | None, list[str]]:
    parser = ArticleMetadataParser()
    parser.feed(body)
    canonical = urljoin(page_url, parser.canonical) if parser.canonical else None
    contributors: list[str] = []
    json_canonical: str | None = None
    for root in parser.jsonld:
        for article in _article_json_objects(root):
            if json_canonical is None:
                raw_url = article.get("url")
                if isinstance(raw_url, str) and raw_url.strip():
                    json_canonical = urljoin(page_url, raw_url.strip())
                main = article.get("mainEntityOfPage")
                if json_canonical is None and isinstance(main, str):
                    json_canonical = urljoin(page_url, main)
                elif json_canonical is None and isinstance(main, dict) and isinstance(main.get("@id"), str):
                    json_canonical = urljoin(page_url, main["@id"])
            for key in ("author", "translator", "editor", "contributor"):
                contributors.extend(_names_from_json(article.get(key)))
    canonical = canonical or json_canonical
    contributors.extend(parser.meta_authors)
    contributors.extend(parser.rel_authors)
    unique: list[str] = []
    seen = set()
    for name in contributors:
        key = name.casefold()
        if key not in seen:
            unique.append(name)
            seen.add(key)
    return canonical, unique


@dataclass
class FetchResult:
    final_url: str
    body: str


def fetch_page(url: str, timeout: float = 20.0, retries: int = 2) -> FetchResult:
    last_error: Exception | None = None
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "ikna-catalogue-v2-provenance/0.11 (+https://github.com/d1d2dopamine/ikna)",
            "Accept": "text/html,application/xhtml+xml",
        },
    )
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                final = response.geturl()
                raw = response.read(2_000_000)
                charset = response.headers.get_content_charset() or "utf-8"
                return FetchResult(final, raw.decode(charset, errors="replace"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as exc:
            last_error = exc
            if attempt < retries:
                time.sleep(0.5 * (attempt + 1))
    assert last_error is not None
    raise last_error


def resolve_document(
    document: str,
    fetcher: Callable[[str], FetchResult] = fetch_page,
) -> tuple[dict[str, Any] | None, str]:
    candidate = candidate_url(document)
    if candidate is None:
        return None, "unsupported-document-id"
    try:
        fetched = fetcher(candidate)
    except Exception as exc:  # network failures become explicit evidence, never attribution
        return None, "fetch-error:" + type(exc).__name__
    if not valid_globalvoices_url(fetched.final_url):
        return None, "redirect-outside-globalvoices"
    canonical, contributors = parse_article_metadata(fetched.final_url, fetched.body)
    if not canonical:
        return None, "missing-canonical"
    if not valid_globalvoices_url(canonical):
        return None, "invalid-canonical-host"
    if not contributors:
        return None, "missing-contributors"
    # The fetched candidate proves the OPUS date/slug identity. Canonical may
    # differ after a legitimate site migration/redirect, but must stay on GV.
    return {
        "document": normalize_document(document),
        "articleUrl": canonical,
        "contributors": contributors,
        "candidateUrl": candidate,
        "resolvedUrl": fetched.final_url,
        "verification": "fetched-document-encoded-url",
    }, "resolved"


def alignment_inventory(alignment_map: str) -> tuple[Counter[str], Counter[tuple[str, str]]]:
    counts: Counter[str] = Counter()
    pairs: Counter[tuple[str, str]] = Counter()
    with _open_text(alignment_map) as handle:
        for physical, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            value = json.loads(line)
            left = normalize_document(str(value.get("contextDocument") or ""))
            right = normalize_document(str(value.get("meaningDocument") or ""))
            if not left or not right:
                raise ValueError(f"{alignment_map}:{physical}: missing contextDocument/meaningDocument")
            counts[left] += 1
            counts[right] += 1
            pairs[(left, right)] += 1
    return counts, pairs


def document_counts(alignment_map: str) -> Counter[str]:
    return alignment_inventory(alignment_map)[0]


def build_manifest(
    alignment_map: str,
    *,
    max_documents: int = 0,
    workers: int = 8,
    fetcher: Callable[[str], FetchResult] = fetch_page,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    counts, pair_counts = alignment_inventory(alignment_map)
    ranked = sorted(counts, key=lambda doc: (-counts[doc], doc))
    if max_documents > 0:
        # Diagnostic bounds should spend requests on *both* documents of the
        # highest-impact alignments. Selecting popular documents independently
        # can verify two disconnected halves and still yield zero publishable
        # rows. Greedy pair coverage maximizes useful attribution evidence.
        selected_set: set[str] = set()
        for pair, _affected in sorted(pair_counts.items(), key=lambda item: (-item[1], item[0])):
            additions = [doc for doc in pair if doc not in selected_set]
            if len(selected_set) + len(additions) > max_documents:
                continue
            selected_set.update(additions)
            if len(selected_set) >= max_documents:
                break
        selected = sorted(selected_set, key=lambda doc: (-counts[doc], doc))
    else:
        selected = ranked
    rows: list[dict[str, Any]] = []
    unresolved: list[dict[str, Any]] = []
    reasons = Counter()

    def one(document: str):
        row, reason = resolve_document(document, fetcher=fetcher)
        return document, row, reason

    if workers <= 1:
        results = (one(document) for document in selected)
        for document, row, reason in results:
            reasons[reason] += 1
            if row is None:
                unresolved.append({"document": document, "affectedRows": counts[document], "reason": reason})
            else:
                rows.append(row)
    else:
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = {executor.submit(one, document): document for document in selected}
            for future in as_completed(futures):
                document, row, reason = future.result()
                reasons[reason] += 1
                if row is None:
                    unresolved.append({"document": document, "affectedRows": counts[document], "reason": reason})
                else:
                    rows.append(row)

    rows.sort(key=lambda row: row["document"])
    unresolved.sort(key=lambda row: (-row["affectedRows"], row["document"]))
    selected_docs = set(selected)
    resolved_docs = {row["document"] for row in rows}
    selected_alignment_rows = sum(
        affected for (left, right), affected in pair_counts.items()
        if left in selected_docs and right in selected_docs
    )
    verified_alignment_rows = sum(
        affected for (left, right), affected in pair_counts.items()
        if left in resolved_docs and right in resolved_docs
    )
    report = {
        "reportVersion": 1,
        "part": 9,
        "status": "pass" if len(rows) == len(selected) and len(selected) == len(ranked) else "partial-evidence",
        "publicationSafe": len(rows) == len(ranked),
        "summary": {
            "alignmentDocuments": len(ranked),
            "selectedDocuments": len(selected),
            "resolvedDocuments": len(rows),
            "unresolvedSelectedDocuments": len(unresolved),
            "deferredDocuments": len(ranked) - len(selected),
            "selectedAlignmentRows": selected_alignment_rows,
            "verifiedAlignmentRows": verified_alignment_rows,
            "resolvedAffectedRowReferences": sum(counts[row["document"]] for row in rows),
        },
        "reasons": dict(sorted(reasons.items())),
        "unresolved": unresolved[:500],
    }
    return rows, report


def markdown(report: dict[str, Any]) -> str:
    s = report["summary"]
    lines = [
        "# Global Voices article-manifest builder",
        "",
        f"Status: **{report['status']}**",
        "",
        f"- documents referenced by alignment: **{s['alignmentDocuments']:,}**",
        f"- documents selected for live verification: **{s['selectedDocuments']:,}**",
        f"- verified document/article mappings: **{s['resolvedDocuments']:,}**",
        f"- unresolved selected documents: **{s['unresolvedSelectedDocuments']:,}**",
        f"- deferred by diagnostic bound: **{s['deferredDocuments']:,}**",
        f"- aligned rows covered by selected document pairs: **{s['selectedAlignmentRows']:,}**",
        f"- aligned rows with both documents verified: **{s['verifiedAlignmentRows']:,}**",
        "",
        "A document enters the manifest only after its date/slug-derived Global Voices URL is fetched, the resolved/canonical URL stays on globalvoices.org, and at least one credited contributor is recovered. Failures remain rejected evidence.",
    ]
    if report.get("reasons"):
        lines += ["", "## Outcomes", ""]
        for key, value in report["reasons"].items():
            lines.append(f"- `{key}`: **{value:,}**")
    if report.get("unresolved"):
        lines += ["", "## Highest-impact unresolved documents", "", "| document | affected rows | reason |", "| --- | ---: | --- |"]
        for row in report["unresolved"][:100]:
            lines.append(f"| `{row['document']}` | {row['affectedRows']:,} | `{row['reason']}` |")
    lines.append("")
    return "\n".join(lines)


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--alignment-map", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--json", required=True)
    ap.add_argument("--markdown", required=True)
    ap.add_argument("--max-documents", type=int, default=0, help="0 = verify every referenced document")
    ap.add_argument("--workers", type=int, default=8)
    return ap


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.max_documents < 0:
        raise SystemExit("--max-documents must be non-negative")
    if args.workers < 1 or args.workers > 32:
        raise SystemExit("--workers must be in 1..32")
    rows, report = build_manifest(
        args.alignment_map,
        max_documents=args.max_documents,
        workers=args.workers,
    )
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as handle:
        for row in rows:
            # Keep only publication fields + verification evidence. The existing
            # attribution resolver ignores extra evidence fields by design.
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    Path(args.json).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown).write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
