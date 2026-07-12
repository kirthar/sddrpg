# Contract: Battle Events & Synergies Public API

**Feature**: 005-battle-events-synergies | **Consumers**: future battle-loop feature
(008 console demo), demos, tests

Normative signatures for the `event` package (commonMain,
`io.github.kirthar.sddrpg.core.event`). Consumes spec 001's `Combatant`/`CombatantId`/
`SkillId`, spec 002's `BattleState`/`CombatAction`/`ActionResolutionResult`, spec 003's
`ScheduleResult`, and spec 004's `StatusEffectState`/`StatusEffectCatalog`/
`StatusTickResult` unchanged. See [data-model.md](../data-model.md) for field tables
and algorithm detail.

## Kotlin API

```kotlin
// BattleEvent.kt
sealed interface BattleEvent {
    data class DamageDealt(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class HealingApplied(val actorId: CombatantId?, val targetId: CombatantId, val amount: Int, val resultingHealth: Int) : BattleEvent
    data class StatusEffectApplied(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class StatusEffectExpired(val combatantId: CombatantId, val effectId: StatusEffectId) : BattleEvent
    data class CombatantDefeated(val combatantId: CombatantId) : BattleEvent
    data class TurnGranted(val combatantId: CombatantId) : BattleEvent
    data class SynergyTriggered(
        val synergyId: SynergyId,
        val firstActorId: CombatantId,
        val secondActorId: CombatantId,
        val targetId: CombatantId,
    ) : BattleEvent
}

data class LoggedEvent(val turnIndex: Int, val event: BattleEvent)

data class EventLog(val entries: List<LoggedEvent> = emptyList()) {
    val turnsGranted: Int get() = entries.count { it.event is BattleEvent.TurnGranted }
    fun append(newEvents: List<BattleEvent>): EventLog
}

// EventDerivation.kt
fun eventsFromResolution(
    oldState: BattleState,
    action: CombatAction,
    result: ActionResolutionResult,
): List<BattleEvent>

fun eventsFromTick(
    oldEffects: StatusEffectState,
    oldBattle: BattleState,
    catalog: StatusEffectCatalog,
    result: StatusTickResult,
): List<BattleEvent>

fun eventsFromSchedule(result: ScheduleResult<*>): List<BattleEvent>

fun eventsFromApply(combatantId: CombatantId, effectId: StatusEffectId): List<BattleEvent>

fun eventsFromSynergyBonus(
    oldBattle: BattleState,
    targetId: CombatantId,
    bonus: SynergyBonus,
    newBattle: BattleState,
): List<BattleEvent>

// SynergyDefinition.kt
data class SynergyId(val value: String)

sealed interface SynergyBonus {
    data class BonusDamage(val amount: Int) : SynergyBonus
}

data class SynergyDefinition(
    val id: SynergyId,
    val firstSkillId: SkillId,
    val secondSkillId: SkillId,
    val window: Int,
    val bonus: SynergyBonus,
)

data class SynergyCatalog(val synergies: List<SynergyDefinition> = emptyList())

sealed interface SynergyCatalogError {
    data class DuplicateId(val id: String) : SynergyCatalogError
    data class SameSkill(val id: String, val skillId: String) : SynergyCatalogError
    data class NonPositiveWindow(val id: String, val window: Int) : SynergyCatalogError
}

sealed interface SynergyCatalogResult {
    data class Valid(val catalog: SynergyCatalog) : SynergyCatalogResult
    data class Invalid(val errors: List<SynergyCatalogError>) : SynergyCatalogResult
}

fun validateSynergyCatalog(catalog: SynergyCatalog): SynergyCatalogResult

// SynergyResolution.kt
data class SynergyTrigger(
    val definition: SynergyDefinition,
    val firstActorId: CombatantId,
    val secondActorId: CombatantId,
    val targetId: CombatantId,
)

/** Spec FR-005/FR-007/FR-008: nearest-prior-match scan over [newEvents] only (research R3). */
fun detectSynergyTriggers(
    newEvents: List<BattleEvent>,
    log: EventLog,
    battle: BattleState,
    catalog: SynergyCatalog,
): List<SynergyTrigger>

/** Spec FR-006: same clamp arithmetic spec 004's applyTickDamage uses, never resolveAction. */
fun applySynergyBonus(
    battle: BattleState,
    targetId: CombatantId,
    bonus: SynergyBonus,
): BattleState
```

Stability rules:

- `eventsFromResolution`/`eventsFromTick`/`eventsFromSchedule`/`eventsFromSynergyBonus`/
  `detectSynergyTriggers`/`applySynergyBonus`/`validateSynergyCatalog` are all pure:
  same inputs always produce structurally equal outputs (spec FR-010, SC-005).
- `resolveAction`, `tickStatusEffects`, and `TurnScheduler.nextTurn`/`markSpent`
  receive no new parameters and know nothing about the `event` package (spec FR-001,
  FR-011) — every `eventsFromX` function is called *after* the matching spec 002/003/004
  function, using its already-produced result.
- `EventLog.append` never drops or reorders previously-recorded entries (FR-003); it
  only ever grows `entries`.
- `detectSynergyTriggers` only considers `newEvents` as the *closing* half of a combo —
  callers MUST call it with exactly the same list they just passed to
  `EventLog.append` (and the post-append `log`) for FR-005/SC-002 to hold; calling it
  with a stale or partial `newEvents` list under-detects.
- `SynergyTriggered` and the events produced by its own `SynergyBonus` (via
  `eventsFromSynergyBonus`) MUST be appended together in the same `append` call so
  US4's "distinct from the events that caused it" always holds without a partially-
  recorded intermediate state.
- `validateSynergyCatalog` accumulates every error found (never stops at the first),
  mirroring spec 001's `CatalogResult`/spec 004's `StatusEffectCatalogResult`
  convention.

## Typical usage (informative, not itself a contract)

```kotlin
// Resolving an action and recording what happened:
val result = resolveAction(battle, action, targetIds)
val events = eventsFromResolution(battle, action, result)
log = log.append(events)
if (result is ActionResolutionResult.Resolved) battle = result.newState

// Detecting and applying any synergy the just-appended events complete:
for (trigger in detectSynergyTriggers(events, log, battle, synergyCatalog)) {
    val before = battle
    battle = applySynergyBonus(battle, trigger.targetId, trigger.definition.bonus)
    val bonusEvents = eventsFromSynergyBonus(before, trigger.targetId, trigger.definition.bonus, battle)
    log = log.append(
        listOf(BattleEvent.SynergyTriggered(trigger.definition.id, trigger.firstActorId, trigger.secondActorId, trigger.targetId)) + bonusEvents
    )
}

// Applying a status effect:
statusEffects = applyStatusEffect(statusEffects, targetId, StatusEffectId("poison"), statusCatalog)
log = log.append(eventsFromApply(targetId, StatusEffectId("poison")))

// Ticking status effects:
val tickResult = tickStatusEffects(statusEffects, battle, statusCatalog)
log = log.append(eventsFromTick(statusEffects, battle, statusCatalog, tickResult))
battle = tickResult.newBattle
statusEffects = tickResult.newEffects

// Asking who's next:
val scheduled = ActiveTimeBattleScheduler.nextTurn(schedule, battle)
log = log.append(eventsFromSchedule(scheduled))
```
