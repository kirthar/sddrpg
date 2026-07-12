# Data Model: Limit Breaks & Summons

**Feature**: 006-limit-breaks-summons | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1-R5)

Extends `core` additively: `Combatant`, `BattleState`, `CombatantId`, `resolveAction`,
`CommandKind`, `BattleEvent`, `EventLog` (specs 001-005) are consumed exactly as
published (spec FR-012); nothing here changes them.

## Catalog layer (data-driven, independent of spec 001's Catalog — same discipline as specs 004/005)

### LimitBreakDefinition
| Field | Type | Rules |
|---|---|---|
| id | LimitBreakId | spec 001's existing identifier type; unique within a `LimitBreakCatalog` |
| threshold | Int | must be > 0 at validation time |
| effectKind | EffectKind (spec 002) | DAMAGE or HEAL |
| formula | DamageFormula (spec 002) | magnitude, reused as-is |
| targeting | TargetingShape (spec 002) | reused as-is |
| element | ElementId? | optional, reused as-is |

### LimitBreakCatalog / validation
`LimitBreakCatalog(limitBreaks: List<LimitBreakDefinition> = emptyList())` →
`validateLimitBreakCatalog(catalog): LimitBreakCatalogResult` (`Valid` XOR
`Invalid(errors)`, accumulate-all, mirroring spec 001/004/005's `*CatalogResult`
convention): checks duplicate ids (`DuplicateId`) and non-positive threshold
(`NonPositiveThreshold`).

