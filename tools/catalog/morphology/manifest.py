#!/usr/bin/env python3
"""Pinned dataset manifests and licence gates for morphology inputs."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .model import MORPHOLOGY_RULE_VERSION

ALLOWED_LICENCES = {
    "CC0-1.0",
    "CC-BY-2.0",
    "CC-BY-3.0",
    "CC-BY-4.0",
    "CC-BY-SA-3.0",
    "CC-BY-SA-4.0",
}
ALLOWED_SOURCE_FAMILIES = {"unimorph", "universal-dependencies"}
ALLOWED_KINDS = {"unimorph", "ud"}


@dataclass(frozen=True)
class DatasetFile:
    path: Path
    sha256: str


@dataclass(frozen=True)
class MorphDataset:
    id: str
    source_family: str
    kind: str
    lang: str
    source_version: str
    source_url: str
    licence_id: str
    licence_name: str
    licence_url: str
    attribution: str
    files: tuple[DatasetFile, ...]

    def public_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "sourceFamily": self.source_family,
            "kind": self.kind,
            "lang": self.lang,
            "sourceVersion": self.source_version,
            "sourceUrl": self.source_url,
            "licence": self.licence_name,
            "licenceUrl": self.licence_url,
            "attribution": self.attribution,
        }


@dataclass(frozen=True)
class MorphManifest:
    path: Path
    datasets: tuple[MorphDataset, ...]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _required(value: dict[str, Any], name: str, where: str) -> str:
    item = value.get(name)
    if not isinstance(item, str) or not item.strip():
        raise ValueError("%s requires non-empty %s" % (where, name))
    return item.strip()


def load_manifest(path: str | Path, verify_hashes: bool = True) -> MorphManifest:
    manifest_path = Path(path).resolve()
    value = json.loads(manifest_path.read_text(encoding="utf-8"))
    if value.get("manifestVersion") != 1:
        raise ValueError("unsupported morphology manifestVersion")
    if value.get("ruleVersion") != MORPHOLOGY_RULE_VERSION:
        raise ValueError("morphology manifest ruleVersion disagrees with the code")
    rows = value.get("datasets")
    if not isinstance(rows, list) or not rows:
        raise ValueError("morphology manifest needs at least one dataset")

    seen: set[str] = set()
    datasets: list[MorphDataset] = []
    for number, row in enumerate(rows, start=1):
        if not isinstance(row, dict):
            raise ValueError("morphology dataset %d must be an object" % number)
        where = "morphology dataset %d" % number
        dataset_id = _required(row, "id", where)
        if dataset_id in seen:
            raise ValueError("duplicate morphology dataset id %s" % dataset_id)
        seen.add(dataset_id)
        source_family = _required(row, "sourceFamily", where)
        kind = _required(row, "kind", where)
        lang = _required(row, "lang", where).lower()
        if source_family not in ALLOWED_SOURCE_FAMILIES:
            raise ValueError("%s uses unaudited source family %s" % (dataset_id, source_family))
        if kind not in ALLOWED_KINDS:
            raise ValueError("%s has unsupported kind %s" % (dataset_id, kind))
        if (source_family == "unimorph") != (kind == "unimorph"):
            raise ValueError("%s sourceFamily/kind disagree" % dataset_id)
        if (source_family == "universal-dependencies") != (kind == "ud"):
            raise ValueError("%s sourceFamily/kind disagree" % dataset_id)
        if len(lang) < 2:
            raise ValueError("%s has invalid language code" % dataset_id)

        licence = row.get("licence")
        if not isinstance(licence, dict):
            raise ValueError("%s requires a licence object" % dataset_id)
        licence_id = _required(licence, "id", dataset_id + " licence")
        if licence_id not in ALLOWED_LICENCES:
            raise ValueError("%s uses disallowed or unaudited licence %s" % (dataset_id, licence_id))

        file_rows = row.get("files")
        if not isinstance(file_rows, list) or not file_rows:
            raise ValueError("%s needs at least one pinned file" % dataset_id)
        files: list[DatasetFile] = []
        for file_number, file_row in enumerate(file_rows, start=1):
            if not isinstance(file_row, dict):
                raise ValueError("%s file %d must be an object" % (dataset_id, file_number))
            rel = _required(file_row, "path", "%s file %d" % (dataset_id, file_number))
            expected = _required(file_row, "sha256", "%s file %d" % (dataset_id, file_number)).lower()
            if len(expected) != 64 or any(ch not in "0123456789abcdef" for ch in expected):
                raise ValueError("%s file %d has invalid sha256" % (dataset_id, file_number))
            file_path = (manifest_path.parent / rel).resolve()
            if not file_path.is_file():
                raise ValueError("%s file does not exist: %s" % (dataset_id, file_path))
            if verify_hashes:
                actual = _sha256(file_path)
                if actual != expected:
                    raise ValueError("%s hash mismatch for %s" % (dataset_id, file_path.name))
            files.append(DatasetFile(path=file_path, sha256=expected))

        datasets.append(
            MorphDataset(
                id=dataset_id,
                source_family=source_family,
                kind=kind,
                lang=lang,
                source_version=_required(row, "sourceVersion", where),
                source_url=_required(row, "sourceUrl", where),
                licence_id=licence_id,
                licence_name=_required(licence, "name", dataset_id + " licence"),
                licence_url=_required(licence, "url", dataset_id + " licence"),
                attribution=_required(row, "attribution", where),
                files=tuple(files),
            )
        )
    return MorphManifest(path=manifest_path, datasets=tuple(datasets))
