#!/usr/bin/env python3
"""Measure a pinned selected intermediate, without selecting or publishing packs.

The built-pack experiment in storage_experiment.py has a different input contract.
Here every field (including unknown evidence) and context order must round-trip
through the actual serialized files. Pooling never separates an origin from its
aligned meaning: candidateId/origins remain inline on that occurrence's reference.
Only standard-library offline I/O is used. Outputs are NOT installable packs.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import platform
import sqlite3
import statistics
import tempfile
import zlib

MIB = 1024 * 1024
MODES = ("pair", "language")
MEANING_KEYS = {"meaning", "meaningId"}
LINK_KEYS = {"candidateId", "origins"}
PACK_REQUIRED = ("id", "text", "context", "translation", "targetStart", "targetEnd",
                 "freqRank", "tokens")


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
                      allow_nan=False).encode("utf-8") + b"\n"


def sha_file(path):
    with Path(path).open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def json_rows(path):
    with gzip.open(path, "rb") as handle:
        for line in handle:
            row = json.loads(line)
            require(isinstance(row, dict), "JSONL row must be an object")
            yield row


class Writer:
    def __init__(self, path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)
        require(not path.exists(), "output file already exists")
        # An unnamed spool cannot be mistaken for a completed gzip by workspace
        # sync/watchers. Publish the closed stream atomically, never in place.
        self.raw = tempfile.TemporaryFile(dir=path.parent)
        self.gz = gzip.GzipFile(fileobj=self.raw, filename="", mode="wb", mtime=0,
                                compresslevel=9)
        self.logical = hashlib.sha256()
        self.raw_bytes = self.rows = 0

    def write(self, value):
        data = encode(value)
        self.gz.write(data)
        self.logical.update(data)
        self.raw_bytes += len(data)
        self.rows += 1

    def __enter__(self):
        return self

    def __exit__(self, *args):
        temp = None
        try:
            self.gz.close()
            if args[0] is None:
                self.raw.seek(0)
                with tempfile.NamedTemporaryFile(dir=self.path.parent, prefix="storage-",
                                                  suffix=".tmp", delete=False) as handle:
                    temp = Path(handle.name)
                    handle.write(self.raw.read())
                temp.replace(self.path)
        finally:
            self.raw.close()
            if temp is not None:
                temp.unlink(missing_ok=True)

    def identity(self):
        return {"file": self.path.name, "bytes": self.path.stat().st_size,
                "rawBytes": self.raw_bytes, "sha256": sha_file(self.path),
                "logicalSha256": self.logical.hexdigest(), "rows": self.rows}


def split_context(context):
    # Unknown fields belong to the core, not to a silent discard list.
    return ({k: v for k, v in context.items() if k not in MEANING_KEYS | LINK_KEYS},
            {k: v for k, v in context.items() if k in MEANING_KEYS},
            {k: v for k, v in context.items() if k in LINK_KEYS})


def read_snapshot(path):
    snapshot = json.loads(path.read_bytes())
    require(snapshot.get("snapshotVersion") == 1 and
            snapshot.get("readyForPart12Measurement") is True and
            snapshot.get("everydaySelectedSnapshotFrozen") is True and
            snapshot.get("publicationSafe") is False,
            "a frozen non-publishing selected snapshot accepted for Part12 is required")
    material = snapshot["selectedMaterial"]
    require(material.get("format") == "selected-memberships-jsonl-gzip-v1" and
            material.get("completeIncludedDecisions") is True and
            material.get("publicationSafe") is False, "unsupported selected material")
    return snapshot


def prepare_baseline(selected, snapshot, root):
    material = snapshot["selectedMaterial"]
    require(selected.stat().st_size == material["sizeBytes"] and
            sha_file(selected) == material["sha256"], "selected compressed identity mismatch")
    logical = hashlib.sha256()
    logical_bytes = memberships = contexts = origins = 0
    counts = Counter()
    missing = Counter()
    specs = {}
    source_hashes = {}
    root.mkdir(parents=True)
    # SQLite's temporary database is deleted on close, with its scratch files
    # routed into this task's output. Do not expose a live named SQLite database
    # as a measurement artifact while workspace file synchronization is active.
    db = sqlite3.connect("")
    decks = []
    batch = []
    try:
        db.execute("PRAGMA temp_store_directory='" + str(root).replace("'", "''") + "'")
        db.executescript("""PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
            PRAGMA cache_size=-32768;
            CREATE TABLE rows(deck TEXT, position INTEGER, payload BLOB,
                PRIMARY KEY(deck,position)) WITHOUT ROWID;""")
        with gzip.open(selected, "rb") as handle:
            for line in handle:
                logical.update(line)
                logical_bytes += len(line)
                row = json.loads(line)
                deck = row["deckId"]
                require(deck in material["decks"], "unrecorded deck in selected material")
                spec = {k: row[k] for k in ("deckId", "lang", "meaningLang", "collection", "level")}
                require(all(isinstance(v, str) and v and v.replace("-", "").isalnum()
                            and v.isascii() for v in spec.values()), "unsafe deck scope")
                require(spec["collection"] == "everyday", "only the accepted Everyday scope is measured")
                require(specs.setdefault(deck, spec) == spec, "inconsistent deck scope")
                require(isinstance(row["contexts"], list) and row["contexts"] and
                        all(isinstance(c, dict) and isinstance(c.get("origins"), list)
                            for c in row["contexts"]), "invalid selected contexts")
                counts[deck] += 1
                data = encode(row)
                source_hashes.setdefault(deck, hashlib.sha256()).update(data)
                batch.append((deck, counts[deck], data))
                if len(batch) >= 5000:
                    db.executemany("INSERT INTO rows VALUES(?,?,?)", batch)
                    batch.clear()
                memberships += 1
                contexts += len(row["contexts"])
                origins += sum(len(c["origins"]) for c in row["contexts"])
                missing.update(k for k in PACK_REQUIRED if k not in row)
                if memberships % 100000 == 0:
                    print(f"Baseline staged: {memberships:,} memberships", flush=True)
        if batch:
            db.executemany("INSERT INTO rows VALUES(?,?,?)", batch)
        db.commit()
        require(logical.hexdigest() == material["logicalSha256"] and
                logical_bytes == material["logicalSizeBytes"], "selected logical identity mismatch")
        require(dict(counts) == material["decks"], "selected deck census mismatch")
        for name, actual in (("memberships", memberships), ("contexts", contexts), ("origins", origins)):
            require(actual == material[name], "selected " + name + " mismatch")
        # Bound open gzip streams to one deck. The staging index retains the
        # original within-deck order without keeping hundreds of writers open.
        for deck in sorted(specs):
            with Writer(root / (deck + ".selected.jsonl.gz")) as writer:
                for (payload,) in db.execute("SELECT payload FROM rows WHERE deck=? ORDER BY position", (deck,)):
                    writer.write(json.loads(payload))
            identity = writer.identity()
            require(identity["rows"] == counts[deck] and
                    identity["logicalSha256"] == source_hashes[deck].hexdigest(),
                    "staged rows differ from selected input")
            decks.append({**specs[deck], **identity})
            if len(decks) % 25 == 0:
                print(f"Baseline packed: {len(decks)}/{len(specs)} decks", flush=True)
    finally:
        db.close()
    # Re-read every baseline file before treating these bytes as verified output.
    for position, deck in enumerate(decks, 1):
        h = hashlib.sha256()
        count = raw_bytes = 0
        for row in json_rows(root / deck["file"]):
            data = encode(row)
            h.update(data)
            raw_bytes += len(data)
            count += 1
        require(h.hexdigest() == deck["logicalSha256"] and count == deck["rows"] and
                raw_bytes == deck["rawBytes"], "serialized baseline mismatch")
        deck["serializedRowsVerified"] = count
        if position % 25 == 0:
            print(f"Baseline reread: {position}/{len(decks)} decks", flush=True)
    descriptor = {"format": "selected-storage-self-contained-v1", "publicationSafe": False,
                  "snapshotId": snapshot["snapshotId"], "decks": decks}
    (root / "layout.json").write_bytes(encode(descriptor))
    return {"decks": decks, "memberships": memberships, "contexts": contexts, "origins": origins,
            "assetBytes": sum(d["bytes"] for d in decks),
            "rawBytes": sum(d["rawBytes"] for d in decks),
            "descriptorBytes": (root / "layout.json").stat().st_size,
            "missingRequiredPackFields": dict(sorted(missing.items()))}


def intern(payload, ids, writer):
    key = encode(payload)
    found = ids.get(key)
    if found is None:
        found = len(ids)
        ids[key] = found
        writer.write(payload)
    return found


def restore(ref, cores, meanings):
    require(isinstance(ref, dict) and set(ref) == {"t", "c"} and
            isinstance(ref["t"], dict) and "contexts" not in ref["t"] and
            isinstance(ref["c"], list) and ref["c"], "invalid membership reference")
    contexts = []
    for item in ref["c"]:
        require(isinstance(item, list) and len(item) == 3, "invalid context reference")
        ci, mi, link = item
        require(type(ci) is int and 0 <= ci < len(cores) and
                type(mi) is int and 0 <= mi < len(meanings) and isinstance(link, dict),
                "context reference out of range")
        core, meaning = cores[ci], meanings[mi]
        require(not (core.keys() & meaning.keys() or core.keys() & link.keys() or
                     meaning.keys() & link.keys()), "overlapping pooled fields")
        contexts.append({**core, **meaning, **link})
    return {**ref["t"], "contexts": contexts}


def verify_group(base_root, decks, root, metadata):
    # Verification uses serialized IDs/payloads, never the in-memory split input.
    for identity in metadata["files"]:
        require(sha_file(root / identity["file"]) == identity["sha256"], "pooled file hash mismatch")
    cores = list(json_rows(root / "contexts.jsonl.gz"))
    meanings = list(json_rows(root / "meanings.jsonl.gz"))
    require(len(cores) == metadata["uniqueContextPayloads"] and
            len(meanings) == metadata["uniqueMeaningPayloads"], "pool census mismatch")
    count = contexts = origins = 0
    per_deck = {}
    for deck in decks:
        digest = hashlib.sha256()
        n = raw_bytes = 0
        refs = iter(json_rows(root / (deck["deckId"] + ".refs.jsonl.gz")))
        for original in json_rows(base_root / deck["file"]):
            ref = next(refs, None)
            require(ref is not None, "missing serialized membership")
            rebuilt = restore(ref, cores, meanings)
            require(rebuilt == original, "serialized row differs: " + deck["deckId"])
            data = encode(rebuilt)
            digest.update(data)
            raw_bytes += len(data)
            n += 1
            contexts += len(rebuilt["contexts"])
            origins += sum(len(c["origins"]) for c in rebuilt["contexts"])
        require(next(refs, None) is None and n == deck["rows"] and
                raw_bytes == deck["rawBytes"] and digest.hexdigest() == deck["logicalSha256"],
                "serialized deck census/digest mismatch")
        per_deck[deck["deckId"]] = n
        count += n
    return {"memberships": count, "contexts": contexts, "origins": origins,
            "perDeckRowsVerified": per_deck}


def compact_group(base_root, decks, root):
    root.mkdir(parents=True)
    core_ids, meaning_ids = {}, {}
    manifests = []
    contexts = origins = memberships = 0
    with Writer(root / "contexts.jsonl.gz") as cw, Writer(root / "meanings.jsonl.gz") as mw:
        for deck in decks:
            with Writer(root / (deck["deckId"] + ".refs.jsonl.gz")) as writer:
                for row in json_rows(base_root / deck["file"]):
                    refs = []
                    for c in row["contexts"]:
                        core, meaning, link = split_context(c)
                        refs.append([intern(core, core_ids, cw), intern(meaning, meaning_ids, mw), link])
                        contexts += 1
                        origins += len(c["origins"])
                    writer.write({"t": {k: v for k, v in row.items() if k != "contexts"}, "c": refs})
                    memberships += 1
            manifests.append({"deckId": deck["deckId"], **writer.identity()})
    files = [cw.identity(), mw.identity(), *manifests]
    metadata = {"format": "selected-storage-pooled-v1", "publicationSafe": False,
                "uniqueContextPayloads": len(core_ids), "uniqueMeaningPayloads": len(meaning_ids),
                "files": files}
    # Release full canonical-key maps before decoding serialized pools.
    core_ids.clear()
    meaning_ids.clear()
    (root / "group.json").write_bytes(encode(metadata))
    saved = json.loads((root / "group.json").read_bytes())
    verification = verify_group(base_root, decks, root, saved)
    require(verification["memberships"] == memberships and verification["contexts"] == contexts and
            verification["origins"] == origins, "pooled group counts mismatch")
    shared = cw.identity()["bytes"] + mw.identity()["bytes"] + (root / "group.json").stat().st_size
    return {"bytes": shared + sum(m["bytes"] for m in manifests), "sharedBytes": shared,
            "rawSharedBytes": cw.raw_bytes + mw.raw_bytes,
            "uniqueContextPayloads": saved["uniqueContextPayloads"],
            "uniqueMeaningPayloads": saved["uniqueMeaningPayloads"], "verification": verification,
            "decks": [{"deckId": m["deckId"], "referenceBytes": m["bytes"],
                       "coldBytes": shared + m["bytes"],
                       "coldRawBytes": cw.raw_bytes + mw.raw_bytes + m["rawBytes"],
                       "reconstructedSelectedRawBytes": deck["rawBytes"]}
                      for m, deck in zip(manifests, decks)], "files": files}


def measure_mode(base_root, decks, mode, root):
    grouped = defaultdict(list)
    for deck in decks:
        key = (deck["lang"], deck["meaningLang"]) if mode == "pair" else (deck["lang"],)
        grouped[key].append(deck)
    groups = []
    for key, group_decks in sorted(grouped.items()):
        print(f"{mode}: {'-'.join(key)} ({len(group_decks)} decks)", flush=True)
        group = compact_group(base_root, group_decks, root / "-".join(key))
        groups.append({"key": list(key), **group})
    return {"mode": mode, "groups": groups, "bytes": sum(g["bytes"] for g in groups),
            "memberships": sum(g["verification"]["memberships"] for g in groups),
            "contexts": sum(g["verification"]["contexts"] for g in groups),
            "origins": sum(g["verification"]["origins"] for g in groups),
            "maxColdDeckBytes": max(d["coldBytes"] for g in groups for d in g["decks"]),
            "maxColdRawBytes": max(d["coldRawBytes"] for g in groups for d in g["decks"]),
            "maxDecksPerDependency": max(len(g["decks"]) for g in groups)}


def render(result):
    base = result["baseline"]
    lines = ["# Selected Everyday storage measurement", "",
             "Exact accepted selected intermediate; not final packs, installed DB sizes or publication approval.",
             "Every layout is reread from disk and restores every row, field and context order.", "",
             "| Layout | Total MiB | Largest cold download MiB | Largest cold logical MiB |",
             "| --- | ---: | ---: | ---: |",
             f"| Self-contained | {base['bytes']/MIB:.3f} | {base['maxColdDeckBytes']/MIB:.3f} | {base['maxColdRawBytes']/MIB:.3f} |"]
    for mode in result["modes"]:
        lines.append(f"| {mode['mode']} | {mode['bytes']/MIB:.3f} | {mode['maxColdDeckBytes']/MIB:.3f} | {mode['maxColdRawBytes']/MIB:.3f} |")
    lines += ["", "Pool cold logical bytes include the whole dependency plus the deck references;",
              "they are not the reconstructed deck size or measured RAM. Unknown fields and",
              "origin/meaning associations are retained. Language pooling remains diagnostic.", "",
              f"Verified in each layout: {base['memberships']:,} memberships, {base['contexts']:,} contexts, {base['origins']:,} origins.", "",
              "## Decision", "", result["decision"]["reason"], "",
              "The inherited 220 MiB/24 MiB thresholds are exploratory here: Everyday only,",
              "without final token/credit/enrichment payloads. No global release gate is closed.", "",
              "## Reader boundary", "",
              "The selected intermediate is missing required PackChunk fields. Its contexts list",
              "includes the primary; PackChunk.contexts means alternatives only. Do not feed these",
              "experimental files to the existing importer. A pinned no-reselection materializer",
              "needs full function-word rank evidence, canonical-token UTF-16 offsets, tokens,",
              "compatibility source credits and a lossless provenance handoff before client acceptance.", ""]
    return "\n".join(lines)


def run(selected, snapshot_path, output, modes=MODES):
    require(modes and set(modes) <= set(MODES) and len(modes) == len(set(modes)), "invalid modes")
    selected, snapshot_path, output = (p.resolve() for p in (selected, snapshot_path, output))
    require(not output.exists() and not selected.is_relative_to(output) and
            not snapshot_path.is_relative_to(output), "output must be a new directory separate from inputs")
    snapshot_sha = sha_file(snapshot_path)
    snapshot = read_snapshot(snapshot_path)
    require(sha_file(snapshot_path) == snapshot_sha, "snapshot changed while reading")
    # Refuse wrong inputs before creating any output.
    require(sha_file(selected) == snapshot["selectedMaterial"]["sha256"], "selected compressed identity mismatch")
    output.mkdir(parents=True)
    base = prepare_baseline(selected, snapshot, output / "self-contained")
    base["bytes"] = base["assetBytes"] + base["descriptorBytes"]
    base["maxColdDeckBytes"] = max(d["bytes"] for d in base["decks"])
    base["maxColdRawBytes"] = max(d["rawBytes"] for d in base["decks"])
    base["medianDeckBytes"] = int(statistics.median(d["bytes"] for d in base["decks"]))
    measured = [measure_mode(output / "self-contained", base["decks"], mode, output / mode)
                for mode in modes]
    for mode in measured:
        for field in ("memberships", "contexts", "origins"):
            require(mode[field] == base[field], "layout " + field + " mismatch")
    require(sha_file(selected) == snapshot["selectedMaterial"]["sha256"] and
            sha_file(snapshot_path) == snapshot_sha,
            "input changed during measurement")
    pair = next((m for m in measured if m["mode"] == "pair"), None)
    material_benefit = pair is not None and pair["bytes"] <= base["bytes"] * 0.9
    candidate = material_benefit and pair["bytes"] <= 220 * MIB and pair["maxColdDeckBytes"] <= 24 * MIB
    decision = {"layout": "pair-candidate-for-further-pack-measurement" if candidate else "keep-self-contained-candidate",
                "reason": ("Pair pooling has at least 10% intermediate savings and passes exploratory transfer thresholds; final pack/client evidence is still required."
                           if candidate else "Pair pooling does not establish at least 10% savings within exploratory transfer thresholds; retain self-contained gzip as the next pack-measurement candidate."),
                "minimumRelativeSavingsForMigration": 0.1, "publicationSafe": False}
    result = {"format": "selected-storage-measurement-v1", "publicationSafe": False,
              "appReaderAccepted": False, "globalCatalogueMeasured": False,
              "snapshotId": snapshot["snapshotId"], "snapshotSha256": snapshot_sha,
              "selectedMaterial": snapshot["selectedMaterial"],
              "pipelineSha256": {Path(__file__).name: sha_file(Path(__file__))},
              "environment": {"python": platform.python_version(), "zlib": zlib.ZLIB_RUNTIME_VERSION,
                              "gzipLevel": 9, "gzipMtime": 0},
              "baseline": base, "modes": measured, "decision": decision,
              "exploratoryThresholds": {"totalBytes": 220 * MIB, "coldTransferBytes": 24 * MIB,
                                        "scope": "Everyday selected intermediate only; not app-ready or global release gates"}}
    (output / "STORAGE.json").write_bytes(encode(result))
    (output / "STORAGE.md").write_text(render(result), encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selected", required=True, type=Path)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="new directory for files and reports")
    parser.add_argument("--modes", choices=("pair", "language", "pair,language"), default="pair,language")
    args = parser.parse_args()
    try:
        run(args.selected, args.snapshot, args.output, tuple(args.modes.split(",")))
    except (ValueError, OSError, EOFError, KeyError, TypeError, sqlite3.Error) as error:
        parser.exit(1, f"storage measurement failed: {error}\n")
    print(args.output / "STORAGE.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
