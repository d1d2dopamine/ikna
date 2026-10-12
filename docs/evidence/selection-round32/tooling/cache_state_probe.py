#!/usr/bin/env python3
"""Fresh-process cache neutrality fixture; only ordinary public policy calls."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
REPO = Path(os.environ.get("IKNA_TEST_REPO", HERE.parent.parent / "ikna-work")).resolve()
sys.dont_write_bytecode = True
sys.path.insert(0, str(REPO / "tools" / "catalog"))
import selection_policy as policy
import overlap_reuse


def main(mode):
    choose = {"original": policy.choose_contexts, "candidate": overlap_reuse.choose_contexts}[mode]
    initial = policy._written_counts.cache_info()._asdict()
    rows = [{"candidateId": str(i), "contextId": str(i), "context": f"Observation {chr(0x410+i)} {chr(0x450+i)} different words.",
             "sourceFamilies": ["tatoeba"], "alignmentScore": None, "tokenCount": 10} for i in range(37)]
    outputs, phases = [], []
    for phase in range(5):
        if phase:
            # Natural overlap calls populate/evict the bounded existing token
            # cache; no cache_clear, cache replacement or private mutation.
            for i in range(8170 if phase % 2 else 8300):
                policy.context_overlap(f"Fixture cache traffic phase {phase} item {i}.", "Fixed independent anchor.", "en")
        chosen, rejected = choose(rows, 12, .85, diverse=True, lang="en")
        outputs.append({"selected": chosen, "rejected": rejected})
        phases.append({"phase": phase, "cache": policy._written_counts.cache_info()._asdict()})
        rows = list(reversed(rows))
    raw = json.dumps(outputs, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
    print(json.dumps({"mode": mode, "initialCache": initial, "phases": phases, "outputs": outputs,
                      "outputSha256": hashlib.sha256(raw).hexdigest(), "privateCacheMutations": False}, sort_keys=True))


if __name__ == "__main__":
    main(sys.argv[1])
