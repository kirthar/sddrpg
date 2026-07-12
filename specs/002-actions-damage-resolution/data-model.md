# Data Model: Actions & Damage Resolution

**Feature**: 002-actions-damage-resolution | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1–R8)

Extends spec 001's model additively: `Combatant`, `ValidatedCatalog`, `StatBlock`,
`Affinity` are consumed exactly as published (spec FR-012), nothing here changes them.

## Battle-time state (new to this feature, R1)

### HealthTrack
| Field | Type | Rules |
|---|---|---|
| current | Int | 0 ≤ current ≤ maximum |
| maximum | Int | captured from `combatant.stats[CoreStats.HP]` at battle start; ≥ 0 |

### BattleCombatant
| Field | Type | Rules |
|---|---|---|
| combatant | Combatant | spec 001 type, unchanged, read-only |
| health | HealthTrack | current starts equal to maximum |

Derived: `isDefeated: Boolean` = `health.current == 0` (spec Assumptions).

### BattleState
| Field | Type | Rules |
|---|---|---|
| participants | List\<BattleCombatant\> | one entry per `Combatant.id`, order stable |

Lookup by `CombatantId`; every mutation described below returns a **new** `BattleState`.

## Action layer (new to this feature)

### EffectKind
Enum: `DAMAGE`, `HEAL` (spec Key Entities "Action"; drives the sign of the health
delta before elemental adjustment, R4).

### TargetingShape (FR-007, R6)
Enum: `SINGLE_ALLY`, `SINGLE_ENEMY`, `SELF`, `ALL_ALLIES`, `ALL_ENEMIES`, `ALL`.

### DamageFormula (FR-004, R3)
Sealed interface, `rawMagnitude(actorStats, targetStats): Int`, always ≥ 0:
- `Physical(power: Int)`: `max(0, power + actor[ATTACK] - target[DEFENSE])`
- `Magical(power: Int)`: `max(0, power + actor[MAGIC] - target[RESISTANCE])`
- `Fixed(amount: Int)`: `amount` (ignores stats; ITEM/DEFEND use, R7)

### CombatAction (FR-001, R2)
| Field | Type | Rules |
|---|---|---|
| actorId | CombatantId | must resolve to a non-defeated participant (FR-003) |
| command | CommandKind | spec 001 enum; gates capability (FR-002) |
| skillId | SkillId? | required capability check when non-null (FR-002) |
| effectKind | EffectKind | DAMAGE or HEAL |
| element | ElementId? | null ⇒ elemental adjustment skipped entirely (FR-005) |
| formula | DamageFormula | selected per action, substitutable (FR-004) |
| targeting | TargetingShape | declared shape the target-id selection must match (FR-007) |

## Resolution (FR-002, FR-003, FR-008, FR-009, FR-010, FR-011)

### Validation, in order
1. Actor exists in `BattleState` and is not defeated (FR-003) → else `ActionError.DefeatedActor`.
2. Actor's `capabilities.commands` contains `action.command` (FR-002) → else `ActionError.MissingCommand`.
3. If `action.skillId != null`, actor's `capabilities.skills` contains it (FR-002) → else `ActionError.MissingSkill`.
4. Every id in the submitted target selection resolves to a participant in the `BattleState` (FR-008) → else `ActionError.UnknownTarget`.
5. The target-id selection matches `action.targeting`'s cardinality/scope relative to the actor's allegiance (FR-008, R6) → else `ActionError.TargetShapeMismatch`.

### Resolution, once validation passes
1. Narrow the validated target set by excluding already-defeated participants (FR-009) — may result in an empty set, which is not an error (edge case).
2. For each remaining target: compute `rawMagnitude` (R3), `baseDelta` (sign from `effectKind`), apply elemental adjustment if `element != null` (R4, R5) → `adjustedDelta`.
3. Apply `adjustedDelta` to the target's `HealthTrack.current`, clamped to `[0, maximum]` (FR-011).
4. Record one `ResolutionOutcome` per affected target.
5. Return the new `BattleState` (only affected participants' `HealthTrack`s differ) and the outcome list.

### ResolutionOutcome (FR-010)
| Field | Type | Rules |
|---|---|---|
| targetId | CombatantId | the affected participant |
| appliedDelta | Int | signed, post-adjustment, post-clamp health change |
| affinityApplied | Affinity? | the stance that drove adjustment, null if no element declared |
| resultingHealth | Int | target's `HealthTrack.current` after this outcome |

### ActionError (FR-002, FR-003, FR-008)
Sealed: `DefeatedActor(actorId)`, `MissingCommand(actorId, command)`,
`MissingSkill(actorId, skillId)`, `UnknownTarget(targetId)`,
`TargetShapeMismatch(shape, reason)`. Each names the offending id/rule, mirroring spec
001's `CatalogError` naming convention (R6, SC-002 precedent).

### ActionResolutionResult
`sealed interface`: `Resolved(newState: BattleState, outcomes: List<ResolutionOutcome>)`
XOR `Rejected(error: ActionError)` — an action never partially resolves. On `Rejected`,
the caller's `BattleState` is never touched: validation (steps 1–5 above) runs entirely
before any `HealthTrack` is read for mutation (spec SC-002).
