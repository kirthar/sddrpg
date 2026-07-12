# Contract: Status Effects Public API

**Feature**: 004-status-effects | **Consumers**: future battle-loop feature, demos, tests

Normative signatures for the `status` package (commonMain,
`io.github.kirthar.sddrpg.core.status`). Consumes spec 001's `Combatant`/`StatId`/
`CoreStats`, spec 002's `BattleState`/`HealthTrack`, and spec 003's
`ActiveTimeBattleScheduler` unchanged. See [data-model.md](../data-model.md) for field
tables and algorithm detail.

## Kotlin API

```kotlin
// EffectKind.kt
sealed interface EffectKind {
    data class StatModifier(val statId: StatId, val delta: Int) : EffectKind
    data object Incapacitate : EffectKind
    data class DamageOverTime(val amountPerTick: Int) : EffectKind
}

// StatusEffectDefinition.kt
data class StatusEffectDefinition(
    val id: StatusEffectId,
    val displayName: String,
    val kind: EffectKind,
    val duration: Int,
)

data class StatusEffectCatalog(val effects: List<StatusEffectDefinition> = emptyList())

sealed interface StatusEffectCatalogError {
    data class DuplicateId(val id: String) : StatusEffectCatalogError
    data class NonPositiveDuration(val definitionId: String, val duration: Int) : StatusEffectCatalogError
}

sealed interface StatusEffectCatalogResult {
    data class Valid(val catalog: StatusEffectCatalog) : StatusEffectCatalogResult
    data class Invalid(val errors: List<StatusEffectCatalogError>) : StatusEffectCatalogResult
}

fun validateStatusEffectCatalog(catalog: StatusEffectCatalog): StatusEffectCatalogResult

// StatusEffectState.kt
data class ActiveEffect(val effectId: StatusEffectId, val remainingDuration: Int)
data class StatusEffectState(val active: Map<CombatantId, List<ActiveEffect>> = emptyMap())

fun applyStatusEffect(
    state: StatusEffectState,
    combatantId: CombatantId,
    effectId: StatusEffectId,
    catalog: StatusEffectCatalog,
): StatusEffectState

fun removeStatusEffect(
    state: StatusEffectState,
    combatantId: CombatantId,
    effectId: StatusEffectId,
): StatusEffectState

// StatusEffectResolution.kt
data class StatusTickResult(val newBattle: BattleState, val newEffects: StatusEffectState)

/** Spec FR-007: one call = one tick, applied to every active effect on every combatant. */
fun tickStatusEffects(
    effects: StatusEffectState,
    battle: BattleState,
    catalog: StatusEffectCatalog,
): StatusTickResult

/**
 * Produces the BattleState resolveAction/nextTurn should actually be called with:
 * stat-modifier deltas applied additively per stat (floored at 0), Incapacitate-kind
 * effects force effective Speed to 0. The input [battle] and every [Combatant] inside
 * it are left untouched — this returns a new value, nothing is mutated in place.
 */
fun deriveEffectiveBattleState(
    battle: BattleState,
    effects: StatusEffectState,
    catalog: StatusEffectCatalog,
): BattleState
```

Stability rules:
- `deriveEffectiveBattleState`, `tickStatusEffects`, `applyStatusEffect`,
  `removeStatusEffect` are all pure: same inputs always produce structurally equal
  outputs (spec FR-010, SC-007).
- A participant with no active effects is returned byte-identical (same `Combatant`
  reference) by `deriveEffectiveBattleState` — wrapping only happens where it changes
  something observable.
- `removeStatusEffect` is idempotent: removing an effect not currently active on the
  named combatant returns a structurally equal `StatusEffectState`, never an error.
- `applyStatusEffect` on an already-active effect **replaces** that effect's
  `ActiveEffect` (duration reset to full) — it never produces two entries for the same
  `(combatantId, effectId)` pair (spec FR-008).
- Callers MUST call `deriveEffectiveBattleState` and pass its result into
  `resolveAction`/`nextTurn` for status effects to have any influence — those two
  functions receive no new parameters and know nothing about the `status` package.

## Typical usage (informative, not itself a contract)

```kotlin
// Before resolving an action or asking who's next, derive the effective view:
val effectiveBattle = deriveEffectiveBattleState(battle, statusEffects, statusCatalog)
val scheduled = ActiveTimeBattleScheduler.nextTurn(schedule, effectiveBattle)
// ... resolveAction(effectiveBattle, action, targets) similarly ...

// After a turn is consumed (spec 003's markSpent), tick every active effect once:
val tickResult = tickStatusEffects(statusEffects, battle, statusCatalog)
battle = tickResult.newBattle
statusEffects = tickResult.newEffects

// Applying an effect as a resolved action's side effect:
statusEffects = applyStatusEffect(statusEffects, targetId, StatusEffectId("poison"), statusCatalog)
```
