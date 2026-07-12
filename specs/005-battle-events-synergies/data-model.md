# Data Model: Battle Events & Synergies

**Feature**: 005-battle-events-synergies | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1–R6)

Extends `core` additively: `Combatant`, `BattleState`, `CombatantId`, `resolveAction`,
`TurnScheduler`, `StatusEffectState`/`tickStatusEffects` (specs 001–004) are consumed
exactly as published (spec FR-011); nothing here changes them.

## Event layer (new to this feature)

### BattleEvent (R2)
Sealed, seven variants — the six FR-001 occurrence kinds plus `SynergyTriggered`
(FR-009). Never freeform text (FR-002, constitution Principle IV).

| Variant | Fields | Notes |
|---|---|---|
| `DamageDealt` | `actorId: CombatantId?`, `targetId: CombatantId`, `amount: Int`, `resultingHealth: Int` | `actorId` is `null` for damage-over-time ticks and synergy bonuses (R2, R4) — both are genuinely actor-less |
| `HealingApplied` | `actorId: CombatantId?`, `targetId: CombatantId`, `amount: Int`, `resultingHealth: Int` | same actor-less allowance as `DamageDealt` |
| `StatusEffectApplied` | `combatantId: CombatantId`, `effectId: StatusEffectId` | fires on every `applyStatusEffect` call, including a refresh (Assumptions) |
| `StatusEffectExpired` | `combatantId: CombatantId`, `effectId: StatusEffectId` | fires when a tick's post-decrement `remainingDuration` reaches `0` |
| `CombatantDefeated` | `combatantId: CombatantId` | fires exactly once per combatant, the first time `resultingHealth` reaches `0` |
| `TurnGranted` | `combatantId: CombatantId` | derived from `ScheduleResult.Ready`; `ScheduleResult.NoOneReady` produces no event |
| `SynergyTriggered` | `synergyId: SynergyId`, `firstActorId: CombatantId`, `secondActorId: CombatantId`, `targetId: CombatantId` | `firstActorId`/`secondActorId` are the two distinct combatants whose qualifying actions satisfied the synergy (FR-008), in the order they occurred |

### LoggedEvent / EventLog (R2)

| Type | Fields | Rules |
|---|---|---|
| `LoggedEvent` | `turnIndex: Int`, `event: BattleEvent` | `turnIndex` = count of `TurnGranted` events already in the log at the moment this event was appended |
| `EventLog` | `entries: List<LoggedEvent>` (default empty) | append-only (FR-003); `turnsGranted: Int` = `entries.count { it.event is TurnGranted }` |

`EventLog.append(newEvents: List<BattleEvent>): EventLog` stamps each event in
`newEvents`, in order, with the running `turnsGranted` count, incrementing that running
count immediately after stamping a `TurnGranted` event (so a batch containing one is
correctly split across two turn indices). Returns a new `EventLog` — the input is
never mutated (US1 scenario 7: nothing already recorded is lost or reordered).

## Synergy catalog layer (data-driven, independent of spec 001's Catalog — same
discipline as spec 004's R4)

### SynergyBonus (R4)
Sealed, one variant for this feature's scope (spec.md's own example, Assumptions
"v1: exactly two required capabilities"):

| Variant | Fields |
|---|---|
| `BonusDamage` | `amount: Int` |

### SynergyDefinition
| Field | Type | Rules |
|---|---|---|
| id | SynergyId | unique within a `SynergyCatalog` |
| firstSkillId | SkillId | one of the two required qualifying capabilities (FR-004) |
| secondSkillId | SkillId | the other; must differ from `firstSkillId` |
| window | Int | must be `> 0` at validation time; compared inclusively against `turnIndex` difference (R5) |
| bonus | SynergyBonus | applied via `applySynergyBonus`, never a new health-mutation pathway (FR-006) |

Order between `firstSkillId`/`secondSkillId` carries no meaning at detection time —
either capability may be satisfied first (R3); the two field names only distinguish
the pair, matching how spec 004's `EffectKind.StatModifier` names its two positional
fields without implying an order of application.

