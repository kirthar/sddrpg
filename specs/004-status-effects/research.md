# Research: Status Effects

**Feature**: 004-status-effects | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here. R1 is the central architectural question spec.md deliberately left
open ("the exact mechanism is a technical decision for `/speckit-plan`").

## R1 — How stat modifiers and Speed-zeroing reach `resolveAction`/`nextTurn` unchanged

**Decision**: `Combatant` (spec 001) is a plain, non-sealed public `interface` with
only immutable `val`s — its two concrete implementations are `internal`, but nothing
stops an *external* class from implementing `Combatant` directly. This feature adds a
private wrapper using Kotlin interface delegation:

```kotlin
private data class EffectiveCombatant(
    private val base: Combatant,
    override val stats: StatBlock,
) : Combatant by base
```

A pure function `deriveEffectiveBattleState(battle, effects, catalog): BattleState`
walks every participant, computes each one's modifier-adjusted `StatBlock` (R3), and —
only for participants that actually have an active effect — replaces their
`BattleCombatant.combatant` with an `EffectiveCombatant` wrapping the original (via
`BattleCombatant.copy(combatant = ...)`, a public data-class operation, not an
internal one). Participants with no active effects are left byte-identical. The
caller passes this *derived* `BattleState` into `resolveAction`/`nextTurn` instead of
the raw one — both functions already read every stat exclusively through
`combatant.stats`, so neither needs to know status effects exist.

**Rationale**: This is the same shape of trick specs 002 and 003 already used
(wrap, don't touch) — spec 002 wrapped `Combatant` in `BattleCombatant` for health,
spec 003 introduced `AtbScheduleState` alongside `BattleState` for readiness; here the
wrapping happens *at the `Combatant` level itself*, which is exactly what's needed
because `resolveAction` and `nextTurn` both read stats through `Combatant.stats` with
no indirection to intercept elsewhere. Delegation (`by base`) means only `stats` is
overridden; every other member (`id`, `capabilities`, `affinities`, …) forwards
untouched, so nothing about a combatant's identity or capabilities is disturbed by
wrapping it — satisfying FR-004's "does not alter underlying stats" (the *underlying*
`Combatant` inside the original `BattleState` truly never changes; only the
short-lived derived copy used for one resolution differs).

