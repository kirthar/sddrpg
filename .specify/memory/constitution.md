<!--
Sync Impact Report
- Version change: (template) → 1.0.0
- Modified principles: n/a (initial ratification)
- Added sections:
  - Core Principles (I–V)
  - Module Architecture & Technology Constraints
  - Non-Goals (v1) & Development Workflow
  - Governance
- Removed sections: none
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (generic Constitution Check gate — compatible as-is)
  - ✅ .specify/templates/spec-template.md (no constitution-specific sections required)
  - ✅ .specify/templates/tasks-template.md (test-first task ordering already supported)
- Follow-up TODOs: none
-->

# Turn-Based Combat Engine (sddrpg) Constitution

## Core Principles

### I. Pure & Deterministic Core

The `core` module MUST be pure Kotlin Multiplatform common code (`commonMain`) with no
platform, UI, I/O, or framework dependencies (kotlinx.serialization and the Kotlin stdlib
are the only permitted dependencies). All randomness MUST flow through a single injected,
seeded RNG abstraction; no combat-affecting code may call platform random sources, clocks,
or any other non-deterministic API. Given the same initial battle configuration, seed, and
sequence of submitted decisions, the engine MUST produce an identical sequence of states
and events.

Rationale: determinism makes every battle replayable and every bug reproducible, enables
property-based testing, and keeps the engine portable to any platform or frontend.

### II. Data-Driven Content — Definitions Are Data, Not Code

All game content — character classes, skills, spells, status effects, summons, limit
breaks, elemental affinities, enemy definitions — MUST be expressed as serializable data
(kotlinx.serialization) interpreted by the engine. Specifically: combatants, character
classes, and enemy types/roles are data definitions; adding a new class, enemy, or skill
MUST NOT require a new code subclass or an engine change. Code implements *capabilities*
(damage formulas, targeting strategies, AI strategies, effect primitives) that data
composes by reference. Enemy behavior variation MUST be modeled as data selecting among
Strategy implementations, never as one class per enemy.

Rationale: this is the reuse contract of the engine — different games ship different data
against the same core, and content iteration never destabilizes engine code.

### III. Test-First Development (NON-NEGOTIABLE)

Every feature follows the Spec Kit flow (specify → clarify → plan → tasks → implement)
before code is written. Within implementation, tests come first: acceptance criteria from
the spec become failing kotest tests in `commonTest` before the behavior is implemented.
Deterministic unit tests MUST cover every resolution rule; property-based tests
(kotest-property) MUST cover invariants (e.g., HP bounds, determinism under identical
seeds, conservation rules). A task is not done while any test is red.

Rationale: a rules engine is only trustworthy if its rules are executable specifications;
determinism (Principle I) exists precisely so tests can assert exact outcomes.

### IV. Simulation–Presentation Separation

The core exposes battle progression exclusively as immutable-state snapshots plus a typed
event stream (Observer/event bus). It MUST NOT render, format user-facing text, localize,
or read input. Decision-making is abstracted behind a decision-source interface so that
human input (demo UIs) and AI (Strategy implementations) are interchangeable per
combatant; the engine treats party members, enemies, and temporary AI allies uniformly in
resolution rules regardless of who decides their actions. Frontends (`demo-console`,
later `demo-app`) consume state + events only through the public core API.

Rationale: the same simulation must drive a console demo today and Compose Multiplatform
web/Android frontends tomorrow, without the engine knowing either exists.

### V. Extensible Architecture Without Speculative Implementation

Abstractions MUST keep future modes possible; implementations are built only when a
current milestone needs them. Concretely: turn ordering lives behind a `TurnScheduler`
abstraction (v1 implements classic/ATB only, but the contract must not preclude
phase-based scheduling à la Fire Emblem); the combatant/class data model MUST leave room
for class progression (job change/promotion) without core rewrites, while v1 ships fixed
classes; grid positioning is reserved to the separate `tactical` module slot and MUST NOT
leak positional concepts into `core`. YAGNI applies to code, never to contracts.

Rationale: the cheap moment to keep a door open is while designing the interface; the
expensive mistake is implementing behind it before a real consumer exists.

## Module Architecture & Technology Constraints

- Modules: `core` (pure engine), `content` (data definitions + loaders), `demo-console`
  (JVM console demo, first end-to-end validation), `tactical` (reserved slot, empty),
  `demo-app` (future Compose Multiplatform for Android/web).
- Dependency direction: `demo-*` → `content` → `core`. Nothing depends on demos; `core`
  depends on nothing in the project.
- Stack: Kotlin Multiplatform 2.4.x, Gradle with version catalog, kotlinx.serialization
  for all content schemas, kotest (engine, assertions, property) in `commonTest`.
- Design patterns of record in `core`: Command (combat actions), State (battle flow and
  status effects), Strategy (damage formulas, AI, targeting), Observer/event bus (battle
  events, synergy/combo triggers).
- Required mechanics scope for the engine: attacks, skills, magic, defend, party
  synergies/combos (including class-combination synergies), elemental weaknesses and
  resistances, status effects, limit breaks (may be class-specific), summons, and
  abstracted turn scheduling.

## Non-Goals (v1) & Development Workflow

Non-goals for v1 (explicitly out of scope; revisiting requires a constitution amendment):

- No networking or multiplayer of any kind.
- No `tactical`/grid implementation — the module slot exists, its content does not.
- No `demo-app` (Compose Multiplatform) until the console demo validates the core.
- No content-authoring tools (editors, validators beyond schema deserialization).
- No persistence/save-system beyond serializing battle state.

Workflow: every feature starts from a spec under `specs/` created via the Spec Kit
skills; implementation PRs reference their spec and tasks. Gradle build and the full
kotest suite MUST pass before merge. Code review verifies compliance with this
constitution, in particular Principles I and II (no non-determinism, no content-as-code).

## Governance

This constitution supersedes ad-hoc practice for this repository. Amendments are made by
editing `.specify/memory/constitution.md` with a version bump and a Sync Impact Report,
propagated to dependent templates in the same change. Versioning follows semantic
versioning: MAJOR for removals/redefinitions of principles, MINOR for new or materially
expanded principles/sections, PATCH for clarifications. Every plan produced by
`/speckit-plan` MUST pass its Constitution Check gate against the current version; any
justified deviation is recorded in that plan's Complexity Tracking section.

**Version**: 1.0.0 | **Ratified**: 2026-07-11 | **Last Amended**: 2026-07-11
