# Planned Future Milestone: Rust Core (`ikna-core`)

> **Status:** Deferred / Planned for the major architectural refactor. Not scheduled for the current `0.12` cycle.

## Motivation

The sole product motivation for evaluating a Rust core in `ikna` is **execution speed and low runtime overhead** on compute-heavy offline paths:

- Local FSRS-6 parameter fitting (gradient-based optimization over multi-thousand review histories).
- Large deck imports (e.g., Anki `.apkg` archives with tens of thousands of notes and cards).
- Incremental sync journal diffing, serialization, and authenticated encryption.
- Reduced garbage-collection pauses and memory pressure on constrained Android devices.

## Why Deferred Until the Major Refactor

The current shared Kotlin engine in `shared/src/jvmShared` compiles cleanly for Android, Windows, and Linux with full automated CI coverage. Introducing a Rust FFI boundary prematurely would multiply build/CI complexity (`cargo-ndk`, Android ABI `.so` packaging across `arm64-v8a` / `armeabi-v7a`, Windows `.dll`, Linux `.so`, and UniFFI bindings) while feature work and bug cleanup are still moving rapidly.

Therefore, the Rust core migration is intentionally paired with the planned large-scale refactor that will also consolidate accumulated architectural debt and edge-case bugs.

## Proposed Phased Scope (When Scheduled)

1. **Benchmark gate first:** Establish reproducible benchmarks on real review logs and large `.apkg` fixtures comparing the existing Kotlin implementation against a Rust prototype before committing to FFI maintenance.
2. **Phase 1 — Pure math crate (`ikna-core` via UniFFI):** Move stateless, compute-bound modules first:
   - FSRS-6 scheduler and local optimizer (`FSRS-OPTIMIZER.md`).
   - Load governor (`riar-manage`, `GOVERNOR.md`).
   - Deterministic sync journal codec / delta merge primitives.
3. **Phase 2 — Heavy parsers:** Move streaming `.apkg` / `.ikna` import/export parsers if benchmarks show material wall-time or memory wins.
4. **Keep UI & platform shells native:** Compose Multiplatform UI, Android/Desktop audio, file pickers, and OS integration remain in Kotlin.

## Acceptance Criteria Before Shipping

- Measurable speed or memory improvement over the Kotlin baseline on target devices.
- Reproducible CI builds across Android (64-bit + legacy 32-bit), Windows x64, and Linux AppImage without breaking F-Droid or offline build rules.
- Full parity with existing unit, migration, and grading contract tests.
