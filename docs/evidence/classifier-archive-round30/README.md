# Classic Bicleaner archive evidence, round 30

Metadata-only audit observed 2026-10-11 23:03–23:04 UTC
(2026-10-12 Asia/Yekaterinburg); integrated into source docs 2026-10-12 UTC.
See [the full report](../../CLASSIFIER-ARCHIVE-AUDIT-0.12.md) and the
[dated feasibility follow-up](../../CLASSIFIER-FEASIBILITY-0.12.md#round-30-follow-up--single-archive-inspection).

This compact directory contains documentation, inert YAML and publisher licence
text only. No archive, classifier weights, dictionaries, frequency lists,
language/content models, training sentences or other corpus payloads are included.

- `archive-evidence.json`: exact official asset identity, observed SHA-256 and
  bytes, HTTP/source metadata, complete safe member inventory, bounded method,
  audit-process RSS/timing and concurrent-profile measurement caveats.
- `en-de.metadata.yaml`: the only archive metadata file, copied byte-for-byte as
  inert UTF-8 text. Do not treat its resource references as runnable paths here.
- `publisher-provenance.json`: pinned publisher URLs, observed hashes and the
  complete small publisher README; separates repository GPL from corpus rights.
- `publisher-LICENSE`: unmodified GPL-3.0 text from the pinned publisher repository.
  It is not a licence newly found inside the model archive.
- `feasibility-conclusion.json`: exact resolved inspection gate, resource mapping,
  remaining rights/evaluation/runtime gates and unchanged deferral decision.
- `checksums.sha256`: hashes of every other file in this evidence directory.

The archive hash pins locally observed official bytes; GitHub supplies no digest.
No standalone file-level notices/source inventory were found. Notices embedded in
unread payloads remain unknown. Internal YAML accuracy histograms are publisher
metadata, not an independent ikna quality result. Source admission, production
rules and the existing benchmark restrictions remain unchanged.

The audit's 18,128 KiB download RSS and 12,812 KiB inspection RSS were measured in
separate low-priority processes alongside the full saved-input profile. Those
figures are neither model startup/inference measurements nor proof that concurrent
activity had zero effect on full-profile elapsed time. No new download or model
execution was performed to integrate this evidence into the repository.
