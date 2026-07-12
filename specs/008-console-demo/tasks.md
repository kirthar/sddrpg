# Tasks: Console Demo

**Input**: Design documents from `/specs/008-console-demo/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md,
contracts/console-demo-api.md, quickstart.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001-007: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when its
module's `allTests` task is green (JVM+JS for `core`/`content`, JVM-only for
`demo-console`).

**Organization**: Spec 008 has **two P1 stories** (US1 the complete battle loop, US2
every occurrence rendered as text — both required together per spec.md's "Why this
priority" for each, mirroring specs 005/006/007's own two-P1 pattern: `runBattleLoop`
already calls `emit` for every event by contract, so the two stories are built as one
integrated increment with separate test files) and **one P2 story** (US3, exercising
every mechanic, which depends on US1+US2 already working).

**Path shorthand**: `CORE-MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core`,
`CORE-TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core`,
`CONTENT-MAIN` = `content/src/commonMain/kotlin/io/github/kirthar/sddrpg/content`,
`CONTENT-TEST` = `content/src/commonTest/kotlin/io/github/kirthar/sddrpg/content`,
`DEMO-MAIN` = `demo-console/src/main/kotlin/io/github/kirthar/sddrpg/demo/console`,
`DEMO-TEST` = `demo-console/src/test/kotlin/io/github/kirthar/sddrpg/demo/console`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (user story phases only)

---

## Phase 1: Setup

- [X] T001 Add kotest JVM test dependencies (`kotest-runner-junit5`,
      `kotest-framework-engine`, `kotest-assertions-core`) as `testImplementation` to
      `demo-console/build.gradle.kts` and a `tasks.withType<Test>().configureEach { useJUnitPlatform() }`
      block, matching `core`/`content`'s existing JVM test wiring — `demo-console` has
      no test source set or test dependencies yet

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The two small reusable `core` additions (`BattleOutcome`,
`AutomaticActionRule`) and the two `content` additions (`DemoActionDefinition`/
`DEMO_ACTIONS`, `buildDemoBattleState` with the enemy-commands workaround) that every
user story's battle loop depends on. No loop/rendering logic yet.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Write failing tests for `BattleOutcome`/`BattleState.outcome()`
      (`Ongoing` while both sides have a living participant; `Victory` when every
      `OPPONENT` participant is defeated; `Defeat` when every `PLAYER` participant is
      defeated; simultaneous-defeat tie-break resolves to `Defeat`, checked before
      `Victory`) in `CORE-TEST/battle/BattleOutcomeTest.kt`
- [X] T003 [P] Write failing tests for `selectAutomaticCommand`/`selectAutomaticSkill`/
      `selectAutomaticTarget` (first matching command in preference order; `null` when
      none match; skill selection is deterministic regardless of `Set` iteration order
      — assert with a multi-skill fixture; target is the living opposing participant
      with lowest current health; ties broken by participant order; `null` for an
      unknown actor or no living opposing participant) in `CORE-TEST/ai/AutomaticActionRuleTest.kt`
- [X] T004 Implement `BattleOutcome` sealed type and `BattleState.outcome()` in
      `CORE-MAIN/battle/BattleOutcome.kt`
- [X] T005 Implement `selectAutomaticCommand`, `selectAutomaticSkill`,
      `selectAutomaticTarget` in `CORE-MAIN/ai/AutomaticActionRule.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS.

- [X] T006 [P] Write failing tests for `DemoActionDefinition`/`DEMO_ACTIONS` (exactly
      one entry per `(command, skillId)` pair covering ATTACK/`cleave`/`fira`; every
      non-null `skillId` referenced actually appears in `DEMO_CONTENT_JSON`'s
      `knownSkills`; the `find(command, skillId)` lookup helper returns the right
      entry and `null` for an unregistered pair) in `CONTENT-TEST/demo/DemoActionsTest.kt`
- [X] T007 [P] Write failing tests for `buildDemoBattleState` (the bomb enemy's
      `capabilities.commands` contains `ATTACK`; every non-enemy participant's
      `capabilities` is unchanged from what `RosterBuilder` alone produces; repeated
      calls return structurally-equal `BattleState`s; an enemy with no declared
      skills does NOT get `CommandKind.SKILL` granted) in `CONTENT-TEST/demo/DemoRosterTest.kt`
- [X] T008 [P] Implement `DemoActionDefinition`, `DEMO_ACTIONS`, and the `find` lookup
      helper in `CONTENT-MAIN/demo/DemoActions.kt`
- [X] T009 [P] Implement the `Combatant`-delegating command-grant wrapper and
      `buildDemoBattleState` in `CONTENT-MAIN/demo/DemoRoster.kt`

**Checkpoint**: `:content:allTests` green on JVM+JS — foundation ready, `demo-console`
work can start.

