# Contract: Compose Demo App Surface

`demo-app` is an application, not a library — its "contract" is (1) the
controller/rendering surface its own tests target, and (2) the build products the
user consumes. All signatures below are what tasks.md's test-first tasks target.

## `BattleController`

```kotlin
class BattleController(
    content: ContentPack,
    session: DemoSession = newDemoSession(),
) {
    var uiState: BattleUiState
        private set
    fun submit(choice: PlayerChoice)
}
```

**Contract**:
- **Construction**: advances every leading automatic turn; afterwards `uiState.phase`
  is `AwaitingPlayerAction` for the first human-controlled combatant the scheduler
  grants, or `BattleOver` if the battle somehow ends first. All occurrences from
  those turns are already in `uiState.logLines`.
- **submit / accepted**: resolves via the published resolver for the chosen action
  kind (`resolveAction` / `resolveLimitBreak` / `resolveSummon`), applies spec 008's
  full per-turn bookkeeping order (research R4), advances consecutive automatic
  turns, and replaces `uiState`. `lastRejection` is `null` in the new state.
  `logLines` strictly extends the previous state's list (append-only).
- **submit / rejected**: only `lastRejection` changes, phase and actor unchanged —
  the turn is never consumed (FR-002/SC-004). The rejection text is the same
  plain-language rendering the log uses for errors.
- **submit / BattleOver**: no-op (state unchanged).
- **Offerability** (FR-006): `AwaitingPlayerAction.actions` contains
  `UseLimitBreak`/`UseSummon` iff the engine would accept them at that moment
  (research R5); every action's `targets` list contains only structurally-valid
  choices for its targeting shape.
- **Health snapshots**: `participants` always reflects the RAW battle state after
  the last completed bookkeeping — always consistent with the last logged
  health-changing occurrence (US2 scenario 4).
- **Determinism** (FR-009/SC-002): two controllers built from equal sessions and
  fed the same `submit` sequence produce equal `uiState` values at every step.
- **Purity of dependencies**: no coroutines, no Flow, no Compose, no platform API —
  plain Kotlin over published `core`/`content` types only (FR-007).
- **Termination**: auto-turn advancement always terminates — every granted
  automatic turn either resolves an action, or is skipped via `markSpent`; battle
  end is checked between turns (`BattleState.outcome()`), and an
  all-automatic-stall (no one can act) exits as `BattleOver` with the current
  outcome rather than looping forever.

## `EventText`

```kotlin
fun BattleEvent.toDisplayText(battle: BattleState): String
fun ActionError.toDisplayText(battle: BattleState): String
fun LimitBreakError.toDisplayText(battle: BattleState): String
fun SummonError.toDisplayText(battle: BattleState): String
```

**Contract**: total over each sealed hierarchy (no fall-through `toString`), names
resolved via `battle.find(id)?.combatant?.displayName`, pure.

## `BattleScreen`

```kotlin
@Composable fun BattleScreen(controller: BattleController)
```

**Contract** (verified by the controller tests plus compilation — no UI test
harness, per research R8): renders `participants` as two side-grouped panels with
name + HP bar; renders `logLines` in a scrolling list following the newest line;
shows action buttons only in `AwaitingPlayerAction`; target picking is a second
step with a cancel affordance that never consumes the turn (local UI state only);
shows `lastRejection` when present; shows a full-screen Victory/Defeat overlay in
`BattleOver`.

## Build products (the user-facing contract)

| Product | Command | Output |
|---|---|---|
| Android APK (debug-signed, installable) | `:demo-app:assembleDebug` | `demo-app/build/outputs/apk/debug/demo-app-debug.apk` |
| Web static site | `:demo-app:wasmJsBrowserDistribution` | `demo-app/build/dist/wasmJs/productionExecutable/` |
| Desktop dev app | `:demo-app:run` | window on the dev machine |
| Test suite | `:demo-app:desktopTest` | kotest suite, green required |

GitHub Pages: `.github/workflows/deploy-pages.yml` publishes the web static site;
requires the owner to enable Pages (Source = GitHub Actions) once, and runs from
the default branch (research R7).
