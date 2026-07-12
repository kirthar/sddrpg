# Research: Battle Events & Synergies

**Feature**: 005-battle-events-synergies | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here. R1 is the central architectural question spec.md deliberately left open
("the exact mechanism is a technical decision for `/speckit-plan`"); R3 is the
feature's other genuinely novel design (nothing prior built a cross-event scan).

## R1 — Events reach a caller via a pure wrap-and-return pattern, not callbacks

**Decision**: `resolveAction` (spec 002), `tickStatusEffects` (spec 004), and
`TurnScheduler.nextTurn` (spec 003) are called exactly as published, completely
unmodified. Small pure functions in the new `event` package — `eventsFromResolution`,
`eventsFromTick`, `eventsFromSchedule` — take that call's own inputs *and* its already-
produced `ActionResolutionResult`/`StatusTickResult`/`ScheduleResult` and return the
`List<BattleEvent>` it represents. An `EventLog.append(events)` then accumulates them
(R2). A caller wanting events performs: call the spec 002/003/004 function → call the
matching `eventsFromX` → call `log.append(...)`. No subscriber list, no registration,
no hidden state anywhere in `core`.

**Rationale**: This is the same "derive from already-produced outputs" shape spec
004's R1 used (`deriveEffectiveBattleState` reads spec 001/002/003's already-published
types without touching them) applied one level up: instead of *feeding* an adjusted
input into an unmodified function (R1 there), this feature *reads* an unmodified
function's already-produced output and turns it into a second, additional piece of
data. Both tricks share the same discipline: touch zero existing files, add a pure
transformation over public types only (FR-011). An imperative Observer (subscriber
registration, `onEvent` callbacks) was the literal reading of "Observer/event-bus" in
the input, but constitution Principle I forbids hidden state and side effects in
`core`'s pure functions — a callback list is exactly that. The event-log-accumulation
pattern delivers the same *observable* outcome (a consumer can inspect what happened)
through pure data instead.

