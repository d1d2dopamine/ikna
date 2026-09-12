#!/usr/bin/env python3
"""Source registry and licence/provenance gates for Catalogue v2 ingestion."""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

# Deliberately narrow. A new licence is a source-audit change, not a string that
# an adapter is allowed to invent while ingesting data.
ALLOWED_CONTENT_LICENCES = {
    "CC0-1.0",
    "CC-BY-2.0-FR",
    "CC-BY-2.5",
    "CC-BY-3.0",
    "CC-BY-4.0",
    "CC-BY-SA-4.0",
}
ALLOWED_COLLECTIONS = {"everyday", "knowledge", "world"}
ALLOWED_ADAPTERS = {"tatoeba", "wikimatrix", "globalvoices"}
ALLOWED_PUBLICATION_STATUSES = {"ready", "record-attribution-required"}


def _expect_keys(value: dict[str, Any], required: set[str], optional: set[str], where: str) -> None:
    missing = sorted(required - value.keys())
    if missing:
        raise ValueError("%s missing required fields: %s" % (where, ", ".join(missing)))
    unknown = sorted(value.keys() - required - optional)
    if unknown:
        raise ValueError("%s has unknown fields: %s" % (where, ", ".join(unknown)))


def _nonempty_string(value: Any, where: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError("%s must be a non-empty string" % where)
    return value.strip()


def _https_url(value: Any, where: str) -> str:
    text = _nonempty_string(value, where)
    if not text.startswith("https://"):
        raise ValueError("%s must use https://" % where)
    return text


@dataclass(frozen=True)
class SourcePolicy:
    id: str
    title: str
    collection: str
    adapter: str
    homepage: str
    distribution: str
    licence_id: str
    licence_name: str
    licence_url: str
    attribution: str
    audit_status: str
    audit_checked: str
    audit_evidence: tuple[str, ...]
    audit_note: str
    publication_status: str
    required_record_attribution: tuple[str, ...]
    default_source_version: str
    requires_explicit_source_version: bool

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "SourcePolicy":
        required = {
            "id",
            "title",
            "collection",
            "adapter",
            "homepage",
            "distribution",
            "defaultSourceVersion",
            "requiresExplicitSourceVersion",
            "licence",
            "attribution",
            "audit",
            "publication",
        }
        _expect_keys(value, required, set(), "source policy")

        licence = value["licence"]
        audit = value["audit"]
        publication = value["publication"]
        if not isinstance(licence, dict) or not isinstance(audit, dict) or not isinstance(publication, dict):
            raise ValueError("source policy nested licence/audit/publication fields must be objects")
        _expect_keys(licence, {"id", "name", "url"}, set(), "source licence")
        _expect_keys(audit, {"status", "checked", "evidence", "note"}, set(), "source audit")
        _expect_keys(publication, {"status", "requiredRecordAttribution"}, set(), "source publication")

        evidence = audit["evidence"]
        if not isinstance(evidence, list) or not evidence:
            raise ValueError("source audit evidence must be a non-empty list")
        required_attribution = publication["requiredRecordAttribution"]
        if not isinstance(required_attribution, list):
            raise ValueError("requiredRecordAttribution must be a list")
        requires_explicit = value["requiresExplicitSourceVersion"]
        if not isinstance(requires_explicit, bool):
            raise ValueError("requiresExplicitSourceVersion must be boolean")

        return cls(
            id=_nonempty_string(value["id"], "source id"),
            title=_nonempty_string(value["title"], "source title"),
            collection=_nonempty_string(value["collection"], "source collection"),
            adapter=_nonempty_string(value["adapter"], "source adapter"),
            homepage=_https_url(value["homepage"], "source homepage"),
            distribution=_https_url(value["distribution"], "source distribution"),
            licence_id=_nonempty_string(licence["id"], "source licence id"),
            licence_name=_nonempty_string(licence["name"], "source licence name"),
            licence_url=_https_url(licence["url"], "source licence URL"),
            attribution=_nonempty_string(value["attribution"], "source attribution"),
            audit_status=_nonempty_string(audit["status"], "source audit status"),
            audit_checked=_nonempty_string(audit["checked"], "source audit checked date"),
            audit_evidence=tuple(_https_url(item, "source audit evidence") for item in evidence),
            audit_note=_nonempty_string(audit["note"], "source audit note"),
            publication_status=_nonempty_string(publication["status"], "source publication status"),
            required_record_attribution=tuple(
                _nonempty_string(item, "required record attribution field") for item in required_attribution
            ),
            default_source_version=_nonempty_string(value["defaultSourceVersion"], "defaultSourceVersion"),
            requires_explicit_source_version=requires_explicit,
        )

    def validate_static(self) -> None:
        if self.licence_id not in ALLOWED_CONTENT_LICENCES:
            raise ValueError("source %s uses unapproved licence %s" % (self.id, self.licence_id))
        if self.collection not in ALLOWED_COLLECTIONS:
            raise ValueError("source %s uses unknown collection %s" % (self.id, self.collection))
        if self.adapter not in ALLOWED_ADAPTERS:
            raise ValueError("source %s uses unknown adapter %s" % (self.id, self.adapter))
        if self.audit_status != "approved":
            raise ValueError("source %s licence audit is not approved" % self.id)
        if self.publication_status not in ALLOWED_PUBLICATION_STATUSES:
            raise ValueError("source %s has unknown publication status %s" % (self.id, self.publication_status))
        if self.publication_status == "ready" and self.required_record_attribution:
            raise ValueError("source %s is ready but also requires record attribution" % self.id)
        if self.publication_status == "record-attribution-required" and not self.required_record_attribution:
            raise ValueError("source %s requires record attribution but declares no fields" % self.id)

    def resolve_source_version(self, supplied: str | None) -> str:
        if supplied is not None and supplied.strip():
            return supplied.strip()
        if self.requires_explicit_source_version:
            raise ValueError(
                "source %s requires an explicit source version for reproducible ingestion" % self.id
            )
        return self.default_source_version

    def validate_origin_for_publication(self, origin: dict[str, Any]) -> None:
        self.validate_static()
        if origin.get("sourceFamily") != self.id:
            raise ValueError("origin/source policy mismatch")
        if not origin.get("sourceVersion"):
            raise ValueError("source %s origin has no source version" % self.id)
        if not origin.get("contextRef") or not origin.get("meaningRef"):
            raise ValueError("source %s origin has no stable source references" % self.id)
        if self.publication_status == "record-attribution-required":
            attribution = origin.get("attribution") or {}
            missing = [name for name in self.required_record_attribution if not attribution.get(name)]
            if missing:
                raise ValueError(
                    "source %s origin lacks required attribution: %s"
                    % (self.id, ", ".join(missing))
                )
            for name in self.required_record_attribution:
                item = attribution[name]
                if name.lower().endswith("url"):
                    _https_url(item, "%s attribution %s" % (self.id, name))
                elif name == "contributors":
                    if not isinstance(item, list) or not item or not all(
                        isinstance(part, str) and part.strip() for part in item
                    ):
                        raise ValueError("source %s contributors must be a non-empty string list" % self.id)


class SourceRegistry:
    def __init__(self, version: int, policies: dict[str, SourcePolicy]):
        self.version = version
        self.policies = policies

    @classmethod
    def load(cls, path: str) -> "SourceRegistry":
        with open(path, encoding="utf-8") as handle:
            raw = json.load(handle)
        if not isinstance(raw, dict):
            raise ValueError("source registry must be an object")
        _expect_keys(raw, {"registryVersion", "sources"}, set(), "source registry")
        if raw.get("registryVersion") != 1:
            raise ValueError("unsupported source registry version")
        if not isinstance(raw.get("sources"), list):
            raise ValueError("source registry sources must be a list")
        policies: dict[str, SourcePolicy] = {}
        for value in raw["sources"]:
            if not isinstance(value, dict):
                raise ValueError("source registry entry must be an object")
            policy = SourcePolicy.from_dict(value)
            if policy.id in policies:
                raise ValueError("duplicate source id %s" % policy.id)
            policies[policy.id] = policy
        registry = cls(raw["registryVersion"], policies)
        registry.validate()
        return registry

    def validate(self) -> None:
        if not self.policies:
            raise ValueError("source registry is empty")
        for policy in self.policies.values():
            policy.validate_static()

    def get(self, source_id: str) -> SourcePolicy:
        try:
            return self.policies[source_id]
        except KeyError as exc:
            raise ValueError("unknown source family %s" % source_id) from exc
