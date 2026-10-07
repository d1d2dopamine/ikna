"""Atomic, deterministic full selection handoff. Rows remain memberships."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import tempfile


class SelectionOutput:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        handle = tempfile.NamedTemporaryFile(dir=self.path.parent, prefix="selected-", suffix=".tmp", delete=False)
        self.temp = Path(handle.name)
        self.raw = handle
        self.gzip = gzip.GzipFile(fileobj=handle, filename="", mode="wb", compresslevel=6, mtime=0)
        self.logical = hashlib.sha256()
        self.logical_bytes = self.contexts = self.origins = 0
        self.decks = Counter()

    def add(self, row):
        raw = (json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n").encode("utf-8")
        self.logical.update(raw); self.logical_bytes += len(raw)
        self.gzip.write(raw)
        self.decks[row["deckId"]] += 1
        self.contexts += len(row["contexts"])
        self.origins += sum(len(c["origins"]) for c in row["contexts"])

    def finish(self):
        self.gzip.close(); self.raw.close()
        with self.temp.open("rb") as handle:
            digest = hashlib.file_digest(handle, "sha256").hexdigest()
        identity = {"format": "selected-memberships-jsonl-gzip-v1", "sha256": digest,
                    "sizeBytes": self.temp.stat().st_size, "logicalSha256": self.logical.hexdigest(),
                    "logicalSizeBytes": self.logical_bytes, "memberships": sum(self.decks.values()),
                    "contexts": self.contexts, "origins": self.origins, "decks": dict(sorted(self.decks.items())),
                    "completeIncludedDecisions": True, "publicationSafe": False}
        self.temp.replace(self.path)
        return identity

    def abort(self):
        self.gzip.close(); self.raw.close(); self.temp.unlink(missing_ok=True)
