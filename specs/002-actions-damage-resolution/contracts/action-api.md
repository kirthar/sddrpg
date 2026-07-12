# Contract: Action Resolution Public API

**Feature**: 002-actions-damage-resolution | **Consumers**: future `TurnScheduler`
feature (003), demos, tests

Normative signatures for the `action` package (commonMain,
`io.github.kirthar.sddrpg.core.action`). Consumes spec 001's
`io.github.kirthar.sddrpg.core.combatant.Combatant` and
`io.github.kirthar.sddrpg.core.catalog.CommandKind` unchanged. See
[data-model.md](../data-model.md) for field tables.

## Kotlin API

```kotlin
// BattleState.kt
data class HealthTrack(val current: Int, val maximum: Int)

data class BattleCombatant(val combatant: Combatant, val health: HealthTrack) {
    val isDefeated: Boolean get() = health.current == 0
}

data class BattleState(val participants: List<BattleCombatant>) {
    fun find(id: CombatantId): BattleCombatant  // throws if absent — internal use after validation
}

/** Snapshots a Roster into starting battle state: current = maximum for every participant. */
fun Roster.toBattleState(): BattleState

// TargetingShape.kt
enum class TargetingShape { SINGLE_ALLY, SINGLE_ENEMY, SELF, ALL_ALLIES, ALL_ENEMIES, ALL }

// DamageFormula.kt
sealed interface DamageFormula {
    fun rawMagnitude(actorStats: StatBlock, targetStats: StatBlock): Int

    data class Physical(val power: Int) : DamageFormula
    data class Magical(val power: Int) : DamageFormula
    data class Fixed(val amount: Int) : DamageFormula
}

// CombatAction.kt
enum class EffectKind { DAMAGE, HEAL }

data class CombatAction(
    val actorId: CombatantId,
    val command: CommandKind,
    val skillId: SkillId? = null,
    val effectKind: EffectKind,
    val element: ElementId? = null,
    val formula: DamageFormula,
    val targeting: TargetingShape,
)

// ActionResolution.kt
data class ResolutionOutcome(
    val targetId: CombatantId,
    val appliedDelta: Int,
    val affinityApplied: Affinity?,
    val resultingHealth: Int,
)

sealed interface ActionError {
    data class DefeatedActor(val actorId: CombatantId) : ActionError
    data class MissingCommand(val actorId: CombatantId, val command: CommandKind) : ActionError
    data class MissingSkill(val actorId: CombatantId, val skillId: SkillId) : ActionError
    data class UnknownTarget(val targetId: CombatantId) : ActionError
    data class TargetShapeMismatch(val shape: TargetingShape, val reason: String) : ActionError
}

sealed interface ActionResolutionResult {
    data class Resolved(val newState: BattleState, val outcomes: List<ResolutionOutcome>) : ActionResolutionResult
    data class Rejected(val error: ActionError) : ActionResolutionResult
}

/**
 * Pure: same (state, action, targetIds) always returns an equal result (spec FR-013,
 * SC-003). Never mutates [state] or any [Combatant]/[BattleCombatant] within it.
 */
fun resolveAction(
    state: BattleState,
    action: CombatAction,
    targetIds: Set<CombatantId>,
): ActionResolutionResult
```

Stability rules:
- `resolveAction` never throws for well-formed inputs (unresolvable ids in `targetIds`
  or `action.actorId` are reported via `ActionResolutionResult.Rejected`, not exceptions).
- On `Rejected`, no `HealthTrack` in `state` is read as changed by the caller — the
  function only constructs a new `BattleState` on the `Resolved` path (spec SC-002:
  rejection happens strictly before any health change).
- `Resolved.newState` differs from the input `state` only in the `HealthTrack` of
  participants named in `outcomes`; every other field of every `BattleCombatant`
  (including the wrapped `Combatant` itself) is identical by structural equality.
- `HealthTrack.current` is always within `[0, HealthTrack.maximum]` in any `BattleState`
  this API produces.
- Validation order is fixed (see data-model.md "Validation, in order") so the first
  applicable `ActionError` is always the one reported — deterministic error reporting,
  not just deterministic success.

## Elemental multiplier table (R4, normative values)

| Stance | Multiplier |
|---|---|
| NEUTRAL | 1.0 |
| WEAKNESS | 2.0 |
| RESISTANCE | 0.5 |
| IMMUNITY | 0.0 |
| ABSORPTION | −1.0 |

Applied to the signed health delta (negative = damage, positive = heal), rounded with
round-half-away-from-zero to the nearest `Int` (R5). Only applied when
`action.element != null`; otherwise the raw signed delta is used unchanged.
