# Contract: Console Demo Public Surface

This feature is an application, not a library other features consume — but the two
new `core` additions and `content`'s demo action mapping are genuinely reusable
public API within this project, and `demo-console`'s loop is structured so its own
tests are the contract for "playable without a real console." All signatures below
are the ones tasks.md's test-first tasks must target.

## `core` additions

### `core.battle.BattleOutcome` / `BattleState.outcome()`

```kotlin
sealed interface BattleOutcome {
    data object Ongoing : BattleOutcome
    data object Victory : BattleOutcome
    data object Defeat : BattleOutcome
}

fun BattleState.outcome(): BattleOutcome
```

**Contract**:
- Pure, total, deterministic: same `BattleState` always returns an equal result.
- `Ongoing` iff at least one `PLAYER`/`TEMPORARY_ALLY`-allegiance... — more precisely,
  at least one `Allegiance.PLAYER` participant is not defeated AND at least one
  `Allegiance.OPPONENT` participant is not defeated.
- `Defeat` iff every `Allegiance.PLAYER` participant is defeated (checked first —
  simultaneous-defeat tie-break resolves to `Defeat`).
- `Victory` iff every `Allegiance.OPPONENT` participant is defeated and the above
  `Defeat` condition does not also hold.
- Never mutates or requires any parameter beyond the receiver.

### `core.ai.AutomaticActionRule`

```kotlin
fun selectAutomaticCommand(actor: Combatant, preferenceOrder: List<CommandKind>): CommandKind?
fun selectAutomaticSkill(actor: Combatant): SkillId?
fun selectAutomaticTarget(battle: BattleState, actorId: CombatantId): CombatantId?
```

**Contract**:
- `selectAutomaticCommand`: returns the first element of `preferenceOrder` that is
  also a member of `actor.capabilities.commands`, or `null` if none match (the "no
  valid action available" edge case). Pure; does not consult `battle` at all (a
  command's usability here is capability-gating only, not resource/target
  availability — matching `resolveAction`'s own layered gating).
- `selectAutomaticSkill`: returns the lexicographically-least `SkillId.value` among
  `actor.capabilities.skills`, or `null` if empty. Deterministic regardless of `Set`
  iteration order (research.md R3).
- `selectAutomaticTarget`: returns the id of the living opposing-allegiance
  participant (relative to `actorId`'s own allegiance) with the lowest
  `HealthTrack.current`; ties broken by earliest position in
  `battle.participants`. Returns `null` if `actorId` is unknown to `battle` or no
  living opposing participant exists.
- All three: same inputs always produce an equal output (constitution Principle I).

## `content` additions

### `content.demo.DemoActionDefinition` / `DEMO_ACTIONS`

```kotlin
data class DemoActionDefinition(
    val command: CommandKind,
    val skillId: SkillId?,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

val DEMO_ACTIONS: List<DemoActionDefinition>
```

**Contract**:
- Exactly one entry per distinct `(command, skillId)` pair.
- Every `skillId` referenced is a member of `DEMO_CONTENT_JSON`'s own
  `knownSkills`/class `skills` sets (cross-checked by a test, not a runtime
  validator — this list is authored Kotlin, not externally loaded).
- A lookup helper is provided for callers:
  `fun DEMO_ACTIONS.find(command: CommandKind, skillId: SkillId?): DemoActionDefinition?`

### `content.demo.buildDemoBattleState`

```kotlin
fun buildDemoBattleState(): BattleState
```

**Contract**:
- Deterministic: repeated calls return structurally-equal `BattleState`s (participant
  order, ids, stats, capabilities all identical every call).
- Every `ENEMY`-kind participant's `capabilities.commands` contains
  `CommandKind.ATTACK`; additionally contains `CommandKind.SKILL` iff that enemy's
  `capabilities.skills` is non-empty (research.md R5). Every non-enemy participant's
  `capabilities` is byte-identical to what `RosterBuilder` alone would have produced
  (the workaround touches enemies only).
- Loads content via `loadContentPack(DEMO_CONTENT_JSON)` (spec 007, unmodified) and
  fails loudly (throws) if that load is ever `Invalid` — the shipped demo content is
  asserted valid by spec 007's own `DemoContentTest`, so this is a defensive
  assertion, not a new validation rule.

## `demo-console` additions

### `demo-console.BattleLoop.runBattleLoop`

```kotlin
tailrec fun runBattleLoop(
    session: BattleSession,
    content: ContentPack,
    readInput: () -> String?,
    emit: (String) -> Unit,
): BattleOutcome
```

**Contract** (this is FR-007's testability requirement, made concrete):
- Never calls `readLine()`/`println()`/any real console API directly — all I/O flows
  through `readInput`/`emit`.
- On a human-controlled combatant's turn: calls `emit` with a prompt, calls
  `readInput` for a submission; on an unparseable or `resolveAction`-rejected
  submission, calls `emit` with the rejection reason in plain language and calls
  `readInput` again — never crashes, never silently advances the turn (FR-002).
- If `readInput` returns `null` (EOF) while awaiting a human submission, the loop
  ends immediately without crashing, returning the current `BattleState.outcome()`
  (which may be `Ongoing` — an incomplete-input ending is not itself a win/loss,
  matching the edge case "ends gracefully rather than crashing or hanging").
- On an automatically-controlled combatant's turn: never calls `readInput`; if
  `selectAutomaticCommand` returns `null`, the turn is skipped (scheduler's
  `markSpent` is still called) without emitting a rejection and without ending the
  battle.
- Every `BattleEvent` produced during a turn is passed through `emit` (via
  `ConsoleRendering`) before the next turn is requested from the scheduler (US2).
- Returns as soon as `BattleState.outcome()` is no longer `Ongoing`; the returned
  value is exactly that outcome.
- Same starting `session`/`content` and same sequence of values from `readInput`
  always produce the same sequence of `emit` calls and the same returned
  `BattleOutcome` (FR-010/SC-002) — verified by a determinism test that replays an
  identical scripted input list twice.

### `demo-console.ConsoleRendering`

```kotlin
fun BattleEvent.toDisplayText(battle: BattleState): String
```

**Contract**:
- Total: every `BattleEvent` subtype has a rendering (US2 acceptance scenarios 1-4);
  no subtype falls through to a generic/raw `toString()`.
- Never returns raw internal data (ids, enum names) as the primary content — resolves
  `CombatantId` to `displayName` via `battle.find(id)?.combatant?.displayName`.
- Pure: same `(event, battle)` always returns an equal string.
