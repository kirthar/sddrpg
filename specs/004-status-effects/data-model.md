# Data Model: Status Effects

**Feature**: 004-status-effects | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1–R7)

Extends `core` additively: `Combatant`, `BattleState`, `CombatantId`, `resolveAction`,
`TurnScheduler`/`ActiveTimeBattleScheduler` (specs 001–003) are consumed exactly as
published (spec FR-011); nothing here changes them, and this feature's own catalog is
independent of spec 001's `Catalog` (R4).

## Catalog layer (data-driven, independent of spec 001's Catalog — R4)

### EffectKind (R2)
Sealed: `StatModifier(statId: StatId, delta: Int)` | `Incapacitate` |
`DamageOverTime(amountPerTick: Int)`.

### StatusEffectDefinition
| Field | Type | Rules |
|---|---|---|
| id | StatusEffectId | unique within a `StatusEffectCatalog` |
| displayName | String | opaque to the engine (Principle IV) |
| kind | EffectKind | determines behavior on application and on tick |
| duration | Int | must be > 0 at validation time (edge case, spec.md) |

### StatusEffectCatalog / validation
`StatusEffectCatalog(effects: List<StatusEffectDefinition>)` →
`validateStatusEffectCatalog(catalog): StatusEffectCatalogResult` (`Valid` XOR
`Invalid(errors: List<StatusEffectCatalogError>)`, accumulate-all, mirroring spec
001's `CatalogResult`/`CatalogError` convention): checks duplicate ids
(`DuplicateId`) and non-positive duration (`NonPositiveDuration`).

## Battle-time state layer (new to this feature)

### ActiveEffect
| Field | Type | Rules |
|---|---|---|
| effectId | StatusEffectId | must resolve in the `StatusEffectCatalog` used to apply/tick it |
| remainingDuration | Int | ≥ 0; reaching 0 on a tick removes the instance (R7) |

### StatusEffectState
| Field | Type | Rules |
|---|---|---|
| active | Map\<CombatantId, List\<ActiveEffect\>\> | absent key ⇒ no active effects for that combatant; at most one `ActiveEffect` per distinct `effectId` per combatant (FR-008: reapplication replaces, never appends) |

## Derivation: effective BattleState (R1, R3, R5)

### EffectiveCombatant (internal to this feature)
`private data class EffectiveCombatant(base: Combatant, override val stats: StatBlock) : Combatant by base`
— every member except `stats` forwards to `base` unchanged.

### deriveEffectiveBattleState
`(battle: BattleState, effects: StatusEffectState, catalog: StatusEffectCatalog) -> BattleState`

For each `BattleCombatant` in `battle.participants`:
1. Look up its active effects (empty list if none — participant is left byte-identical).
2. Group `StatModifier`-kind effects by `statId`, sum deltas per stat.
3. If any `Incapacitate`-kind effect is active, add an implicit `SPEED -> -∞`-equivalent
   override: effective Speed is forced to `0` regardless of any other Speed modifier
   (R5) — computed as a final clamp, not a summed delta, so it can't be offset by a
   simultaneous Speed-increasing modifier.
4. If step 2 or 3 produced any change, compute the new `StatBlock` (base value + summed
   delta, floored at 0, per-stat; Speed forced to 0 if incapacitated) and replace
   `battleCombatant.combatant` with an `EffectiveCombatant` wrapping the original.
5. Otherwise, leave the participant unchanged (same object, no wrapping).

Returns a new `BattleState` with the same participant order and `HealthTrack`s;
only wrapped combatants' `stats` differ from the input.

## Operations (R6, R7)

### applyStatusEffect
`(state: StatusEffectState, combatantId: CombatantId, effectId: StatusEffectId, catalog: StatusEffectCatalog) -> StatusEffectState`

Looks up `effectId`'s `duration` in `catalog`; if the combatant already has an
`ActiveEffect` for that `effectId`, its `remainingDuration` is reset to `duration`
(replace, not append — FR-008); otherwise a new `ActiveEffect(effectId, duration)` is
added to that combatant's list.

### removeStatusEffect
`(state: StatusEffectState, combatantId: CombatantId, effectId: StatusEffectId) -> StatusEffectState`

Removes the matching `ActiveEffect` if present; idempotent no-op if absent (edge case,
spec.md).

### tickStatusEffects
`(effects: StatusEffectState, battle: BattleState, catalog: StatusEffectCatalog) -> StatusTickResult`

Per the clarified FR-007 rule (one call = one tick, applied to every active effect on
every combatant, regardless of whose turn triggered it):
1. For every `(combatantId, ActiveEffect)` pair: decrement `remainingDuration` by 1.
2. For every effect whose definition `kind` is `DamageOverTime` and whose
   `remainingDuration` (post-decrement) is `>= 0` (i.e., not yet removed by step 3):
   apply `amountPerTick` damage to that combatant's `HealthTrack`, clamped to
   `[0, maximum]` (R6) — accumulating into a new `BattleState`.
3. Remove every `ActiveEffect` whose `remainingDuration` has reached `0`.
4. Return `StatusTickResult(newBattle, newEffects)`.

### StatusTickResult
| Field | Type |
|---|---|
| newBattle | BattleState — unchanged except `HealthTrack`s touched by damage-over-time |
| newEffects | StatusEffectState — durations decremented, expired entries removed |

## Determinism guarantee (spec FR-010/SC-007)

Every operation above is a pure integer-arithmetic function of its inputs; no
`Double`, no RNG, no wall-clock. Given the same starting `StatusEffectState` +
`BattleState` and the same sequence of apply/remove/tick calls, every call produces
byte-for-byte identical results, on both JVM and JS.
