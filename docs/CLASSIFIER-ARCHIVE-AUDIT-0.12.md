# Classic Bicleaner en–de archive audit

Observed 2026-10-11 UTC / 2026-10-12 Asia/Yekaterinburg.
Bounded, metadata-only addendum to [round-28 feasibility](CLASSIFIER-FEASIBILITY-0.12.md).
The original audit left source untouched while the full saved-input profile ran.
This round-30 documentation-only integration is dated 2026-10-12 UTC, after that
profile completed. It changes no runtime/tooling, selection policy or app visuals.

## Decision

**Keep classifier integration and the scored comparison deferred.** The specific
archive-inspection unknown is now resolved: one official archive has been
acquired, content-pinned, completely enumerated and its small model YAML read.
This does **not** resolve per-resource or underlying-corpus provenance, independent
benchmark permissions, runtime feasibility or incremental quality benefit.
The existing rules, source registry, 12,000 cap and 40/1,000 supply thresholds are
unchanged. No semantic score or policy change was produced.

## Exact official artifact

- Repository/release: `bitextor/bicleaner-data`, classic `v1.6`.
- Asset ID: `88211159`, name `en-de.tar.gz`.
- Official metadata: https://api.github.com/repos/bitextor/bicleaner-data/releases/assets/88211159
- Official download: https://github.com/bitextor/bicleaner-data/releases/download/v1.6/en-de.tar.gz
- Exact received bytes: **523,179,851**, matching both live GitHub metadata and
  response Content-Length.
- SHA-256: `c932449563ef0108757382d7d2de2dae2bdd97f78cf9dcee3fcf734a80c5b98d`.
- Publisher digest remains `null`; the local hash pins observed bytes, rather than
  providing an independent publisher-signed checksum.
- HTTPS redirect stayed on GitHub's release asset host. Response dates, ETag and
  Content-Disposition are retained; its temporary signed query was not saved.
- Raw archive retained only in the audit's external input workspace. It is not
  included in this repository/evidence package. Do not add it to a source ZIP,
  catalogue, attachment or other redistribution.

## Complete member inventory

All paths are under `en-de/`. There is one directory and the following 14 regular
files. There are no symlinks, hardlinks, sparse entries, duplicate normalized
names, absolute paths or parent-traversal paths in the inspected inventory.
All files have mode `0664`; none has an executable mode. Model data must still be
treated as potentially executable when deserialized, regardless of file mode.

| Member | Tar-stored bytes | Role/evidence | Contents inspected |
| --- | ---: | --- | --- |
| `en-de.yaml` | 1,182 | Model metadata | UTF-8 text only |
| `en-de.classifier` | 274,819,224 | YAML classifier path | No |
| `dict-en.gz` | 27,451,611 | YAML source dictionary | No |
| `dict-de.gz` | 46,150,807 | YAML target dictionary | No |
| `wordfreq-en.gz` | 1,566,172 | YAML source frequencies | No |
| `wordfreq-de.gz` | 3,666,068 | YAML target frequencies | No |
| `model.lm.en` | 58,232,663 | YAML source LM | No |
| `model.lm.de` | 62,336,166 | YAML target LM | No |
| `porn-model.en` | 1,213,624 | YAML content-model path | No |
| `train.en-de` | 30,758,021 | Name suggests training material; contents unverified | No |
| `porn-removal.txt.en` | 18,925,337 | Name suggests auxiliary text; contents unverified | No |
| `porn-removal.en.ftz` | 1,213,624 | Unreferenced auxiliary filename | No |
| `lm.en` | 58,247,369 | Unreferenced auxiliary filename | No |
| `lm.de` | 62,357,443 | Unreferenced auxiliary filename | No |

Total regular-file bytes: **646,939,311**. Complete decompressed gzip/tar stream:
**646,952,960** bytes, including tar headers/padding. Gzip integrity/end-of-stream
was checked. These are archive layout sizes, not decoded nested dictionaries or
runtime RAM. Matching lengths of similarly named auxiliary files do not establish
identical contents; no duplicate-content claim is made.

The full archive necessarily contains the two text-suggestive entries above.
They were not extracted, read as sentences, ingested or used as a new corpus.
No resource payload was opened for interpretation, unpickled, installed or run.
The compressed archive was hashed as opaque bytes and non-metadata tar payloads
were skipped during the streaming inventory. No per-resource payload hash is
claimed. Before any authorized future extraction/execution, compute and record
those individual hashes in the isolated workflow.

## YAML findings

The only metadata file is `en-de/en-de.yaml`, 1,182 bytes, SHA-256
`7e8e201e4ddcb362cd0c377396f3574571895694d51716ecd26ee9f400e65730`.
It was read as inert UTF-8 text, not loaded through an object-constructing YAML
parser. All eight referenced resource paths exist as regular members.

- `classifier_type: extra_trees`; `source_lang: en`; `target_lang: de`;
  `features_version: 4`.
- `normalize_by_length: True`, `treat_oovs: False`, `qmax_limit: 40`,
  `disable_features_quest: True`, `length_ratio: 0.8337820`.
- YAML references both dictionaries and both word-frequency files, two character
  LMs (`model.lm.en`, `model.lm.de`), and `porn-model.en` with side `sl`.
  `disable_lang_ident` is `False`.
- Internal test histograms total 5,000 `good` and 4,998 `wrong` observations and
  include publisher precision/recall/accuracy arrays. Dataset identity, label
  construction, split and overlap are undocumented in this YAML. These arrays
  are not an independent ikna/Tatoeba accuracy result or a calibrated policy.
