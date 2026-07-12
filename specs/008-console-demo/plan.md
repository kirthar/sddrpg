# Implementation Plan: Console Demo

**Branch**: `008-console-demo` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-console-demo/spec.md`

## Summary

Make `demo-console` a genuinely playable, watchable battle: drive specs 001-007's
turn loop (`TurnScheduler` → `resolveAction` → `tickStatusEffects` → event derivation
→ synergy/limit-gauge bookkeeping) end-to-end for the first time, render every
occurrence as text, gate human input exactly as `resolveAction` gates it, and give
automatically-controlled combatants a small deterministic action-selection rule. Two
new small pieces of genuinely reusable, pure `core` logic are added (an automatic
command/target selection rule, and battle-outcome detection) alongside a
content-owned mapping of what the demo's own basic attack/skills actually do, and a
console-I/O-injectable turn-loop driver that lives entirely in `demo-console`.

Research also surfaced and resolves one real, previously-uncovered defect: every
enemy built via spec 001's `RosterBuilder.addEnemy` always gets an **empty**
`capabilities.commands` set, unconditionally — meaning no enemy has ever been able to
submit *any* action through `resolveAction`, for any `CommandKind`, ever, in the
whole codebase (see research.md R5). No existing test anywhere exercises an enemy as
`resolveAction`'s actor to catch this. Left unaddressed, this makes constitution
Principle IV's "uniform resolution treatment" and this feature's own FR-003/SC-001
impossible (a battle where the enemy can never act cannot end in defeat). The fix is
a "wrap, don't touch" workaround scoped to this feature's own demo roster assembly —
no change to any spec 001-007 file.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (JVM target for `demo-console`; the two new `core`
additions are commonMain, jvm+js)

**Primary Dependencies**: existing `core`/`content` modules only; no new external
dependencies

**Storage**: N/A (in-memory battle session only, per spec's out-of-scope: no save/load)

**Testing**: kotest (`commonTest` for the two new `core` files; JVM-only kotest for
`demo-console`'s loop/rendering/AI-wiring logic, run via the input/output injection
seam so no real console I/O is needed in tests)

**Target Platform**: JVM console application (`demo-console` is already JVM-only, per
its existing `build.gradle.kts`)

**Project Type**: single project — three existing Gradle modules extended (`core`
additively, `content` additively, `demo-console` implemented for the first time)

**Performance Goals**: N/A — a single interactive demo battle, no throughput/latency target

**Constraints**: fully deterministic (constitution Principle I); no new dependency on
any spec 001-007 file's existing signature; `demo-console` must stay free of
game-content knowledge (constitution Principle II) — it consumes `content`'s demo
action mapping, never hardcodes formulas itself

**Scale/Scope**: one small hand-authored demo roster (2 party members vs. 1 enemy,
per spec 007's shipped `DEMO_CONTENT_JSON`); a single playable battle from start to
conclusion

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Pure & Deterministic Core)**: PASS. The two new `core` additions
  (automatic command/target selection, battle-outcome detection) are pure functions
  over already-published `core` types, no RNG, no I/O. `demo-console`'s loop is
  deterministic given the same content and the same sequence of human inputs (FR-010);
  the only non-determinism source (real console I/O) is isolated behind an injected
  input/output seam precisely so the loop itself stays testable and deterministic
  (research.md R2).
- **Principle II (Data-Driven Content)**: PASS. What the demo's basic attack and each
  skill does (formula/targeting/element) is authored as data in `content`, not as
  `demo-console` code branching per skill id (research.md R1). `demo-console` looks
  the mapping up by `CommandKind`/`SkillId`, never hardcodes a formula.
- **Principle III (Test-First)**: PASS. tasks.md (next phase) will order tests before
  implementation for every piece, matching specs 001-007's established discipline.
- **Principle IV (Simulation-Presentation Separation)**: PASS. All text rendering
  lives in `demo-console` only; `core`/`content` stay free of any formatted-text
  concern. The automatic action rule and battle-outcome detection are pure decision
  logic, not presentation, so placing them in `core` does not violate this principle
  the same way spec 001's `DecisionSource` already anticipated (human vs. AI are
  interchangeable decision sources behind one interface).
- **Principle V (Extensible Architecture Without Speculative Implementation)**: PASS.
  The automatic action rule takes its command preference order as a parameter rather
  than hardcoding one (reusable beyond this one demo without new abstraction); no
  generic "action definition" catalog type is introduced (spec FR-006's own
  constraint) — the demo action mapping is a small, concrete, non-generic list scoped
  to exactly the shipped demo content.
- **Cross-spec discipline**: PASS, with one documented, narrowly-scoped exception.
  Every existing spec 001-007 file's public signature is unchanged. The one thing
  this feature adds to `core` beyond brand-new files is nothing — the enemy-commands
  workaround (research.md R5) is implemented entirely via `Combatant` interface
  delegation (the same pattern spec 004's `EffectiveCombatant` already established)
  inside `content`'s own new demo-assembly file, touching zero existing spec 001-007
  source lines.

**Result**: No violations. Nothing to record in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/008-console-demo/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── console-demo-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/
├── ai/
│   └── AutomaticActionRule.kt      # NEW: selectAutomaticCommand, selectAutomaticSkill, selectAutomaticTarget
└── battle/
    └── BattleOutcome.kt            # NEW: BattleOutcome sealed type + BattleState.outcome()

core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/
├── ai/AutomaticActionRuleTest.kt   # NEW
└── battle/BattleOutcomeTest.kt     # NEW

content/src/commonMain/kotlin/io/github/kirthar/sddrpg/content/demo/
├── DemoActions.kt                  # NEW: DemoActionDefinition + DEMO_ACTIONS for the shipped demo content
└── DemoRoster.kt                   # NEW: builds the demo Roster/BattleState and applies the
                                     #      enemy-commands workaround (research.md R5) via Combatant delegation

content/src/commonTest/kotlin/io/github/kirthar/sddrpg/content/demo/
├── DemoActionsTest.kt              # NEW
└── DemoRosterTest.kt               # NEW

demo-console/src/main/kotlin/io/github/kirthar/sddrpg/demo/console/
├── Main.kt                         # REPLACED: wires real System.in/println into BattleLoop
├── BattleSession.kt                # NEW: the mutable-across-turns bundle of battle-time state
├── BattleLoop.kt                   # NEW: the injectable turn-by-turn driver (FR-007 testability seam)
└── ConsoleRendering.kt             # NEW: BattleEvent -> human-readable text (US2)

demo-console/src/test/kotlin/io/github/kirthar/sddrpg/demo/console/
├── BattleLoopTest.kt                # NEW: scripted input/output, no real console (FR-007)
├── BattleLoopDeterminismTest.kt     # NEW: same scripted inputs replayed twice, identical outcome (FR-010)
├── BattleLoopPlaythroughTest.kt     # NEW: US3 — a full scripted playthrough exercises every mechanic
└── ConsoleRenderingTest.kt          # NEW
```

**Structure Decision**: Single project, extending the three existing modules along
their existing dependency direction (`demo-console` → `content` → `core`). No new
Gradle module. `core` gains two small new files (no existing file touched);
`content` gains a `demo/` sub-package addition (no existing `content` file touched);
`demo-console` goes from a placeholder to its first real implementation.

## Complexity Tracking

*No violations — table intentionally empty.*
