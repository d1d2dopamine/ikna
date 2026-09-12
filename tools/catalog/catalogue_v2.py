#!/usr/bin/env python3
"""Small reference helpers for the Catalogue v2 content contract.

This module does not build the catalogue. It freezes the deterministic identity
rules used by the v2 fixtures so later ingestion code does not invent a second
implementation.
"""

import hashlib
import unicodedata

TARGET_IDENTITY_VERSION = 2
TARGET_IDENTITY_METHOD = "nfkc-casefold-exact"


def canonical_target(text):
    """Canonical exact-target key used by the first v2 identity method."""
    return unicodedata.normalize("NFKC", text).casefold()


def target_id(language, text, version=TARGET_IDENTITY_VERSION):
    """Compact opaque id for an exact target in one learning language."""
    lang = language.strip().lower()
    if not lang:
        raise ValueError("language is empty")
    canonical = canonical_target(text)
    if not canonical:
        raise ValueError("target text is empty")
    digest = hashlib.sha256((lang + "\0" + canonical).encode("utf-8")).hexdigest()[:16]
    return "t%d:%s:%s" % (version, lang, digest)