---

## Phase 3: User Story 1 — A complete battle plays out from start to a clear conclusion (Priority: P1)

**Goal**: `runBattleLoop` drives the scheduler, gates human submissions exactly as
`resolveAction` gates them, selects automatic actions via `core.ai`, and returns the
correct `BattleOutcome` the instant one side is wiped — using only the basic
ATTACK/SKILL/MAGIC commands `DEMO_ACTIONS` already defines (limit break/summon
submission is US3's extension).

**Independent Test** (from spec US1): script a full sequence of valid inputs for
cloud/aerith's turns, run `runBattleLoop` against `buildDemoBattleState()`, and verify
it returns `Victory` or `Defeat`, the bomb's turns never call `readInput`, and replaying
the identical scripted input list returns the identical outcome.

### Tests for User Story 1 (write first, must fail) ⚠️

- [X] T010 [US1] Write failing tests for human-turn gating (a prompt is emitted only
      on a human-controlled combatant's turn; a valid submission resolves and the
      scheduler advances; a `resolveAction`-rejected submission emits the rejection
      reason in plain language and reprompts without consuming the turn; unparseable
      input reprompts the same way) in `DEMO-TEST/BattleLoopTest.kt`
- [X] T011 [US1] Write failing tests for automatic-turn selection (no prompt/`readInput`
      call on an automatically-controlled combatant's turn; a valid action is selected
      via `selectAutomaticCommand`/`selectAutomaticSkill`/`selectAutomaticTarget` and
      resolved; a combatant with no usable command is skipped — `markSpent` still
      called — without ending the battle or emitting a rejection) in `DEMO-TEST/BattleLoopTest.kt`
- [X] T012 [US1] Write failing tests for conclusion handling (the loop returns the
      instant `BattleState.outcome()` is no longer `Ongoing`, matching that exact
      value; `readInput` returning `null` while awaiting a human submission ends the
      loop gracefully without crashing, returning the current — possibly `Ongoing` —
      outcome) in `DEMO-TEST/BattleLoopTest.kt`
- [X] T013 [P] [US1] Write a failing determinism test (the same starting session,
      content, and scripted `readInput` sequence, run twice, produce an identical
      returned `BattleOutcome` and an identical sequence of `emit` calls) in
      `DEMO-TEST/BattleLoopDeterminismTest.kt`

### Implementation for User Story 1

- [X] T014 [US1] Implement `BattleSession` and a `newBattleSession()` factory (battle
      from `buildDemoBattleState()`, schedule from
      `ActiveTimeBattleScheduler.initialSchedule`, empty effects/gauges/log, resources
      from `battle.initialResourceState()`) in `DEMO-MAIN/BattleSession.kt`
- [X] T015 [US1] Implement `runBattleLoop`: step the scheduler via `nextTurn`; on a
      human turn, prompt/parse/gate via `resolveAction` with reprompt-on-rejection and
      graceful EOF handling (T010/T012); on an automatic turn, select via
      `core.ai`'s three functions plus `DEMO_ACTIONS.find`, skipping the turn if no
      command is usable (T011); after every resolved submission, apply the full
      per-turn bookkeeping order from research.md R6 (event derivation, synergy
      detection + bonus, limit-gauge charging, one status-effect tick,
      `deriveEffectiveBattleState` before the next turn); call `markSpent`; check
      `BattleState.outcome()` and recurse or return (T012/T013) in `DEMO-MAIN/BattleLoop.kt`

**Checkpoint**: US1 green — a full battle can be played via scripted input to a clear,
deterministic conclusion. Events are derived and bookkept correctly but not yet
rendered as text (US2).

---

## Phase 4: User Story 2 — Every occurrence in the battle is visible as it happens (Priority: P1)

**Goal**: Every `BattleEvent` subtype (including spec 006's `GaugeFull`/
`LimitBreakUsed`/`SummonCast`, same sealed hierarchy, different file) renders as plain
language, and `runBattleLoop` emits that text for every new event each turn.

**Independent Test** (from spec US2): play through a battle and verify each documented
occurrence kind, when it happens, appears as readable text via `emit` before the next
turn begins.

### Tests for User Story 2 (write first, must fail) ⚠️

- [X] T016 [P] [US2] Write failing tests for `BattleEvent.toDisplayText` covering every
      subtype — `DamageDealt`/`HealingApplied` with and without an `actorId`,
      `StatusEffectApplied`, `StatusEffectExpired`, `CombatantDefeated`, `TurnGranted`,
      `SynergyTriggered`, `GaugeFull`, `LimitBreakUsed`, `SummonCast` — asserting
      combatant display names are resolved from `battle`, never raw ids, in
      `DEMO-TEST/ConsoleRenderingTest.kt`
- [X] T017 [US2] Write a failing test extending `BattleLoopTest` asserting `emit`
      receives rendered text (not raw event data) for every occurrence produced during
      a turn, in the order they were derived, in `DEMO-TEST/BattleLoopTest.kt`

### Implementation for User Story 2

- [X] T018 [P] [US2] Implement `BattleEvent.toDisplayText(battle)` covering every
      subtype in `DEMO-MAIN/ConsoleRendering.kt`
- [X] T019 [US2] Wire `ConsoleRendering` into `runBattleLoop`'s per-turn event
      emission (replacing any placeholder emission from T015) in `DEMO-MAIN/BattleLoop.kt`

**Checkpoint**: US1 + US2 green together — a full, watchable battle, the feature's
namesake payoff.

---

## Phase 5: User Story 3 — Every mechanic built so far is actually reachable in play (Priority: P2)

**Goal**: The human-turn parser also recognizes submitting cloud's limit break
(`omnislash`, once his gauge is full) and a summon (`meteor`, while MP allows),
via `resolveLimitBreak`/`resolveSummon` and `eventsFromLimitBreak`/`eventsFromSummon`
instead of `resolveAction`/`eventsFromResolution` — so a full playthrough can actually
exercise every mechanic specs 001-007 built, not just basic attacks.

**Independent Test** (from spec US3): script a full playthrough of the shipped demo
content that submits enough basic attacks to charge cloud's gauge and enough turns for
poison to tick down, then submits `omnislash` and `meteor`; verify the resulting
`EventLog` contains at least one status-effect tick (`DamageDealt` with `actorId ==
null`), at least one `LimitBreakUsed`, and at least one `SynergyTriggered` or
`SummonCast`.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T020 [US3] Write the failing scripted full-playthrough test described above in
      `DEMO-TEST/BattleLoopPlaythroughTest.kt`

### Implementation for User Story 3

- [ ] T021 [US3] Extend `runBattleLoop`'s human-turn command parser to recognize a
      limit-break submission and a summon submission (by id), routing them through
      `resolveLimitBreak`/`resolveSummon` and `eventsFromLimitBreak`/`eventsFromSummon`
      with the same rejection-reprompt handling as T010, updating `gauges`/`resources`
      on success, in `DEMO-MAIN/BattleLoop.kt`

**Checkpoint**: All three user stories independently green; a full playthrough
provably exercises every mechanic specs 001-007 built.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T022 [P] KDoc pass on all public `core`/`content`/`demo-console` additions from
      this feature (surface listed in contracts/console-demo-api.md)
- [ ] T023 Run quickstart validation: `:core:allTests`, `:content:allTests`,
      `:demo-console:allTests` green (JVM+JS for the first two, JVM-only for the
      third), then `:build` green for the whole project; update
      `specs/008-console-demo/quickstart.md`'s mapping table if any test file names
      drifted; update root `README.md` to mark spec 008 ✅ implemented, the
      `demo-console` module status line, and the completed 8-spec roadmap

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (extends the same `BattleLoop.kt`/`BattleLoopTest.kt` files
  T015/T010-T012 already created)
- **US3 (Phase 5)** → US1 + US2 (needs both the loop and its rendering already working
  to script and observe a full playthrough)
- **Polish (Phase 6)** → all stories

### Within Each Phase

Tests first (parallel only where files genuinely differ) → confirm they FAIL →
implementation → re-run until green. `DEMO-MAIN/BattleLoop.kt` is touched by T015,
then T019, then T021 in sequence — do not parallelize these against each other.
`DEMO-TEST/BattleLoopTest.kt` is touched by T010, T011, T012, then T017 in sequence —
same file, same rule.

### Parallel Opportunities

- T002 and T003 (foundational `core` tests: `BattleOutcome`/`AutomaticActionRule`) —
  different files, no shared dependency.
- T006 and T007 (foundational `content` tests: `DemoActions`/`DemoRoster`) — different
  files, no shared dependency.
- T008 and T009 (foundational `content` implementation) — different files, both
  independent of each other's internals.
- T013 (determinism test, its own file) is parallel with T010-T012 (same
  `BattleLoopTest.kt` file, sequential among themselves).
- T016 (`ConsoleRenderingTest.kt`) is parallel with the Phase 3 files (different
  module concern).
- T018 (`ConsoleRendering.kt` implementation) is parallel with anything not touching
  `BattleLoop.kt`.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: both are P1 — a battle loop that reaches a
correct conclusion but shows nothing isn't "a console demo," and text rendering with
no working loop underneath it isn't testable at all, so the smallest genuinely useful
slice is both together (spec.md's own "Why this priority" for US2). Then US3 (limit
break/summon reachability) as the final increment that makes the "end-to-end
validation of specs 001-007" payoff provable, not just assumed. Stop and validate at
every checkpoint with the module `allTests` task.
