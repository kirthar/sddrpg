# Contract: Limit Breaks & Summons Public API

**Feature**: 006-limit-breaks-summons | **Consumers**: future battle-loop feature
(008 console demo), demos, tests

Normative signatures for the `limitbreak` package (commonMain,
`io.github.kirthar.sddrpg.core.limitbreak`) and the one new file added to the
existing `event` package. Consumes spec 001's `Combatant`/`LimitBreakId`/`CoreStats`,
spec 002's `BattleState`/`CombatAction`/`CommandKind`/`resolveAction`, and spec 005's
`BattleEvent`/`EventLog` unchanged. See [data-model.md](../data-model.md) for field
tables and algorithm detail.

## Kotlin API

```kotlin
// LimitBreakDefinition.kt
data class LimitBreakDefinition(
    val id: LimitBreakId,
    val threshold: Int,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

data class LimitBreakCatalog(val limitBreaks: List<LimitBreakDefinition> = emptyList())

sealed interface LimitBreakCatalogError {
    data class DuplicateId(val id: String) : LimitBreakCatalogError
    data class NonPositiveThreshold(val definitionId: String, val threshold: Int) : LimitBreakCatalogError
}

sealed interface LimitBreakCatalogResult {
    data class Valid(val catalog: LimitBreakCatalog) : LimitBreakCatalogResult
    data class Invalid(val errors: List<LimitBreakCatalogError>) : LimitBreakCatalogResult
}

fun validateLimitBreakCatalog(catalog: LimitBreakCatalog): LimitBreakCatalogResult

// SummonDefinition.kt
data class SummonId(val value: String)

data class SummonDefinition(
    val id: SummonId,
    val cost: Int,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

data class SummonCatalog(val summons: List<SummonDefinition> = emptyList())

sealed interface SummonCatalogError {
    data class DuplicateId(val id: String) : SummonCatalogError
    data class NegativeCost(val definitionId: String, val cost: Int) : SummonCatalogError
}

sealed interface SummonCatalogResult {
    data class Valid(val catalog: SummonCatalog) : SummonCatalogResult
    data class Invalid(val errors: List<SummonCatalogError>) : SummonCatalogResult
}

fun validateSummonCatalog(catalog: SummonCatalog): SummonCatalogResult

// LimitGaugeState.kt
data class LimitGaugeState(val gauges: Map<CombatantId, Int> = emptyMap())

fun chargeLimitGauge(
    state: LimitGaugeState,
    battle: BattleState,
    events: List<BattleEvent>,
    catalog: LimitBreakCatalog,
): LimitGaugeState

// ResourceState.kt
data class ResourceState(val current: Map<CombatantId, Int> = emptyMap())

fun BattleState.initialResourceState(): ResourceState

fun deductResource(state: ResourceState, combatantId: CombatantId, amount: Int): ResourceState

// LimitBreakSummonResolution.kt
sealed interface LimitBreakError {
    data class NoActiveLimitBreak(val actorId: CombatantId) : LimitBreakError
    data class GaugeNotFull(val actorId: CombatantId, val current: Int, val threshold: Int) : LimitBreakError
    data class ActionRejected(val actorId: CombatantId, val underlying: ActionError) : LimitBreakError
}

sealed interface LimitBreakResolutionResult {
    data class Resolved(val newState: BattleState, val newGauges: LimitGaugeState, val outcomes: List<ResolutionOutcome>) : LimitBreakResolutionResult
    data class Rejected(val error: LimitBreakError) : LimitBreakResolutionResult
}

fun resolveLimitBreak(
    battle: BattleState,
    gauges: LimitGaugeState,
    actorId: CombatantId,
    targetIds: Set<CombatantId>,
    catalog: LimitBreakCatalog,
): LimitBreakResolutionResult

sealed interface SummonError {
    data class UnknownSummon(val summonId: SummonId) : SummonError
    data class InsufficientResource(val actorId: CombatantId, val required: Int, val available: Int) : SummonError
    data class ActionRejected(val actorId: CombatantId, val underlying: ActionError) : SummonError
}

sealed interface SummonResolutionResult {
    data class Resolved(val newState: BattleState, val newResources: ResourceState, val outcomes: List<ResolutionOutcome>) : SummonResolutionResult
    data class Rejected(val error: SummonError) : SummonResolutionResult
}

fun resolveSummon(
    battle: BattleState,
    resources: ResourceState,
    actorId: CombatantId,
    summonId: SummonId,
    targetIds: Set<CombatantId>,
    catalog: SummonCatalog,
): SummonResolutionResult

// event/LimitBreakSummonEvents.kt (new file, same package as BattleEvent)
data class GaugeFull(val combatantId: CombatantId) : BattleEvent
data class LimitBreakUsed(val combatantId: CombatantId, val limitBreakId: LimitBreakId) : BattleEvent
data class SummonCast(val combatantId: CombatantId, val summonId: SummonId) : BattleEvent

fun eventsFromGaugeCharge(battle: BattleState, before: LimitGaugeState, after: LimitGaugeState, catalog: LimitBreakCatalog): List<BattleEvent>

fun eventsFromLimitBreak(
    oldState: BattleState,
    actorId: CombatantId,
    limitBreakId: LimitBreakId,
    effectKind: EffectKind,
    result: LimitBreakResolutionResult,
): List<BattleEvent>

fun eventsFromSummon(
    oldState: BattleState,
    actorId: CombatantId,
    summonId: SummonId,
    effectKind: EffectKind,
    result: SummonResolutionResult,
): List<BattleEvent>
```

