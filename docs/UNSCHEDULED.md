# 🧭 Unscheduled ideas

This file is deliberately **not a roadmap**.

Items here have no target release, no target date and no implied commitment. They
may be implemented later, rewritten completely, or rejected without a deprecation
process. Moving an idea here means only that it was worth recording; it does not
mean that 0.11 or any later release owes the feature.

## 🛟 Local-history resilience

The project already has explicit export/restore paths. A separate future idea is
to add automatic local safety snapshots before operations that can replace or
remove a large amount of learner data, such as a restore, destructive import or
future database maintenance operation.

This should only be pursued if it solves a demonstrated failure mode. It must not
turn into cloud backup, an account system or telemetry.

## 🔎 Local diagnostics

A small local-only diagnostics surface could expose facts useful while debugging
an installation, for example:

- database/schema version;
- installed deck and target counts;
- review-row count;
- last successful planning/scheduler pass;
- active Catalogue generation;
- local database size.

Nothing in this idea requires sending diagnostics anywhere. It is optional and
may be rejected if ordinary logs and exports remain sufficient.

## ⚙️ Settings audit

At some future checkpoint, review settings for controls whose original ownership
has moved into Governor, Scheduler or automatic grading policy. Remove or rewrite
only controls that are demonstrably stale; do not delete user choice merely to
make the settings screen smaller.

## 🔗 Shared-target UX audit

Catalogue v2 lets one learning target belong to multiple decks while retaining
one learner memory. A later UI audit could check that deck deletion, progress
labels and per-deck counters never imply that those memberships are independent
memories.

This is a presentation audit, not permission to change target identity.

## 🌱 First-run explanation

If real users are confused by the small initial batch, consider a minimal
explanation that it is an adaptive starting point rather than a daily quota.
Avoid multi-page onboarding unless evidence shows it is needed.

## 📦 Catalogue-download states

The client could distinguish download, verification, decompression and import
failures more explicitly when installing Catalogue assets. This is useful only if
current error messages prove insufficient in real use.

## 🧩 Widget compatibility matrix

Continue testing the Android widget on the smallest declared size, large system
font scales and several launcher implementations. Treat launcher-specific layout
work as maintenance, not as a redesign project.

## 🚫 What this file is not

This file must not be used to smuggle work into a release. In particular:

- an item here does not block 0.11;
- an item here does not gain priority merely by being old;
- an item here may be deleted if later evidence says it is unnecessary;
- scheduled Context/Transfer Policy work remains in the 0.11 roadmap rather than
  being duplicated here.