**Alternatives considered**: wrapping `resolveAction`/`tickStatusEffects`/`nextTurn`
themselves to return `(result, events)` tuples directly (would change their existing
public signatures — explicitly forbidden by FR-011, unlike the additive
`eventsFromX(...)` siblings which sit entirely outside those functions); a mutable
`EventBus` object with `subscribe`/`publish` (rejected outright — global mutable state,
directly contradicts Principle I and would make results depend on subscription order,
breaking FR-010's determinism guarantee); threading a `MutableList<BattleEvent>`
parameter through every spec 002–004 function (would touch every one of their
signatures — same FR-011 violation as the tuple option).

## R2 — `BattleEvent`: closed discriminator, `EventLog` stamps turn sequencing at append time

**Decision**: `BattleEvent` is a sealed interface with exactly the six FR-001 kinds
plus `SynergyTriggered` (FR-009) — seven variants, each carrying only the identifying
data FR-002 requires (never formatted text):

```kotlin
sealed interface BattleEvent {
    data class DamageDealt(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class HealingApplied(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class StatusEffectApplied(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class StatusEffectExpired(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class CombatantDefeated(val combatantId: CombatantId) : BattleEvent
    data class TurnGranted(val combatantId: CombatantId) : BattleEvent
    data class SynergyTriggered(val synergyId: SynergyId, val firstActorId: CombatantId, val secondActorId: CombatantId, val targetId: CombatantId) : BattleEvent
}
```

`actorId` is nullable on `DamageDealt`/`HealingApplied` specifically because
damage-over-time ticks (spec 004) and synergy bonuses (this feature, FR-006) are
genuinely actor-less — the same reasoning spec 004's R6 already established for DoT
("a fundamentally different mechanic... forcing an actor in would be worse than the
duplication"). This is what US1 scenario 3's "indistinguishable in shape" means: same
event type, same field set, `actorId` legitimately absent for that kind of occurrence —
not that every field must be populated identically.

Events don't carry their own turn index. Instead, `EventLog` is:

```kotlin
data class LoggedEvent(val turnIndex: Int, val event: BattleEvent)
data class EventLog(val entries: List<LoggedEvent> = emptyList()) {
    val turnsGranted: Int get() = entries.count { it.event is BattleEvent.TurnGranted }
    fun append(newEvents: List<BattleEvent>): EventLog { /* stamps turnIndex = turnsGranted so far, incrementing after each TurnGranted within the batch */ }
}
```

`append` stamps each incoming event with the count of `TurnGranted` events already in
the log at that point (incrementing mid-batch if the batch itself contains a
`TurnGranted`), giving every event a "which turn was this during" value with zero extra
state to thread through the pure `eventsFromX` functions — they stay turn-agnostic,
and only `append` (the one place accumulation actually happens, per FR-003) needs to
know about turn counting.

**Rationale**: Mirrors spec 002's `DamageFormula`/spec 004's `EffectKind` shape (closed
sealed interface, content/engine chooses a fixed kind set, Principle II — though here
the "content" is the engine's own occurrences, not catalog data). Deriving `turnIndex`
at append time rather than inside each `eventsFromX` function keeps those functions
symmetric with spec 004's R1/R6 precedent (pure functions of their own narrow inputs
only) and keeps "how turns are counted" in exactly one place, satisfying FR-010's
determinism requirement trivially (same append sequence ⇒ same stamps, always).

**Alternatives considered**: a monotonic global sequence number instead of a
turn-based index (doesn't satisfy the Assumptions' explicit "window unit: turns
consumed" requirement — a synergy window needs to compare in the *turn* unit, not raw
event count, since a single resolved action can produce a variable number of events
for e.g. `ALL_ENEMIES` targeting); requiring callers to pass an explicit turn counter
into every `eventsFromX` call (extra required parameter every call site must thread
correctly — rejected as needless surface area when `EventLog` can derive it from what
it already accumulates).

## R3 — Synergy detection: pure backward scan from each newly-appended qualifying event

**Decision**: A "qualifying action" (per spec.md's Assumptions) is any `DamageDealt` or
`HealingApplied` event with a non-null `actorId` — this is exactly the same nullability
distinction R2 introduced, reused for free as the qualification filter. `detectSynergyTriggers`
takes the events just appended (`newEvents`, the same list passed to `append`), the
resulting `log` (which already includes them), the current `battle: BattleState`
(for live capability lookups), and a `SynergyCatalog`:

```kotlin
fun detectSynergyTriggers(newEvents: List<BattleEvent>, log: EventLog, battle: BattleState, catalog: SynergyCatalog): List<SynergyTrigger>
```

For each qualifying event `E` in `newEvents` (candidate = the *closing* half of a
combo), and each `SynergyDefinition` where `E`'s actor currently has (live lookup via
`battle.find(E.actorId).combatant.capabilities.skills`) either of the definition's two
required skills: scan `log.entries` strictly *before* `E`'s own position, nearest-first,
for a qualifying event `E'` on the same `targetId`, within `E.turnIndex - E'.turnIndex
<= window`, whose actor is a *different* combatant (FR-008) and currently has the
*other* required skill. The first (nearest) match found triggers the synergy for
`(E'.actorId, E.actorId, targetId)`.

Because every call only pairs the *newest* qualifying event(s) against strictly earlier
ones, a given concrete pair of events is only ever evaluated once (when the later one
was "new") — no separate "already triggered" bookkeeping is needed to avoid duplicate
triggers for the same pair, and nothing prevents an *older* event from later pairing
with a *different, later* qualifying event too (correctly allowed — spec.md doesn't
forbid one occurrence participating in multiple synergies).

**Rationale**: "Nearest prior match" gives a single deterministic answer when multiple
eligible partners exist in the window (FR-010) without needing an arbitrary
tie-break rule invented from nothing. Doing the live capability lookup against
`battle` (rather than baking "had capability X" into the event itself) keeps
`BattleEvent` a pure occurrence record (Principle IV: structured facts, not derived
judgments) and trivially supports FR-008's "same combatant can't satisfy both roles"
check via simple identity comparison, no extra bookkeeping. Scoping the scan to only
`newEvents` as candidates (not literally every event in the log every time) keeps the
"per-newly-appended-event" cost bound the Performance Goals describe, while still being
a full scan of *prior* history per candidate — acceptable at the stated 2–10-combatant,
bounded-turn-count scale.

**Alternatives considered**: baking `capabilities` snapshot into each qualifying event
at creation time (event ceases to be a pure occurrence-fact and duplicates data already
available live from `Combatant`; also incorrect if a future feature ever made
capabilities time-varying — an unnecessary staleness risk this design avoids for free);
tracking "already-triggered pairs" in an explicit `Set` threaded through every call
(more state to carry for no behavioral gain, since the newest-event-only scan already
prevents duplicate pairing); triggering only the *first* eligible partner encountered
in forward (oldest-first) order instead of nearest (arbitrary and less intuitive —
"the combo that just completed" reads naturally as "with whoever set it up most
recently", matching how real-time combo systems in the genre this spec is modeling
typically work).

## R4 — `applySynergyBonus` reuses spec 004's actor-less clamp pattern, not `resolveAction`

**Decision**: `SynergyBonus` is a closed sealed interface (`BonusDamage(amount: Int)`
only, per spec.md's own example and Assumption "v1: exactly two required capabilities" —
no other bonus shape is requested). `applySynergyBonus(battle, targetId, bonus):
BattleState` applies it with the exact same three-line clamp arithmetic
`applyTickDamage` (spec 004, R6) uses: `(current - amount).coerceIn(0, maximum)`. A
matching `eventsFromSynergyBonus(oldBattle, targetId, bonus, newBattle)` produces the
`DamageDealt(actorId = null, ...)` event (plus a `CombatantDefeated` event if it
crosses to zero, same asymmetric check R6 below formalizes), and the orchestrating
caller separately appends the `SynergyTriggered` event (FR-009) alongside it.

**Rationale**: FR-006 requires the bonus go through "the existing resolution
mechanisms... never a new, separate health-modification pathway" — but a synergy bonus
has no actor and no `CombatAction` to submit (there is no "who is casting the combo
bonus"), the exact same mismatch spec 004's R6 identified for damage-over-time against
`resolveAction`'s actor/capability-gated entry point. Reusing that established,
already-justified small duplication (rather than synthesizing a fake self-targeting
`CombatAction` just to force a fit) is both smaller and more honest about what's
actually happening — a data-driven, automatic effect, not a submitted action.

**Alternatives considered**: synthesizing a `CombatAction` with the first triggering
actor as a stand-in actor (spec.md never asks a synergy's bonus to be attributable to
either specific actor as *the* actor — `SynergyTriggered` already names both
combatants, an actor-less bonus event is the more accurate record); adding a new public
health-mutation entry point to spec 002 (touches an existing spec 002 file — rejected,
FR-011, same reasoning as spec 004 R6's rejected alternative).

## R5 — Window unit and boundary: inclusive turn-index difference (spec Assumptions)

**Decision**: A synergy's `window: Int` (must be `> 0` at catalog validation) is
compared as `candidateEvent.turnIndex - priorEvent.turnIndex <= window` — inclusive at
the boundary, and note `window` counts turns, not events, so multiple qualifying
events stamped with the same `turnIndex` (produced during the same granted turn) always
satisfy any `window >= 0`, i.e., a same-turn combo is never excluded by an otherwise-
tight window.

**Rationale**: "Within a window of a few turns" (spec.md's own phrasing) reads as
inclusive — "within 2 turns" naturally includes exactly-2-turns-later, not
strictly-less-than. This directly matches spec 004's own duration semantics precedent
(a `duration`-turn effect ticks for that many turns inclusive of its final tick, R7
there) rather than inventing a different boundary convention for this feature alone.

**Alternatives considered**: strict `<` (would make "window = 2" behave like "1 full
turn of leeway", a common off-by-one surprise with no textual support in spec.md);
measuring in raw event count instead of `turnIndex` difference (rejected already by R2 —
contradicts the Assumptions' explicit "turns consumed" unit).

## R6 — Shared `defeatedEvents` helper: the resolution/tick asymmetry

**Decision**: A small shared private helper,
`defeatedEvents(wasDefeatedBefore: Boolean, resultingHealth: Int, combatantId: CombatantId): List<BattleEvent>`,
returns a single-element `CombatantDefeated` list when `!wasDefeatedBefore &&
resultingHealth == 0`, else empty. `eventsFromResolution` calls it once per
`ResolutionOutcome` (using the *original* `BattleState` passed into `resolveAction`
to answer "was defeated before" — safe because `resolveAction` guarantees each
`targetId` appears at most once per call, spec 002's `resolveTargets` never
duplicates). `eventsFromTick` and `eventsFromSynergyBonus` call it once per combatant
whose health changed, using the pre-tick/pre-bonus `BattleState` the same way.

Deriving tick-sourced damage itself (as opposed to just the defeat check) requires one
extra step `eventsFromResolution` doesn't: `tickStatusEffects` doesn't report
per-effect outcomes the way `resolveAction` reports `ResolutionOutcome`s, so
`eventsFromTick` recomputes them by walking `oldEffects.active` in the *same* map/list
order `tickStatusEffects`'s own loop uses (verified against its source, R6/R7 of spec
004) and folding each `DamageOverTime` effect's `amountPerTick` against a running
`current` starting from the pre-tick `HealthTrack` — mathematically identical to the
engine's own sequential clamp because every step in that fold is a pure subtraction
floored at zero (never an increase), so fold order only needs to match for
per-combatant *multi-DoT* determinism (FR-010), not for the final health value, which
is order-independent for a sum of non-negative subtractions.

**Rationale**: `resolveAction` produces its own outcome list (input parity is trivial);
`tickStatusEffects` doesn't, so genuine (small, justified) recomputation is required —
naming this asymmetry explicitly here avoids it reading as an oversight later. The
shared `defeatedEvents` helper avoids restating the "was it already at 0" check three
times (resolution, tick, synergy bonus) with three chances to get the boundary wrong.

**Alternatives considered**: modifying `tickStatusEffects` to also return per-effect
outcomes (touches an existing spec 004 file — rejected, FR-011); computing tick damage
events without regard to order (would risk non-deterministic per-event `resultingHealth`
stamps for a combatant under two simultaneous DoT effects — rejected, breaks FR-010).
