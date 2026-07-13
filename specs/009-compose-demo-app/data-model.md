# Phase 1 Data Model: Compose Demo App

All entities are additive and live in `demo-app`'s commonMain. Types published by
specs 001-008 (`BattleState`, `AtbScheduleState`, `StatusEffectState`,
`LimitGaugeState`, `ResourceState`, `EventLog`, `BattleOutcome`, `ContentPack`,
`DemoActionDefinition`, `CombatantId`, …) are reused by reference, never redefined.

## `DemoSession` (`BattleController.kt`)

The controller's internal session bundle — the same composition spec 008's
`BattleSession` established, re-declared here because `demo-console` is a JVM-only
application module this multiplatform module cannot depend on:

| Field | Type | Notes |
|---|---|---|
| `battle` | `BattleState` | always the RAW state (health source of truth), never the effective-wrapped view |
| `schedule` | `AtbScheduleState` | |
| `effects` | `StatusEffectState` | |
| `gauges` | `LimitGaugeState` | |
| `resources` | `ResourceState` | |
| `log` | `EventLog` | |

Factory: `newDemoSession(): DemoSession` — `buildDemoBattleState()` +
`initialSchedule` + empty effects/gauges/log + `initialResourceState()`.

## `BattleUiState` (`BattleUiState.kt`)

The single immutable value the UI observes; replaced wholesale on every change.

| Field | Type | Notes |
|---|---|---|
| `participants` | `List<ParticipantView>` | order = battle participant order (stable) |
| `logLines` | `List<String>` | full rendered log so far, append-only across states |
| `phase` | `BattlePhase` | see below |
| `lastRejection` | `String?` | plain-language reason of the most recent rejected submission; cleared on the next accepted one |

### `ParticipantView`

| Field | Type | Notes |
|---|---|---|
| `id` | `CombatantId` | |
| `name` | `String` | display name |
| `currentHp` / `maxHp` | `Int` | from the RAW battle state |
| `isPlayerSide` | `Boolean` | drives panel placement |
| `isDefeated` | `Boolean` | |

### `BattlePhase` (sealed)

- `AwaitingPlayerAction(actorId: CombatantId, actorName: String, actions: List<OfferedAction>)`
- `BattleOver(victory: Boolean)`

### `OfferedAction` (sealed) — what a button represents

- `BasicAttack(definition: DemoActionDefinition)`
- `UseSkill(definition: DemoActionDefinition)` — one per skill the actor can use
  (capability-gated, same as the engine's own gate)
- `UseLimitBreak(limitBreakId: LimitBreakId)` — present iff offerable (research R5)
- `UseSummon(summonId: SummonId)` — present iff offerable (research R5)

Every `OfferedAction` carries `targets: List<CombatantId>` — the currently valid
target choices for its targeting shape (living opposing combatants for
`SINGLE_ENEMY`, etc.), so the target picker never offers a selection the engine
would structurally reject; the resolver remains the actual gate regardless.

## `PlayerChoice` (`BattleUiState.kt`)

What `submit` accepts: `PlayerChoice(action: OfferedAction, targetId: CombatantId)`.

## `BattleController` (`BattleController.kt`)

| Member | Signature | Contract |
|---|---|---|
| ctor | `BattleController(content: ContentPack, session: DemoSession = newDemoSession())` | advances leading automatic turns; `uiState` immediately meaningful |
| `uiState` | `var uiState: BattleUiState; private set` | replaced, never mutated |
| `submit` | `fun submit(choice: PlayerChoice)` | rejection → only `lastRejection` changes (turn kept); resolution → full spec 008 bookkeeping, then auto-turns advance until human turn / battle over |

State transitions: `AwaitingPlayerAction --submit(valid)--> [bookkeeping + auto
turns] --> AwaitingPlayerAction | BattleOver`;
`AwaitingPlayerAction --submit(invalid)--> AwaitingPlayerAction (lastRejection set)`.
`BattleOver` is terminal — `submit` in that phase is a no-op.

## `EventText` (`EventText.kt`)

`fun BattleEvent.toDisplayText(battle: BattleState): String` and
`fun ActionError.toDisplayText(battle: BattleState): String` — total over the whole
sealed hierarchies (incl. spec 006's `GaugeFull`/`LimitBreakUsed`/`SummonCast`),
same wording conventions as spec 008's console rendering; names resolved from the
battle state, never raw ids. Plus rejection wording for
`LimitBreakError`/`SummonError` (the two resolver-specific error types).

## Compose layer (`ui/BattleScreen.kt`) — no new data types

`@Composable fun BattleScreen(controller: BattleController)` holds
`mutableStateOf(controller.uiState)` + a local `selectedAction: OfferedAction?`
(the target-picker step; setting it back to `null` is the cancel affordance — a
purely local UI concern, which is why it is NOT in `BattleUiState`).