- No licence, attribution, source-corpus inventory, training-data version, split
  date, tokenizer/version or training-overlap field appears in the YAML. The
  tokenizer/version statement remains release-level evidence from the earlier
  publisher inspection, not a pack-embedded contract.

The YAML plus classifier, dictionaries and frequencies account for 353,655,064
member bytes. Adding every YAML-referenced auxiliary resource totals 475,437,517
bytes. Neither number establishes a safe minimum runtime installation: previous
source review found eager loading and Hardrules initialization, while nested
`.gz` dictionaries remain compressed at these measured sizes. Startup/RSS,
execution time, disabled-helper behavior and deterministic repeatability are
still unmeasured.

## Notices and provenance

**No standalone LICENSE, COPYING, NOTICE, README, attribution or source-corpus
inventory appears in the complete archive inventory.** The YAML also contains
none of that provenance information. Embedded notices in unread binary/resource
payloads were not searched and are not ruled out.

The separate repository README and GPL-3.0 text were re-fetched from pinned
commit `c9e9f0d08e40f01d47b5afb165ed032b25bad7c1` and preserved:

- README, 164 bytes, SHA-256
  `2f65a897157a87f4442ae73835ae6eb5433d0479eb372219d6cea215bc1c98fb`:
  https://raw.githubusercontent.com/bitextor/bicleaner-data/c9e9f0d08e40f01d47b5afb165ed032b25bad7c1/README.md
- LICENSE, 35,149 bytes, SHA-256
  `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`:
  https://raw.githubusercontent.com/bitextor/bicleaner-data/c9e9f0d08e40f01d47b5afb165ed032b25bad7c1/LICENSE

This is positive repository-level licensing evidence for a repository presenting
trained resources. It is not a per-file source-corpus inventory and does not show
that all underlying training texts may be redistributed as ikna cards. The
archive audit found no additional notice resolving those questions. No claim
that use is unlawful is made. Under ikna's conservative gates, resource
provenance/rights remain unresolved rather than silently inferred from GPL.

## Resource and execution boundaries

A single low-priority process downloaded the archive, with a hard 600,000,000-byte
ceiling and 256 MiB address-space ceiling. Download duration was 25.562 seconds;
observed maximum RSS was 18,128 KiB. The separate sequential streaming inspection
used a 256 MiB address-space ceiling, 4 GiB decompressed-stream ceiling, 2 MiB
metadata-per-file ceiling, 8 MiB total metadata ceiling, 10,000-member ceiling and
300 CPU-second soft limit. It took 2.149 seconds, with 12,812 KiB maximum RSS.
Only 1,182 bytes of archive metadata were materialized. Download and inspection
ran sequentially alongside the full saved-input profile, which retained priority.
These small concurrent footprints do not prove zero full-profile slowdown or
establish an isolated machine timing measurement. These figures describe
the audit tools, **not classifier loading or inference**. Free disk after the
download was approximately 15 GiB. No large parallel work was launched.

No dependencies, model execution, agreements, account actions, external contact,
other packs, source-corpus collection, catalogue admission or publication were
performed. No ikna repository/app/visual changes were made during the audit itself. This
follow-up adds only the requested documentation and compact metadata evidence.

## Exact follow-on status

Closed: the proposed single-archive acquisition/inventory/YAML-inspection gate,
with a content hash, byte-exact evidence and observed tar layout size.

Still open before a scored experiment or integration:

1. Resolve resource/source-corpus provenance and applicable permissions without
   assuming that repository GPL grants every original-text right.
2. Freeze dependencies, then separately authorize and measure an isolated offline
   one-process startup and bounded scoring trial. Current metadata-only approval
   does not authorize unpickling or running this model.
3. Resolve the existing independent-label original-text permissions, unknown
   training overlap and transfer limitations. The 300-pair manifest remains
   unscored and is not newly admitted card data.
4. Measure incremental defect detection and accepted good-material loss before
   changing policy. YAML histograms cannot substitute for this experiment.
5. Keep the established direct-pack coverage limitation: at most 16/110 learner
   directions; 94/110 unsupported, including no Japanese/Korean/Chinese classic
   packs in the previously reviewed releases. This audit adds no new coverage.

## Compact source evidence

The [round-30 evidence directory](evidence/classifier-archive-round30/README.md)
contains only small factual records and inert metadata:

- [Archive provenance, inventory, hashes and resource measurements](evidence/classifier-archive-round30/archive-evidence.json).
- [Original inert model YAML](evidence/classifier-archive-round30/en-de.metadata.yaml).
- [Publisher provenance and licensing limitations](evidence/classifier-archive-round30/publisher-provenance.json).
- [Unmodified publisher GPL-3.0 text](evidence/classifier-archive-round30/publisher-LICENSE).
- [Machine-readable feasibility conclusion](evidence/classifier-archive-round30/feasibility-conclusion.json).
- [Evidence checksums](evidence/classifier-archive-round30/checksums.sha256).

No raw model, dictionary, frequency, auxiliary model or corpus payload accompanies
these documents. Original procedure hashes are recorded for traceability; adding
this evidence does not rerun a download, install dependencies or execute models.
The earlier round-28 report remains a historical checkpoint, with a dated
[follow-up section](CLASSIFIER-FEASIBILITY-0.12.md#round-30-follow-up--single-archive-inspection)
recording exactly what this audit changed.
