# Research: Actions & Damage Resolution

**Feature**: 002-actions-damage-resolution | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here.

## R1 — Where does "current health" live? (spec FR-011, FR-012)

**Decision**: Introduce `HealthTrack(current: Int, maximum: Int)` and
`BattleCombatant(combatant: Combatant, health: HealthTrack)`, aggregated in an
immutable `BattleState(participants: List<BattleCombatant>)`. `maximum` is captured
from `combatant.stats[CoreStats.HP]` when a `BattleCombatant` is created (battle start);
`current` starts equal to `maximum`. `resolveAction` is a pure function
`(BattleState, CombatAction, targetIds) -> ActionResolutionResult` returning a **new**
`BattleState` plus the list of `ResolutionOutcome`s — never mutating its input.

**Rationale**: Spec 001 explicitly deferred the current-vs-maximum invariant ("enforced
once battle state exists") without prescribing how; spec 002 is that feature, and its
own FR-012 forbids touching `Combatant`'s public shape. Wrapping rather than extending
is the only option that satisfies both: `Combatant.stats[HP]` keeps meaning "this
combatant's maximum HP at its current level/class" (unchanged, spec 001 semantics
intact), while `BattleState` — a type spec 002 owns — is where the battle-time number
that actually goes up and down lives. This also keeps the design honest about scope:
nothing outside this feature needs to know battle state exists yet.

**Alternatives considered**: adding a mutable `currentHealth` field to `Combatant`
(violates FR-012 outright — forbidden); a global mutable HP map keyed by
`CombatantId` (breaks Principle I's "no hidden state" — every function would need an
implicit dependency instead of an explicit parameter); recomputing "defeated" from
`stats[HP]` directly (impossible — `Combatant.stats` is immutable derived data, spec 001
never designed it to decrease).

## R2 — Action representation (Command pattern, spec FR-001)

**Decision**: `CombatAction` is a plain data class, not an object with an `execute()`
method:

```kotlin
data class CombatAction(
    val actorId: CombatantId,
    val command: CommandKind,
    val skillId: SkillId? = null,
    val effectKind: EffectKind,       // DAMAGE | HEAL
    val element: ElementId? = null,
    val formula: DamageFormula,
    val targeting: TargetingShape,
)
```

**Rationale**: The constitution's Command pattern principle is about actions being
first-class, submittable, decoupled objects — not about object-oriented `execute()`
dispatch, which would smuggle mutation/side effects into a module that must stay pure
(Principle I). A data class submitted to a pure `resolveAction` function gets the same
decoupling (an action is data that *can* be queued, logged, replayed, inspected by
tests) with zero risk of hidden state. This also keeps action authoring symmetric with
spec 001's fully data-driven definitions.

**Alternatives considered**: `sealed interface CombatAction { fun resolve(state): ... }`
(couples the action's shape to its resolution algorithm — every new action type would
need its own resolution code, violating Principle II's substitutability goal for
formulas); an event-sourced command bus (real overkill for one-shot resolution; no
consumer needs it yet — Principle V).

## R3 — Damage/heal formulas (Strategy, spec FR-004)

**Decision**: `DamageFormula` is a sealed interface with one pure method
`rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int`, always returning a
value ≥ 0:

```kotlin
sealed interface DamageFormula {
    fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int

    data class Physical(val power: Int) : DamageFormula {
        // max(0, power + ATTACK - DEFENSE)
    }
    data class Magical(val power: Int) : DamageFormula {
        // max(0, power + MAGIC - RESISTANCE)
    }
    data class Fixed(val amount: Int) : DamageFormula {
        // ignores stats entirely — for items with a flat, pre-authored effect
    }
}
```

`power` is the action's own authored strength (0 for a plain unmodified ATTACK
command; a skill/spell/item's own value otherwise) — content data, not hardcoded per
skill.

**Rationale**: Two stat-driven shapes cover spec FR-004's explicit minimum
(physical-style, magical-style); `Fixed` is the natural third shape for ITEM actions
whose effect is authored directly rather than derived from combat stats, and costs
nothing extra to include since the sealed set is closed and exhaustive-matched anyway.
Flooring at 0 inside the formula (not deferred to a later step) is this feature's
answer to the spec's edge case ("a formula's raw result may be negative... one
documented rounding/flooring rule"): a very defensive target can make an attack deal
zero raw magnitude, which is the ordinary, expected JRPG-genre outcome, not an error.

**Alternatives considered**: one generic formula with a `type` enum and a stat-name
parameter list (harder to keep type-safe, no real flexibility gain over two concrete
cases plus `Fixed`); formula-as-expression-string (deferred already in spec 001 R5 for
growth curves — same reasoning applies, no interpreter in v1).

## R4 — Elemental adjustment multipliers (spec FR-005/FR-006)

**Decision**: One fixed table, applied to the *signed* health delta (not the raw
magnitude) so a single function handles both damage and heal symmetrically:

| Affinity stance | Multiplier |
|---|---|
| NEUTRAL | 1.0 |
| WEAKNESS | 2.0 |
| RESISTANCE | 0.5 |
| IMMUNITY | 0.0 |
| ABSORPTION | −1.0 |

