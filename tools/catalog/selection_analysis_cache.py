"""Experimental offline context-analysis reuse; never a publishing path.

Call only after full input/provenance validation, fresh staging and rank building.
This cache cannot replace those gates, pair evidence, allocation or output checks.
It preserves the *exact* choices_json strings and the original quality Counter.

Artifacts are immutable gzip JSONL plus a checksummed completion manifest. An
incomplete or corrupt entry raises CacheIntegrityError, without overwriting or
repairing it. Remove/quarantine such private artifacts deliberately before retry.
Publication of each file uses an exclusive hard link; the manifest is published
last. An interruption can leave an explicitly unusable entry, never a cache hit.
Inputs and the staging database must remain stable while this function runs.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import platform
import re
import sqlite3
import tempfile
import unicodedata
import zlib
from pathlib import Path

from segmentation import CJK, prepare

# Version 1 keyed analysis by normalized frequency contexts. It cannot safely
# supply boundaries for distinct raw source variants and is never migrated.
CACHE_VERSION = 2
HERE = Path(__file__).resolve().parent
SOURCE_ROOT = HERE.parent.parent
IMPLEMENTATIONS = (
    "selection_analysis_cache.py", "selection_experiment.py", "selection_policy.py",
    "build_catalogue_v2.py", "catalogue_core.py", "catalogue_v2.py",
    "segmentation.py", "japanese_boundary.py", "ingest/model.py",
)
MAX_LINE_BYTES = 8 * 1024 * 1024
MAX_MANIFEST_BYTES = 1024 * 1024
BATCH_SIZE = 5000
HEX = re.compile(r"[0-9a-f]{64}\Z")


class CacheIntegrityError(ValueError):
    """An existing artifact is incomplete or cannot be safely reused."""


def _json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def _sha(value):
    return hashlib.sha256(_json(value)).hexdigest()


def _file_sha(path):
    with Path(path).open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def _contexts(db, collection, lang):
    return db.execute("SELECT analysis_key,context FROM analysis_context WHERE collection=? AND lang=? ORDER BY analysis_key",
                      (collection, lang))


def _representative_digest(db, collection, lang):
    digest = hashlib.sha256()
    for row in db.execute("SELECT context_key,context FROM context WHERE collection=? AND lang=? ORDER BY context_key",
                          (collection, lang)):
        digest.update(_json(list(row)) + b"\n")
    return digest.hexdigest()


def _context_digest(db, collection, lang):
    digest = hashlib.sha256()
    count = 0
    # The representative mapping also affects preserved presentation surfaces.
    for row in db.execute("SELECT analysis_key,context_key,context FROM analysis_context "
                          "WHERE collection=? AND lang=? ORDER BY analysis_key", (collection, lang)):
        digest.update(_json(list(row)) + b"\n")
        count += 1
    return digest.hexdigest(), count


def _counts(value):
    if not isinstance(value, dict) or any(not isinstance(k, str) or type(v) is not int or v < 0
                                          for k, v in value.items()):
        raise CacheIntegrityError("invalid cached quality Counter")
    return dict(value)


def _row(value, collection, lang, expected_key):
    if (not isinstance(value, list) or len(value) != 5 or
            value[:3] != [collection, lang, expected_key] or
            type(value[3]) is not int or value[3] < 0 or not isinstance(value[4], str)):
        raise CacheIntegrityError("cache row schema or exact analysis-key alignment mismatch")
    choices = json.loads(value[4])
    if not isinstance(choices, list) or any(
        not isinstance(c, list) or len(c) != 4 or type(c[0]) is not int or c[0] < 1
        or any(not isinstance(s, str) for s in c[1:]) for c in choices
    ):
        raise CacheIntegrityError("invalid cached choices schema")
    return value


def _identity(db, collection, lang, ranks, function_top, japanese_guard,
              canonical_unique, registry_sha256, parent_identity, runtime_identity):
    if not isinstance(registry_sha256, str) or not HEX.fullmatch(registry_sha256):
        raise ValueError("registry_sha256 must be the verified source-registry SHA-256")
    if type(function_top) is not int or function_top < 0 or type(canonical_unique) is not bool:
        raise ValueError("invalid context analysis settings")
    if any(not isinstance(k, str) or type(v) is not int or v < 1 for k, v in ranks.items()):
        raise ValueError("invalid exact ranks")
    runtime = dict(runtime_identity or {})
    if lang in CJK:
        libraries = runtime.get("icuLibraries")
        if (not isinstance(libraries, dict) or not {"main", "uc", "data"}.issubset(libraries) or
                any(not isinstance(k, str) or not isinstance(v, str) or not HEX.fullmatch(v)
                    for k, v in libraries.items())):
            raise ValueError("CJK cache needs runtime_identity.icuLibraries with verified loaded main/uc/data SHA-256s")
    guard_evidence = None
    if lang == "ja" and japanese_guard is not None:
        guard_evidence = getattr(japanese_guard, "evidence", None)
        if not isinstance(guard_evidence, dict) or not guard_evidence:
            raise ValueError("Japanese cache needs verified engine/dictionary guard evidence")
    contexts_sha, count = _context_digest(db, collection, lang)
    identity = {
        "cacheVersion": CACHE_VERSION, "analysisIdentity": "exact-utf8-source-v1",
        "analysisSchema": ["collection", "lang", "analysis_key", "token_count", "choices_json"],
        "collection": collection, "lang": lang, "contextTextSha256": contexts_sha,
        "contextCount": count, "ranksSha256": _sha(sorted(ranks.items())),
        "frequencyRepresentativesSha256": _representative_digest(db, collection, lang),
        "registrySha256": registry_sha256, "functionTop": function_top,
        "canonicalUnique": canonical_unique, "japaneseGuard": guard_evidence,
        "python": platform.python_version(), "pythonImplementation": platform.python_implementation(),
        "unicode": unicodedata.unidata_version, "segmentation": prepare([lang]),
        "runtimeIdentity": runtime, "parentIdentity": dict(parent_identity or {}),
        "implementationSha256": {name: _file_sha(HERE / name) for name in IMPLEMENTATIONS},
    }
    return identity


def _cache_root(db, cache_dir):
    root = Path(cache_dir).resolve()
    if root == SOURCE_ROOT or SOURCE_ROOT in root.parents:
        raise ValueError("private analysis cache must be outside repository source assets")
    for _, _, filename in db.execute("PRAGMA database_list"):
        if filename:
            path = Path(filename).resolve()
            if path == root or root in path.parents:
                raise ValueError("private analysis cache must be separate from staging/temporary artifacts")
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    return root


def _load_manifest(path, identity, key):
    try:
        with path.open("rb") as handle:
            raw = handle.read(MAX_MANIFEST_BYTES + 1)
        if len(raw) > MAX_MANIFEST_BYTES:
            raise CacheIntegrityError("cache manifest exceeds limit")
        envelope = json.loads(raw)
        payload = envelope["payload"]
        if (set(envelope) != {"payload", "sha256"} or _sha(payload) != envelope["sha256"] or
                set(payload) != {"complete", "identity", "key", "rows", "dataSha256", "dataBytes", "counts"} or
                payload["complete"] is not True or payload["identity"] != identity or payload["key"] != key or
                type(payload["rows"]) is not int or payload["rows"] != identity["contextCount"] or
                type(payload["dataBytes"]) is not int or payload["dataBytes"] < 0 or
                not isinstance(payload["dataSha256"], str) or not HEX.fullmatch(payload["dataSha256"])):
            raise CacheIntegrityError("cache manifest identity/integrity/count mismatch")
        _counts(payload["counts"])
        return payload
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise CacheIntegrityError(f"invalid cache manifest: {path}: {exc}") from exc


def _restore(db, data_path, manifest, collection, lang):
    # Use one open descriptor for hashing and decompression. Restored rows first
    # live in a disk-backed temporary SQLite database; target rows are untouched
    # until the entire gzip, schema, count and context alignment have passed.
    with tempfile.TemporaryDirectory(prefix="ikna-analysis-verify-") as temp:
        verified = sqlite3.connect(str(Path(temp) / "verified.sqlite"))
        try:
            verified.execute("CREATE TABLE rows(collection TEXT,lang TEXT,analysis_key TEXT PRIMARY KEY,token_count INTEGER,choices_json TEXT)")
            with data_path.open("rb") as raw:
                if os.fstat(raw.fileno()).st_size != manifest["dataBytes"] or hashlib.file_digest(raw, "sha256").hexdigest() != manifest["dataSha256"]:
                    raise CacheIntegrityError("cache compressed bytes/hash mismatch")
                raw.seek(0)
                seen = 0
                with gzip.GzipFile(fileobj=raw, mode="rb") as stream:
                    batch = []
                    for key, _text in _contexts(db, collection, lang):
                        line = stream.readline(MAX_LINE_BYTES + 1)
                        if not line or len(line) > MAX_LINE_BYTES or not line.endswith(b"\n"):
                            raise CacheIntegrityError("cache rows truncated, oversized or count mismatch")
                        batch.append(_row(json.loads(line), collection, lang, key))
                        seen += 1
                        if len(batch) == BATCH_SIZE:
                            verified.executemany("INSERT INTO rows VALUES(?,?,?,?,?)", batch)
                            batch.clear()
                    if stream.read(1) or seen != manifest["rows"]:
                        raise CacheIntegrityError("cache row count mismatch")
                    if batch:
                        verified.executemany("INSERT INTO rows VALUES(?,?,?,?,?)", batch)
                # Detect changes to the open artifact during validation as well.
                raw.seek(0)
                if hashlib.file_digest(raw, "sha256").hexdigest() != manifest["dataSha256"]:
                    raise CacheIntegrityError("cache bytes changed during validation")
            # The production staging schema sets journal_mode=OFF. Savepoints
            # alone cannot undo DELETE/partial INSERT in that mode. Briefly use
            # a disk rollback journal; never change a caller's active transaction.
            journal = db.execute("PRAGMA journal_mode").fetchone()[0].lower()
            if journal == "off":
                if db.in_transaction:
                    raise CacheIntegrityError("cache restore requires committed staging when journal_mode=OFF")
                actual = db.execute("PRAGMA journal_mode=DELETE").fetchone()[0].lower()
                if actual not in {"delete", "memory"}:
                    raise CacheIntegrityError("could not enable cache restore rollback journal")
            try:
                db.execute("SAVEPOINT analysis_cache_restore")
                try:
                    db.execute("DELETE FROM context_analysis WHERE collection=? AND lang=?", (collection, lang))
                    cursor = verified.execute("SELECT * FROM rows ORDER BY analysis_key")
                    while batch := cursor.fetchmany(BATCH_SIZE):
                        db.executemany("INSERT INTO context_analysis VALUES(?,?,?,?,?)", batch)
                    db.execute("RELEASE analysis_cache_restore")
                except BaseException:
                    db.execute("ROLLBACK TO analysis_cache_restore")
                    db.execute("RELEASE analysis_cache_restore")
                    raise
            finally:
                if journal == "off":
                    db.execute("PRAGMA journal_mode=OFF")
        finally:
            verified.close()


def _publish(db, root, data_path, manifest_path, identity, key, counts, collection, lang):
    # Temporary files are siblings on the same filesystem, but never complete
    # entries. No completed or corrupt artifact is ever replaced by os.replace.
    with tempfile.TemporaryDirectory(prefix=".analysis-writing-", dir=root) as temp:
        data_temp = Path(temp) / "rows.jsonl.gz"
        seen = 0
        with data_temp.open("wb") as raw:
            with gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as stream:
                contexts = iter(_contexts(db, collection, lang))
                for row in db.execute("SELECT * FROM context_analysis WHERE collection=? AND lang=? ORDER BY analysis_key", (collection, lang)):
                    expected = next(contexts, None)
                    if expected is None:
                        raise CacheIntegrityError("computed analysis has foreign or excess rows")
                    value = _row(list(row), collection, lang, expected[0])
                    line = _json(value) + b"\n"
                    if len(line) > MAX_LINE_BYTES:
                        raise CacheIntegrityError("computed analysis row exceeds cache limit")
                    stream.write(line)
                    seen += 1
                if next(contexts, None) is not None or seen != identity["contextCount"]:
                    raise CacheIntegrityError("computed analysis missing exact context rows")
            raw.flush()
            os.fsync(raw.fileno())
        payload = {"complete": True, "identity": identity, "key": key, "rows": seen,
                   "dataSha256": _file_sha(data_temp), "dataBytes": data_temp.stat().st_size,
                   "counts": _counts(counts)}
        manifest_temp = Path(temp) / "manifest.json"
        with manifest_temp.open("wb") as handle:
            handle.write(_json({"payload": payload, "sha256": _sha(payload)}) + b"\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.link(data_temp, data_path)  # atomic exclusive creation, never overwrite
        os.link(manifest_temp, manifest_path)  # sole completion marker, published last
        directory = os.open(root, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    return payload


def run_cached_analysis(db, collection, lang, ranks, function_top, japanese_guard,
                        canonical_unique, cache_dir, registry_sha256, compute, *,
                        parent_identity=None, runtime_identity=None):
    """Return (original quality-count dict, separate cache diagnostics).

    compute is a zero-argument call to the original build_context_analysis with
    exactly these arguments. parent_identity binds verified pool/report hashes
    and scope; runtime_identity binds additional actual-loaded dependency hashes
    (required as icuLibraries for CJK). No wall time enters cache decisions.
    The caller owns full validation and must supply stable verified identities.
    """
    root = _cache_root(db, cache_dir)
    identity = _identity(db, collection, lang, ranks, function_top, japanese_guard,
                         canonical_unique, registry_sha256, parent_identity, runtime_identity)
    key = _sha(identity)
    data_path, manifest_path = root / (key + ".jsonl.gz"), root / (key + ".json")
    exists = data_path.exists(), manifest_path.exists()
    try:
        if any(exists):
            if not all(exists):
                raise CacheIntegrityError("incomplete cache entry: data and completion manifest are both required")
            manifest = _load_manifest(manifest_path, identity, key)
            _restore(db, data_path, manifest, collection, lang)
            status = "hit"
        else:
            # Never let the original count-only shortcut lose policy counters or
            # trust stale analysis from this staging DB. A miss always computes.
            db.execute("DELETE FROM context_analysis WHERE collection=? AND lang=?", (collection, lang))
            counts = _counts(compute())
            if (_context_digest(db, collection, lang) != (identity["contextTextSha256"], identity["contextCount"]) or
                    _representative_digest(db, collection, lang) != identity["frequencyRepresentativesSha256"]):
                raise CacheIntegrityError("input contexts changed during analysis")
            manifest = _publish(db, root, data_path, manifest_path, identity, key, counts, collection, lang)
            status = "miss"
    except (OSError, EOFError, zlib.error, sqlite3.DatabaseError, UnicodeError, ValueError) as exc:
        if isinstance(exc, CacheIntegrityError):
            raise
        raise CacheIntegrityError(f"analysis cache failed closed: {exc}") from exc
    return dict(manifest["counts"]), {"status": status, "key": key,
        "rows": manifest["rows"], "dataBytes": manifest["dataBytes"],
        "dataSha256": manifest["dataSha256"], "publicationSafe": False}
