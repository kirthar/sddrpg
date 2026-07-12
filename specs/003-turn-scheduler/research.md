# Research: Turn Scheduler

**Feature**: 003-turn-scheduler | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here.

## R1 — Substitutable scheduler shape (spec FR-005/SC-006)

**Decision**: A generic Strategy interface, parameterized over its own private state
type:

```kotlin
interface TurnScheduler<State> {
    fun initialSchedule(battle: BattleState): State
    fun nextTurn(schedule: State, battle: BattleState): ScheduleResult<State>
    fun markSpent(schedule: State, combatantId: CombatantId): State
}
```

`ActiveTimeBattleScheduler : TurnScheduler<AtbScheduleState>` is the only
implementation this feature ships.

**Rationale**: A future phase-based (Fire Emblem-style) scheduler needs a
*fundamentally different* kind of bookkeeping — "which side's phase is active" and
"who on that side hasn't acted yet this phase" is nothing like a per-combatant
readiness gauge. Making the state type a generic parameter (rather than one shared
concrete `ScheduleState` class both algorithms must squeeze into) is what makes
SC-006 literally true: a second implementation is a new class + a new private state
type behind the same three-method interface, with zero changes to `BattleState`,
`Combatant`, or anything that calls `nextTurn`/`markSpent` generically.

**Alternatives considered**: one shared concrete `ScheduleState` data class with fields
for both algorithms (forces ATB's readiness map and a hypothetical phase-tracker to
coexist in one type — violates Principle V by pre-designing for a mode not yet built,
and any real phase implementation would likely need incompatible fields anyway);
non-generic interface returning `Any` for state (defeats Kotlin's type safety, pushes
unsafe casts onto every caller).

## R2 — Readiness accrual: tick-jump integer math, not simulated ticks (spec FR-002)

**Decision**: No per-tick loop. `nextTurn` computes, for every non-defeated candidate
`c` with `speed_c = combatant.stats[CoreStats.SPEED]`:

- if `speed_c <= 0`: excluded from candidacy (never becomes ready — spec edge case)
- else: `ticksNeeded_c = ceilDiv(READY_THRESHOLD - readiness_c, speed_c)` using pure
  integer ceiling division (`(numerator + denominator - 1) / denominator` for
  non-negative operands — no `Double`, no rounding function needed at all)

`minTicks = min(ticksNeeded_c)` over finite candidates (`null`/no result if none
exist — spec FR-009's "no one ready"). Every non-defeated combatant's readiness
advances by `minTicks * speed_c` in the returned state. Every candidate whose
`readiness` (after advancing) is `>= READY_THRESHOLD` is now "ready"; if more than one
qualifies, R3's tie-break picks the winner. `markSpent` subtracts `READY_THRESHOLD`
from the winner's readiness (carry-over of overshoot, not a hard reset to 0).

**Rationale**: A literal per-tick simulation loop (`while (readiness < threshold)
{ readiness += speed }`) would be O(threshold/speed) iterations — unbounded, wasteful,
and pure ceremony since the exact same answer is one integer division away. Carrying
over overshoot (rather than resetting to `0`) means a very fast combatant that jumps
past the threshold by a few points doesn't lose that progress toward its *next* turn —
standard ATB behavior and directly what keeps SC-001's "faster combatants get a
proportionally greater share of turns" true even under coarse, jump-based advancement.
Pure integer division keeps this feature *stricter* than spec 002 (which needed one
documented rounding rule for its ×0.5 resistance case) — there is no rounding rule to
document here at all, only integer ceiling division.

**Alternatives considered**: simulated per-tick loop (rejected — unbounded cost, no
behavioral difference from the closed-form calculation); floating-point readiness
percentages (rejected — reintroduces the cross-platform determinism risk spec 001 R4
deliberately avoided everywhere else in this engine).

## R3 — Readiness threshold and tie-breaking (spec FR-002/FR-007)

**Decision**: `READY_THRESHOLD = 1000` (a fixed engine constant, not content data —
it has no observable meaning outside relative speed comparisons, since ticks are
never exposed to callers). Ties are broken by each candidate's index in
`battle.participants` (spec 002's `BattleState`, itself ordered by spec 001 R7's
deterministic roster-insertion order) — the candidate appearing earliest in that list
wins.

**Rationale**: 1000 gives enough granularity that typical Speed values (roughly 1–100
per spec 001's fixture conventions) produce meaningfully different tick counts without
degenerate one-tick-always-ready behavior at the low end; because ticks are purely
internal to the accrual computation and never part of the public API, the exact
constant is a technical, not business, decision (spec.md deferred it here, mirroring
spec 002's precedent for its multiplier table). Reusing `BattleState.participants`
order for tie-breaks needs no new ordering concept and stays consistent with every
other deterministic-ordering decision already made in this engine (spec 001 R7, spec
002 R6) — one canonical "stable order" notion across the whole codebase.

**Alternatives considered**: `CombatantId` lexicographic ordering (rejected — spec 001
R7's ids are `"c1"`, `"c2"`, …, `"c10"`, and `"c10" < "c2"` lexicographically once a
roster exceeds nine members, a subtle correctness trap); random tie-break seeded
per-battle (rejected outright — violates Principle I, no RNG in this feature at all).

## R4 — ScheduleState initialization and defeated-combatant handling (spec FR-004/FR-009)

**Decision**: `initialSchedule(battle)` sets every participant's readiness to `0`,
including any combatant that happens to already be defeated at battle start (harmless
— they're simply always filtered out of candidacy). Defeated status is read fresh from
the `BattleState` passed into *every* `nextTurn` call, not cached in `AtbScheduleState`
— so a combatant defeated between two calls (via spec 002's `resolveAction` producing
a new `BattleState`) is excluded starting from the very next query, with no explicit
"defeat notification" needed (spec US2 scenario 2).

**Rationale**: Keeping `AtbScheduleState` free of any health/defeated bookkeeping is
what makes it correct-by-construction to read the caller's current `BattleState` each
time — there is exactly one source of truth for "who's alive" (spec 002's model),
never a second copy that could drift out of sync.

**Alternatives considered**: caching alive/defeated status inside `ScheduleState`
(creates a second, potentially stale copy of information `BattleState` already owns —
rejected as needless duplication and a sync-bug risk).