Applied as: `baseDelta = if (effectKind == DAMAGE) -rawMagnitude else +rawMagnitude`
(negative delta = health decreases); if `element != null`,
`adjustedDelta = roundHalfAwayFromZero(baseDelta * multiplier(target.affinityTo(element)))`;
if `element == null`, `adjustedDelta = baseDelta` unchanged (FR-005's "skip entirely").

**Rationale**: These are the standard weakness=double / resist=half / immune=zero /
absorb=full-reversal values used across the FF-style genre this project targets
(README), satisfying FR-006's "one fixed multiplier per stance" with numbers a player
would recognize as correct. Applying the multiplier to the *signed* delta rather than
writing four branch-specific cases is what makes ABSORPTION's "reverses the sign"
requirement (FR-005) fall out of the same formula as WEAKNESS/RESISTANCE (multiply by
−1) instead of needing special-cased logic — directly serving SC-006 (new stance
behavior without touching the action/targeting code path, since the table is the only
thing that would change).

**Alternatives considered**: per-element custom multipliers (spec FR-006 explicitly
asks for one fixed table, not per-element data — that door stays open for a future spec
if a game needs it, not this one, per Principle V); percentage-based additive
resistance (more configurable but no spec requirement motivates the complexity now).

## R5 — Rounding rule (spec edge case, Assumptions)

**Decision**: `roundHalfAwayFromZero(x: Double): Int` — ties (`.5`) round away from
zero (7.5 → 8, −7.5 → −8), matching the value already named in spec.md's Assumptions
section. Applied once, only at the elemental-adjustment step (the only place
non-integer arithmetic occurs, since RESISTANCE's ×0.5 can produce a `.5`); formula
`rawMagnitude` stays `Int` throughout (R3), so no rounding happens there.

**Rationale**: A single, named, documented rounding point keeps the "identical inputs →
identical outputs" guarantee (spec FR-006, SC-003) trivially auditable — there is
exactly one place fractional arithmetic can occur, and it is pure Kotlin `Double` math
(IEEE-754, deterministic on both JVM and JS for these small magnitudes), never
platform-dependent random behavior.

**Alternatives considered**: banker's rounding (less intuitive for players expecting
"7.5 damage becomes 8", no justification without a documented reason); truncation
toward zero (would silently under-deliver resistance-reduced damage, unintuitive).

## R6 — Targeting shapes & allegiance semantics (spec FR-007/FR-008/FR-009)

**Decision**: `TargetingShape` enum: `SINGLE_ALLY, SINGLE_ENEMY, SELF, ALL_ALLIES,
ALL_ENEMIES, ALL`. "Ally" means same `Allegiance` as the actor and **includes the
actor itself** (a single-target heal can target the caster); `SELF` is a stricter shape
that forces the target set to be exactly `{actorId}` with no choice. "Enemy" means
opposite `Allegiance`. `ALL` means every participant regardless of allegiance. Shape
validation happens before defeated-combatant filtering: a submission's target-id set
must match the shape's cardinality/scope *as authored* (SINGLE_* exactly one id of the
right scope, SELF exactly the actor, ALL_* the full matching scope or a subset a
caller pre-filtered) — then defeated participants are excluded from the *resolved* set
(FR-009), which may legitimately leave zero outcomes (edge case) without that being a
validation failure.

**Rationale**: Including self in "ally" shapes matches genre convention (party-wide or
single-target heals commonly can target the caster) and keeps `SELF` meaningfully
different (some actions, like a future Defend, must *never* be redirectable).
Validating shape before defeated-filtering is what makes the "already-defeated single
target" edge case unambiguous: explicitly selecting a defeated combatant with a
SINGLE_* shape is a rejection (you can't single-target a corpse), while an ALL_* shape
naturally narrows without failing (that specific exclusion rule is spec'd, FR-009).

**Alternatives considered**: excluding self from "ally" shapes (would force `SELF` to
be reused everywhere the actor might want to self-target with a shared skill,
duplicating skill definitions); silently ignoring defeated combatants in explicit
SINGLE_* submissions instead of rejecting (removes useful UI/AI feedback that the
chosen target is no longer legal — worse debuggability, against Principle I's replay/
debug intent).

## R7 — DEFEND and ITEM commands' resolution shape (spec User Story scope)

**Decision**: `DEFEND` resolves through the same pipeline as any other action but
with `TargetingShape.SELF` and `DamageFormula.Fixed(0)` (zero-magnitude, no element) —
proving it is a validly gated, capability-checked, self-targeted action with a
well-defined (empty) outcome, while its actual stateful effect ("reduce next incoming
damage") is explicitly out of this feature's scope (spec Assumptions: status/turn
state is a later feature). `ITEM` actions reuse `DamageFormula.Fixed` for their
authored flat effect (R3); item consumption/inventory bookkeeping is likewise out of
scope (spec Assumptions).

**Rationale**: This keeps every `CommandKind` from spec 001 representable and testable
by this feature's pipeline (capability gating exercises all five gated commands) without
inventing unspec'd mechanics (a defense-reduction rule, an inventory system) that belong
to later features and would be speculative implementation (Principle V).

**Alternatives considered**: excluding DEFEND/ITEM from this feature's resolution path
entirely (would leave two of spec 001's five `CommandKind`s unexercised by any test,
and contradicts spec FR-001 which lists both explicitly as in-scope action types).

## R8 — Resource costs, hit/miss, and turn sequencing: confirmed out of scope

**Decision**: No MP/resource cost checks, no hit/miss/critical RNG, no multi-action
turn loop in this feature — `resolveAction` resolves exactly one submitted action
against one `BattleState` snapshot and returns.

**Rationale**: Directly stated in spec.md's Assumptions section; restated here because
it drives R2's pure-data `CombatAction` shape (no cost fields) and confirms Constitution
Check gate I (no RNG) has no exception to track.

**Alternatives considered**: none — this is a spec-mandated boundary, not an open
design choice.