### SummonId (new identifier, defined in this feature — no summon id type existed before)
`@JvmInline value class SummonId(val value: String)`, same shape as spec 004's
`StatusEffectId` (a small identifier owned by its own feature, not spec 001's model).

### SummonDefinition
| Field | Type | Rules |
|---|---|---|
| id | SummonId | unique within a `SummonCatalog` |
| cost | Int | must be ≥ 0 at validation time (a free summon is legal; a negative cost is not) |
| effectKind | EffectKind | DAMAGE or HEAL |
| formula | DamageFormula | reused as-is |
| targeting | TargetingShape | reused as-is |
| element | ElementId? | optional, reused as-is |

### SummonCatalog / validation
`SummonCatalog(summons: List<SummonDefinition> = emptyList())` →
`validateSummonCatalog(catalog): SummonCatalogResult`: checks duplicate ids
(`DuplicateId`) and negative cost (`NegativeCost`).

## Battle-time state layer (new to this feature)

### LimitGaugeState (R2)
| Field | Type | Rules |
|---|---|---|
| gauges | Map\<CombatantId, Int\> | absent key ⇒ gauge 0 (or combatant has no active limit break, R5); value always in `[0, threshold]` for combatants that do |

### ResourceState (R3)
| Field | Type | Rules |
|---|---|---|
| current | Map\<CombatantId, Int\> | initialized once per battle from `combatant.stats[CoreStats.MP]` via `BattleState.initialResourceState()`; never negative |

## Operations

### chargeLimitGauge (R2, R5)
`(state: LimitGaugeState, battle: BattleState, events: List<BattleEvent>, catalog: LimitBreakCatalog) -> LimitGaugeState`

For each `DamageDealt` event in `events`: look up the target's active limit break via
`battle.find(targetId)!!.combatant.capabilities.limitBreaks.firstOrNull()` (R5). If
none, skip (no gauge entry created, US1 scenario 3). Otherwise look up that limit
break's `threshold` in `catalog` (skip gracefully if the id isn't found — same
stale-reference tolerance spec 004's analyze finding F2 established), and set that
combatant's gauge to `(current + event.amount).coerceAtMost(threshold)`.

### initialResourceState / deductResource (R3)
`BattleState.initialResourceState(): ResourceState` snapshots `CoreStats.MP` for every
participant. `deductResource(state, combatantId, amount): ResourceState` subtracts
unconditionally — callers MUST check sufficiency first (spec FR-008); this function
itself does not re-validate (mirrors `applyTickDamage`-style small helpers elsewhere
that trust their caller's already-established precondition).

### resolveLimitBreak (R1)
`(battle: BattleState, gauges: LimitGaugeState, actorId: CombatantId, targetIds: Set<CombatantId>, catalog: LimitBreakCatalog) -> LimitBreakResolutionResult`

1. Look up the actor's active limit break id (R5); if none, reject
   (`LimitBreakError.NoActiveLimitBreak`).
2. Look up its definition in `catalog`; if the actor's gauge (default 0) is below its
   `threshold`, reject (`LimitBreakError.GaugeNotFull`) — spec FR-004, US2 scenario 1.
3. Otherwise synthesize `CombatAction(actorId, CommandKind.SKILL, skillId = null,
   effectKind, element, formula, targeting)` from the definition and call
   `resolveAction(battle, action, targetIds)` unmodified (R1).
4. On `Resolved`, reset the actor's gauge to 0 (spec FR-006) and return
   `LimitBreakResolutionResult.Resolved(newState, newGauges, outcomes)`. On
   `Rejected` from `resolveAction` itself (e.g. a genuine `MissingCommand` if the
   class never granted `SKILL`), propagate as-is — gauge is left untouched.

### resolveSummon (R1)
`(battle: BattleState, resources: ResourceState, actorId: CombatantId, summonId: SummonId, targetIds: Set<CombatantId>, catalog: SummonCatalog) -> SummonResolutionResult`

1. Look up `summonId` in `catalog`; unknown id rejects (`SummonError.UnknownSummon`).
2. If the actor's current resource (default 0) is less than the summon's `cost`,
   reject (`SummonError.InsufficientResource`) with nothing deducted — spec FR-008.
3. Otherwise deduct the cost, synthesize `CombatAction(actorId, CommandKind.SUMMON,
   skillId = null, effectKind, element, formula, targeting)`, and call
   `resolveAction(battle, action, targetIds)` unmodified — `resolveAction`'s own
   existing `CommandKind.SUMMON` capability gate applies natively here (R1).
4. On `Resolved`, return `SummonResolutionResult.Resolved(newState, newResources,
   outcomes)` with the deduction already applied. On `Rejected` from `resolveAction`
   itself, propagate as-is — the already-deducted resource is NOT refunded (matching
   the spirit of spec 002's "an action never partially resolves" only applying to
   health outcomes, not to this feature's own pre-check; see Edge Cases below).

## Event layer (new file inside the existing `event` package — research R4)

### New BattleEvent variants
| Variant | Fields | Fires when |
|---|---|---|
| `GaugeFull` | `combatantId: CombatantId` | a `chargeLimitGauge` call crosses a combatant's gauge from below threshold to at-or-above threshold (once per crossing, not once per charge while already full) |
| `LimitBreakUsed` | `combatantId: CombatantId`, `limitBreakId: LimitBreakId` | a `resolveLimitBreak` call returns `Resolved` |
| `SummonCast` | `combatantId: CombatantId`, `summonId: SummonId` | a `resolveSummon` call returns `Resolved` |

### eventsFromGaugeCharge
`(before: LimitGaugeState, after: LimitGaugeState, catalog: LimitBreakCatalog) -> List<BattleEvent>`

Compares the two maps; for each combatant whose gauge crossed from below its
threshold to at-or-above it, emits one `GaugeFull`.

### eventsFromLimitBreak / eventsFromSummon
`(oldState: BattleState, actorId: CombatantId, id: LimitBreakId | SummonId, effectKind: EffectKind, result: LimitBreakResolutionResult | SummonResolutionResult) -> List<BattleEvent>`

On `Rejected`, returns an empty list (nothing happened). On `Resolved`, builds the
`DamageDealt`/`HealingApplied` (chosen by `effectKind`, the same selection rule spec
005's `eventsFromResolution` uses) plus a defeat check for each entry in
`result.outcomes` directly — **not** by reconstructing a synthetic
`ActionResolutionResult` to hand to spec 005's `eventsFromResolution` (an earlier
draft of this contract did that; it required callers to fabricate a fake resolution
result just to reuse a function, which is backwards — a wrapper deriving its own
events should do so directly). The defeat check reuses spec 005's `internal
defeatedEvents` helper: reusing it here is honest module-internal sharing between two
files that are both genuinely part of the `event` package (this file is placed there
*by Kotlin's own sealed-type rule*, research R4 — not as a workaround to reach across
an unrelated feature's encapsulation boundary), unlike the classId-style internals
this project's discipline otherwise avoids reaching into. The `LimitBreakUsed`/
`SummonCast` event itself is prepended before its effect's events, mirroring spec
005's `SynergyTriggered`-before-its-bonus-events ordering.

## Determinism guarantee (spec FR-011/SC-005)

Every operation above is a pure function of its inputs — no RNG, no wall-clock. Given
the same starting `LimitGaugeState`/`ResourceState`/`BattleState` and the same
sequence of charge/resolve calls, every call produces byte-for-byte identical results,
on both JVM and JS.