**Alternatives considered**: extending spec 001's `Catalog`/`Combatant` with a new
`withStats(...)` function (would touch spec 001's files — explicitly disallowed by
spec FR-011, even though Kotlin extension functions are additive in principle, editing
existing source files was ruled out to keep this feature's diff scoped to its own
package, matching specs 002/003's discipline of a brand-new file set); a
reflection/property-overlay approach (unnecessary complexity — Kotlin interface
delegation already solves this exactly, no reflection needed); mutable `Combatant`
(rejected outright — breaks Principle I's immutability discipline everywhere else in
`core`).

## R2 — `EffectKind`: closed, data-selected discriminator (spec FR-001)

**Decision**: `EffectKind` is a sealed interface, exactly three variants for this
feature (spec.md's explicit scope):

```kotlin
sealed interface EffectKind {
    data class StatModifier(val statId: StatId, val delta: Int) : EffectKind
    data object Incapacitate : EffectKind
    data class DamageOverTime(val amountPerTick: Int) : EffectKind
}
```

**Rationale**: Mirrors spec 002's `DamageFormula` shape exactly (sealed, data-carrying
variants selected by content) — content authors pick a kind and its parameters, engine
code never grows a new class per named effect (a "Poison" or "Stun" is just a
`StatusEffectDefinition` value, constitution Principle II). `DamageOverTime`'s
`amountPerTick` reuses the same "magnitude as authored data" pattern spec 002 used for
`DamageFormula.Fixed`.

**Alternatives considered**: an open/abstract class per kind (defeats the closed-set,
exhaustive-`when` guarantee that keeps every consumer of `EffectKind` total); an enum
with a generic parameter bag (loses type safety — `StatModifier` needs a `StatId` and
an `Int`, `DamageOverTime` needs only an `Int`, a shared bag can't express that).

## R3 — Stat modifier combination: additive, floored at zero (spec FR-004, edge case)

**Decision**: For a combatant with active stat-modifier effects, group active
`StatModifier` effects by `statId`, sum their `delta`s, add each sum to the
combatant's current value for that stat, and floor at `0` before constructing the new
`StatBlock` (whose constructor already enforces non-negative values, spec 001 R4 — the
floor must happen *before* construction or it throws). Stats a modifier references
that the target doesn't currently have are silently skipped (no-op) rather than
erroring — this feature's catalog is independent of spec 001's (R4 below), so it
cannot cross-validate that every referenced `StatId` exists on every combatant that
might receive the effect; that responsibility stays with whoever authors content in
feature 007.

**Rationale**: Additive combination is the simplest, most predictable rule satisfying
FR-004's "combine additively into one adjusted value, not just the most recently
applied one" — two +5 Defense buffs give +10, not +5 or a magnitude war between them.
Flooring at zero reuses spec 001's existing non-negative-stat invariant rather than
inventing a new bound.

**Alternatives considered**: multiplicative/percentage modifiers (not requested by
spec.md, which only asks for additive-feeling "buffs/debuffs"; percentage stacking
introduces order-of-operations questions with no spec-level answer — deferred, not
built); last-applied-wins (explicitly rejected by FR-004 scenario 4).

## R4 — Status effect catalog is independent of spec 001's `Catalog` (spec FR-011)

**Decision**: `StatusEffectCatalog(effects: List<StatusEffectDefinition>)` is its own
small aggregate, validated by its own `validateStatusEffectCatalog(...)` function —
entirely separate from spec 001's `Catalog`/`ValidatedCatalog`/`validateCatalog`. It
checks only what it can see: unique `StatusEffectId`s and `duration > 0` (the edge
case spec.md calls out — a non-positive duration is rejected at validation time, never
applied at runtime).

**Rationale**: Adding a `statusEffects` field to spec 001's `Catalog` data class would
touch an existing spec 001 file — even though Kotlin data classes tolerate a new field
with a default value without breaking callers, editing that file at all falls outside
the "purely additive, new files only" discipline this feature (and specs 002–003
before it) commits to. A fully separate catalog costs nothing: nothing about status
effects needs to be cross-validated against classes, characters, or enemies at this
stage (a future content feature, 007, is free to compose both catalogs together when
it actually authors game content).

**Alternatives considered**: extending spec 001's `Catalog` (touches an existing file
— rejected per FR-011's discipline, even though technically non-breaking); no
validation at all for the status catalog (would let a broken duration slip through to
runtime, contradicting the edge case spec.md explicitly calls out).

## R5 — Incapacitation reuses spec 003's zero-Speed exclusion (spec FR-005/FR-007)

**Decision**: An `Incapacitate`-kind active effect, when deriving the effective
`BattleState` (R1), forces the affected combatant's effective `CoreStats.SPEED` to `0`
(combined additively with any stat-modifier deltas the same way any other stat would
be — Speed is not special-cased beyond being the stat `Incapacitate` targets). No new
code is added to `ActiveTimeBattleScheduler`: spec 003's `nextTurn` already excludes
`speed <= 0` candidates from ATB accrual entirely (spec 003 research R2, tested by
`ZeroSpeedTest`), which is exactly "never offered a turn" (FR-005) — and because
excluded candidates never advance their own `AtbScheduleState` readiness while
excluded (spec 003's `minTicks` computation skips them), their progress is correctly
*frozen*, not lost: once the effect ends, their real Speed resumes and their gauge
continues from wherever it was.

**Rationale**: This is the cleanest possible integration — a already-built,
already-tested exclusion mechanism (spec 003) is reused exactly as designed, and
"resumes normal scheduling once the effect ends" (US3 scenario 2) falls out for free
because nothing about the combatant's real `AtbScheduleState` entry was ever touched,
only the *effective* stat view fed into that one `nextTurn` call.

**Alternatives considered**: a dedicated `TurnScheduler` parameter or method for
"excluded combatant ids" (would require touching spec 003's interface — rejected,
FR-011); tracking incapacitation inside `AtbScheduleState` itself (spec 003's state
type is intentionally opaque to this feature and already closed over its own concern,
readiness only — mixing in incapacitation there would blur R1 of spec 003's own
design).

## R6 — Damage-over-time reuses spec 002's clamp arithmetic, not its pipeline (spec FR-006)

**Decision**: `tickStatusEffects` applies each active `DamageOverTime` effect's
`amountPerTick` directly: `newCurrent = (health.current - amountPerTick).coerceIn(0,
health.maximum)`, producing a new `BattleState` the same shape `resolveAction`
produces, but *without* calling `resolveAction` — there is no `CombatAction`, no
actor, and no capability gate to check (spec FR-006: "without requiring any actor to
submit an action").

**Rationale**: `resolveAction`'s public entry point requires a `CombatAction` with an
`actorId` and a `command` the actor must currently be capable of — DoT ticking has
neither concept; a poisoned combatant with no granted commands must still take poison
damage. Routing DoT through the action-gated pipeline would be a semantic mismatch,
not genuine reuse — three lines of duplicated clamp arithmetic is the honest, small
cost of a mechanic that is fundamentally actor-less, matching spec 002's own R7
precedent (DEFEND/ITEM fit the action pipeline naturally; a fundamentally different
mechanic like DoT does not, and forcing it in would be worse than the duplication).

**Alternatives considered**: synthesizing a fake `CombatAction` where the poisoned
combatant is its own actor and target (requires them to have *some* granted command
just to be able to take poison damage — nonsensical: a stunned, commandless combatant
must still be poisonable); exposing a new public health-mutation function from spec
002 (touches an existing spec 002 file — rejected, FR-011).

## R7 — Tick order and the "one uniform rule" from the clarification (spec FR-007, FR-008)

**Decision**: `tickStatusEffects(effects, battle, catalog)` processes every active
effect on every combatant in one pass: decrement `remainingDuration` by `1`; if an
effect is `DamageOverTime`-kind and *still active after decrementing* (i.e.,
`remainingDuration >= 0` before removal — the tick that brings it to `0` still deals
its damage, matching genre convention that "3 turns of poison" means three ticks of
damage), apply its damage via R6; then remove any effect whose `remainingDuration` has
reached `0`. Reapplication (`applyStatusEffect`, FR-008) resets `remainingDuration` to
the definition's full `duration` and does not add a second instance — an effect
already active on a combatant is found by `(combatantId, effectId)` and replaced, not
appended.

**Rationale**: Directly implements the spec's Clarifications entry (one call =
one tick for everyone, not per-own-turn) and FR-008's refresh-not-stack rule with a
single, uniform algorithm — no special-casing by kind except for *what* happens on
tick (only `DamageOverTime` deals damage; `StatModifier` and `Incapacitate` have no
per-tick side effect beyond the shared duration decrement).

**Alternatives considered**: ticking DoT damage *before* decrementing duration (would
deal damage on the tick that already reduced remaining duration to a still-positive
number the previous call — off-by-one from the documented "N ticks of damage over N
turns" reading); decrementing but never removing at exactly `0` (would leave a
zero-duration "ghost" entry — rejected, contradicts FR-003's automatic-expiry
requirement).