### SynergyCatalog / validation
`SynergyCatalog(synergies: List<SynergyDefinition>)` →
`validateSynergyCatalog(catalog): SynergyCatalogResult` (`Valid` XOR
`Invalid(errors: List<SynergyCatalogError>)`, accumulate-all, mirroring spec 001's
`CatalogResult`/spec 004's `StatusEffectCatalogResult` convention):

| Error | Condition |
|---|---|
| `DuplicateId` | two `SynergyDefinition`s share an `id` |
| `SameSkill` | `firstSkillId == secondSkillId` on one definition (FR-004: two *distinct* capabilities) |
| `NonPositiveWindow` | `window <= 0` |

## Detection result (R3)

### SynergyTrigger
| Field | Type | Notes |
|---|---|---|
| definition | SynergyDefinition | the synergy that fired |
| firstActorId | CombatantId | the combatant whose qualifying event was found *earlier* in the log |
| secondActorId | CombatantId | the combatant whose qualifying event was the newly-appended one being scanned |
| targetId | CombatantId | the shared target both qualifying events affected |

`detectSynergyTriggers(newEvents, log, battle, catalog): List<SynergyTrigger>` — see
research R3 for the nearest-prior-match algorithm. Zero synergies in `catalog` always
returns an empty list (Edge Cases: User Story 1 has no dependency on this).

## Operations

### eventsFromResolution (R1, R6)
`(oldState: BattleState, action: CombatAction, result: ActionResolutionResult) -> List<BattleEvent>`

`Rejected` results produce no events (nothing happened). `Resolved` results produce,
per `ResolutionOutcome`: a `DamageDealt` or `HealingApplied` event (chosen by
`action.effectKind`, `actorId = action.actorId`, `amount = abs(appliedDelta)`,
`resultingHealth` copied through) followed by `defeatedEvents(...)` (R6) comparing
against `oldState.find(outcome.targetId)!!.isDefeated`.

### eventsFromTick (R1, R6)
`(oldEffects: StatusEffectState, oldBattle: BattleState, catalog: StatusEffectCatalog, result: StatusTickResult) -> List<BattleEvent>`

Walks `oldEffects.active` in the same map/list order `tickStatusEffects` itself
iterates (verified against spec 004's source). Per `(combatantId, ActiveEffect)`:
if the effect's catalog `kind` is `DamageOverTime`, folds `amountPerTick` against a
running `current` (starting at the pre-tick `HealthTrack.current`, clamped `[0,
maximum]` each step) to produce a `DamageDealt(actorId = null, ...)` event plus
`defeatedEvents(...)`; if the decremented `remainingDuration` reached `0`, also
produces a `StatusEffectExpired` event for that effect.

### eventsFromSchedule (R1)
`(result: ScheduleResult<*>) -> List<BattleEvent>`

`Ready(combatantId, _)` → `listOf(TurnGranted(combatantId))`. `NoOneReady` → empty list.

### eventsFromApply (R1)
`(combatantId: CombatantId, effectId: StatusEffectId) -> List<BattleEvent>`

Always `listOf(StatusEffectApplied(combatantId, effectId))` — a thin wrapper a caller
uses right after calling spec 004's `applyStatusEffect`, unconditionally (a refresh is
still an application, per the Assumptions note on `StatusEffectApplied` above). Kept
as its own tiny function rather than folded into `eventsFromTick` because application
and ticking are two entirely separate spec 004 call sites with no shared inputs.

### applySynergyBonus / eventsFromSynergyBonus (R4, R6)
`applySynergyBonus(battle: BattleState, targetId: CombatantId, bonus: SynergyBonus): BattleState`
applies `BonusDamage.amount` with the same `(current - amount).coerceIn(0, maximum)`
clamp `applyTickDamage` (spec 004) uses. `eventsFromSynergyBonus(oldBattle, targetId,
bonus, newBattle)` produces the resulting `DamageDealt(actorId = null, ...)` event plus
`defeatedEvents(...)`; the caller separately appends the owning `SynergyTriggered`
event (FR-009) — bonus-effect events and the trigger event are always appended
together in the same `append` call so US4's "distinct from the events that caused it"
holds while nothing is ever recorded partially.

### defeatedEvents (R6, shared helper)
`(wasDefeatedBefore: Boolean, resultingHealth: Int, combatantId: CombatantId) -> List<BattleEvent>`

Returns `listOf(CombatantDefeated(combatantId))` when `!wasDefeatedBefore &&
resultingHealth == 0`, else an empty list. Used identically by all three health-
changing derivations above so the "already defeated, don't re-report" boundary is
defined exactly once (spec 004 established the analogous discipline for its own
single health-mutation path; this feature has three, so centralizing matters more).

## Determinism guarantee (spec FR-010/SC-005)

Every operation above is a pure function of its inputs — no RNG, no wall-clock, no
iteration-order ambiguity (`EventLog.entries` is an ordered `List`; `StatusEffectState.active`
iteration order matches spec 004's own already-deterministic map/list construction).
Given the same starting `EventLog`/`BattleState`/catalogs and the same sequence of
resolve/tick/schedule/detect/apply calls, every call produces byte-for-byte identical
results, on both JVM and JS.
