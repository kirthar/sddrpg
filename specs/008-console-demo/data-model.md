# Phase 1 Data Model: Console Demo

All entities below are additive: no existing spec 001-007 type is modified. Types
already published by specs 001-007 (`BattleState`, `Combatant`, `CombatantId`,
`CommandKind`, `SkillId`, `DamageFormula`, `TargetingShape`, `EffectKind`,
`ElementId`, `AtbScheduleState`, `StatusEffectState`, `LimitGaugeState`,
`ResourceState`, `EventLog`, `BattleEvent`, `ContentPack`) are reused by reference,
not redefined here.

## New `core` types

### `BattleOutcome` (`core/.../battle/BattleOutcome.kt`)

```kotlin
sealed interface BattleOutcome {
    data object Ongoing : BattleOutcome
    data object Victory : BattleOutcome
    data object Defeat : BattleOutcome
}
```

- Derived, never stored: `fun BattleState.outcome(): BattleOutcome`.
- `Defeat` is checked before `Victory` (simultaneous-defeat tie-break, spec.md
  Assumptions).

### Automatic action rule functions (`core/.../ai/AutomaticActionRule.kt`)

No new data types — three pure functions over existing types (see research.md R3):

- `fun selectAutomaticCommand(actor: Combatant, preferenceOrder: List<CommandKind>): CommandKind?`
- `fun selectAutomaticSkill(actor: Combatant): SkillId?`
- `fun selectAutomaticTarget(battle: BattleState, actorId: CombatantId): CombatantId?`

## New `content` types

### `DemoActionDefinition` (`content/.../demo/DemoActions.kt`)

| Field | Type | Notes |
|---|---|---|
| `command` | `CommandKind` | which submitted command this definition describes |
| `skillId` | `SkillId?` | `null` for the basic ATTACK command; identifies which skill otherwise |
| `effectKind` | `EffectKind` | DAMAGE or HEAL |
| `formula` | `DamageFormula` | reused from spec 002, unmodified |
| `targeting` | `TargetingShape` | reused from spec 002, unmodified |
| `element` | `ElementId?` | `null` for non-elemental actions |

Invariant: at most one `DemoActionDefinition` per distinct `(command, skillId)` pair
in `DEMO_ACTIONS` (mirrors spec 001's Catalog's own "one definition per id"
convention) — validated by a test, not a runtime check, since this is authored
Kotlin data, not externally loaded content (research.md R1).

`DEMO_ACTIONS: List<DemoActionDefinition>` — exactly 3 entries for the shipped demo
content: basic ATTACK, `cleave` (SKILL), `fira` (MAGIC). See research.md R1 for the
concrete values.

### Enemy command-grant wrapper (`content/.../demo/DemoRoster.kt`)

No new public data type — a `private` `Combatant`-delegating wrapper (research.md
R5) plus a builder function:

- `fun buildDemoBattleState(): BattleState` — builds the shipped demo roster via
  `RosterBuilder` (unmodified spec 001 API) against `loadContentPack(DEMO_CONTENT_JSON)`'s
  catalog, wraps every `ENEMY`-kind combatant to grant it `CommandKind.ATTACK`
  (always) and `CommandKind.SKILL` (iff it has any declared skill), then returns
  `.toBattleState()`.

## New `demo-console` types

### `BattleSession` (`demo-console/.../BattleSession.kt`)

The spec's own "Battle Session" key entity, made concrete:

```kotlin
data class BattleSession(
    val battle: BattleState,
    val schedule: AtbScheduleState,
    val effects: StatusEffectState,
    val gauges: LimitGaugeState,
    val resources: ResourceState,
    val log: EventLog,
)
```

- Constructed once at battle start (`battle` from `buildDemoBattleState()`,
  `schedule` from `ActiveTimeBattleScheduler.initialSchedule(battle)`, `effects`/
  `gauges` empty, `resources` from `battle.initialResourceState()`, `log` empty).
- Every turn produces a *new* `BattleSession` (immutable, matches constitution
  Principle I) — `BattleLoop.kt` never mutates one in place.

### `BattleLoop` (`demo-console/.../BattleLoop.kt`)

No new public data type beyond `BattleSession` — the injectable driver function
itself (research.md R2):

```kotlin
tailrec fun runBattleLoop(
    session: BattleSession,
    content: ContentPack,
    readInput: () -> String?,
    emit: (String) -> Unit,
): BattleOutcome
```

Internally, per iteration: ask the `TurnScheduler` who's next → if human, prompt +
parse + validate via `resolveAction`'s own rejection (reprompt on failure, per
FR-002) → if automatic, use `selectAutomaticCommand`/`selectAutomaticSkill`/
`selectAutomaticTarget` to build a `CombatAction` from a `DEMO_ACTIONS` lookup (skip
the turn if no usable command exists, per the edge case) → resolve → derive events →
detect synergies → charge gauges → tick status effects → render every new event via
`ConsoleRendering` → check `BattleState.outcome()` → recurse or return.

### `ConsoleRendering` (`demo-console/.../ConsoleRendering.kt`)

- `fun BattleEvent.toDisplayText(battle: BattleState): String` — one line of plain
  language per event kind (US2). Takes `battle` (the state the event occurred
  against) only to resolve combatant display names from `CombatantId`, since
  `BattleEvent` itself intentionally carries only ids, never names (constitution
  Principle IV: `core` never formats text).

## State transitions

`BattleSession` moves strictly forward, one `resolveAction`+bookkeeping cycle per
granted turn, until `BattleState.outcome()` is no longer `Ongoing`. No transition
skips a step in research.md R6's ordering; no transition is reachable that leaves
`EventLog` inconsistent with `BattleState` (every event append happens against the
exact state transition that produced it, matching specs 004-006's own established
event-derivation discipline).
