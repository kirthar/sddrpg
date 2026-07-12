# Quickstart: Validating Battle Events & Synergies

**Feature**: 005-battle-events-synergies

## Prerequisites

Same as specs 001–004: JDK 21+, Node.js (JS test target). No new setup — this feature
adds one package (`event`) to the existing `core` module and reuses spec 002's
`resolveAction`/`BattleState`, spec 003's `ActiveTimeBattleScheduler`, and spec 004's
`tickStatusEffects` fixtures as the starting point.

## Run the validation suite

```bash
./gradlew :core:allTests        # kotest on JVM + JS/node — must be green on both
./gradlew :core:jvmTest --tests '*SynergyResolutionTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 scenario 1 (empty log, no error) | `event/EventLogTest` |
| US1 scenario 2 (damage from a resolved action) | `event/EventDerivationTest`, via `eventsFromResolution` |
| US1 scenario 3 (DoT tick damage, same shape as a resolved action's) | `event/StatusTickEventTest`, via `eventsFromTick`: asserts a `resolveAction`-sourced and a `tickStatusEffects`-sourced `DamageDealt` differ only in `actorId` |
| US1 scenario 4 (status applied/expired) | `event/StatusTickEventTest`, via `eventsFromApply`/`eventsFromTick` |
| US1 scenario 5 (combatant defeated) | `event/DefeatedEventTest`: covers all three health-changing sources (resolution, tick, synergy bonus) via the shared `defeatedEvents` helper |
| US1 scenario 6 (turn granted) | `event/EventDerivationTest`, via `eventsFromSchedule` |
| US1 scenario 7 (log only grows, never reorders) | `event/EventLogTest` |
| US2 scenarios 1–4 (synergy triggers / window miss / target mismatch / same-combatant rejection) | `event/SynergyResolutionTest` |
| US2 scenario 5 (bonus damage respects health bounds) | `event/SynergyResolutionTest` (0/maximum boundary asserted alongside the trigger scenarios) |
| US3 (multiple independent synergies coexist) | `event/MultipleSynergiesTest` |
| US4 (synergy trigger is its own event) | `event/SynergyTriggeredEventTest` |
| FR-004/FR-007 (catalog validation, accumulate-all) | `event/SynergyCatalogValidationTest` |
| FR-005 (nearest-prior-match algorithm, R3) | `event/SynergyResolutionTest`: three-candidate scenario asserts the *nearest* eligible prior event wins, not the first or last arbitrary one |
| FR-010/SC-005 (determinism) | `event/EventDeterminismTest`: property test over resolve/tick/schedule/detect sequences |
| FR-011 (specs 001–004 contracts untouched) | `event/EventContractTest`: compilation itself is partial proof — no source changes to `model`/`combatant`/`action`/`schedule`/`status`/`catalog` packages; a battle with no synergy catalog behaves identically to specs 001–004 alone |
| Edge cases (window expiry, defeated-combatant-supplied-first-half, empty catalog, unbounded log growth) | `event/SynergyResolutionTest`, `event/EventLogTest` |

## Expected outcome

- `:core:allTests` green on JVM and JS with identical results (same as specs 001–004).
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No UI or battle loop exists yet (`demo-console` remains a placeholder until feature
008; wiring `eventsFromX`/`detectSynergyTriggers` into an actual per-turn loop is a
later feature). A scratch test may resolve two actions from combatants with
complementary skills against the same target within a window, then print the
resulting `EventLog.entries` — useful for eyeballing the trigger sequence, not
required for completion.