Stability rules:

- `chargeLimitGauge`, `deductResource`, `resolveLimitBreak`, `resolveSummon`,
  `eventsFromGaugeCharge`, `eventsFromLimitBreak`, `eventsFromSummon`,
  `validateLimitBreakCatalog`, `validateSummonCatalog` are all pure: same inputs
  always produce structurally equal outputs (spec FR-011, SC-005).
- `resolveAction` and `CommandKind` receive no new parameters/values and know
  nothing about the `limitbreak` package (spec FR-012) — `resolveLimitBreak`/
  `resolveSummon` are called *before* `resolveAction`, never the reverse.
- A combatant whose class declares no limit break never accumulates gauge state
  (`chargeLimitGauge` silently skips them) — `LimitGaugeState.gauges` has no entry for
  such a combatant, ever (spec FR-001, edge case).
- `resolveSummon`'s resource deduction is NOT refunded if `resolveAction` itself then
  rejects the synthesized action (e.g. the caster's class never granted
  `CommandKind.SUMMON`) — callers authoring content are expected to only grant summon
  capability alongside `SUMMON`, the same convention `resolveLimitBreak` relies on for
  `SKILL` (research R1); this is a content-authoring responsibility, not an engine gap.
- `GaugeFull` fires exactly once per crossing from below-threshold to at-or-above
  threshold — a combatant already at threshold who takes more damage (clamped, no
  further gauge change) does not re-fire it.
- `LimitBreakUsed`/`SummonCast` and the `DamageDealt`/`HealingApplied`/
  `CombatantDefeated` events their own resolution produced are built together, in one
  call, by `eventsFromLimitBreak`/`eventsFromSummon` themselves — they derive
  directly from `result.outcomes` (choosing `DamageDealt` vs `HealingApplied` by the
  passed-in `effectKind`, the same rule spec 005's `eventsFromResolution` uses) rather
  than requiring the caller to reconstruct a synthetic `ActionResolutionResult` just to
  reuse that function. The trigger event (`LimitBreakUsed`/`SummonCast`) is always
  first in the returned list, mirroring spec 005's `SynergyTriggered`-before-its-
  bonus-events ordering. `Rejected` results return an empty list.

## Typical usage (informative, not itself a contract)

```kotlin
// Resolving a normal action, then charging gauges from what it produced:
val result = resolveAction(battle, action, targetIds)
val actionEvents = eventsFromResolution(battle, action, result)
if (result is ActionResolutionResult.Resolved) battle = result.newState
val gaugeBefore = gauges
gauges = chargeLimitGauge(gauges, battle, actionEvents, limitBreakCatalog)
log = log.append(actionEvents + eventsFromGaugeCharge(battle, gaugeBefore, gauges, limitBreakCatalog))

// Using a limit break once a gauge is full:
val oldBattle = battle
val lbResult = resolveLimitBreak(battle, gauges, actorId, targetIds, limitBreakCatalog)
if (lbResult is LimitBreakResolutionResult.Resolved) {
    battle = lbResult.newState
    gauges = lbResult.newGauges
}
log = log.append(eventsFromLimitBreak(oldBattle, actorId, limitBreakId, definition.effectKind, lbResult))

// Casting a summon:
val oldBattle2 = battle
val summonResult = resolveSummon(battle, resources, actorId, summonId, targetIds, summonCatalog)
if (summonResult is SummonResolutionResult.Resolved) {
    battle = summonResult.newState
    resources = summonResult.newResources
}
log = log.append(eventsFromSummon(oldBattle2, actorId, summonId, definition.effectKind, summonResult))
```
