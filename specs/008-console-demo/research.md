# Phase 0 Research: Console Demo

## R1: Where do the demo's action definitions (formula/targeting/element) live?

**Decision**: A small, additive mapping in `content/src/commonMain/.../demo/DemoActions.kt`:

```kotlin
data class DemoActionDefinition(
    val command: CommandKind,
    val skillId: SkillId?,       // null for the basic ATTACK command
    val effectKind: EffectKind,  // core.action.EffectKind (DAMAGE/HEAL)
    val formula: DamageFormula,  // core.action.DamageFormula (reused, not redefined)
    val targeting: TargetingShape,
    val element: ElementId? = null,
)

val DEMO_ACTIONS: List<DemoActionDefinition> = listOf(
    DemoActionDefinition(CommandKind.ATTACK, skillId = null, EffectKind.DAMAGE, DamageFormula.Physical(10), TargetingShape.SINGLE_ENEMY),
    DemoActionDefinition(CommandKind.SKILL, SkillId("cleave"), EffectKind.DAMAGE, DamageFormula.Physical(15), TargetingShape.SINGLE_ENEMY),
    DemoActionDefinition(CommandKind.MAGIC, SkillId("fira"), EffectKind.DAMAGE, DamageFormula.Magical(20), TargetingShape.SINGLE_ENEMY, ElementId("fire")),
)
```

**Rationale**: Reuses spec 002's own `DamageFormula`/`TargetingShape`/`EffectKind`
types directly (no DTO layer needed — this is authored Kotlin data, not deserialized
JSON, so there is no serialization boundary to cross, unlike spec 007's catalogs).
Lives in `content` because it describes *what the shipped demo content's actions do*,
which is exactly the same kind of fact spec 007 already put in `content` (limit
break/summon formulas, status effect definitions). Keeping it out of `demo-console`
keeps that module free of game-content knowledge (constitution Principle II) — a
different demo content pack would ship a different `DEMO_ACTIONS` list without
touching any `demo-console` file.

**Alternatives considered**:
- *New generic engine-wide "action catalog" type in `core`*: rejected — spec.md's
  FR-006 explicitly rules this out (no speculative general-purpose catalog for a
  scope this small; constitution Principle V).
- *Inline in `demo-console`*: rejected — would leak content knowledge into the
  presentation module, breaking the separation every other spec has preserved.
- *Retrofit `@Serializable` onto a JSON-authored action catalog, loaded via
  `content`'s existing DTO pipeline (spec 007)*: rejected as overkill — the demo
  content's basic attack/skills are a fixed, tiny, hand-authored set; a full
  load-from-text pipeline for 3 entries adds machinery with no consumer need
  (Principle V, no speculative implementation).

## R2: How is the battle loop testable apart from real console I/O?

**Decision**: The turn-loop driver in `demo-console` (`BattleLoop.kt`) takes two
function parameters instead of calling `readLine()`/`println()` directly:

```kotlin
fun runBattleLoop(
    session: BattleSession,
    schedule: AtbScheduleState,
    content: ContentPack,
    readInput: () -> String?,   // null signals end-of-input (EOF)
    emit: (String) -> Unit,
): BattleOutcome
```

`main()` wires `readInput = ::readLine` and `emit = ::println`; tests supply a
scripted `Iterator<String>`-backed lambda and a `MutableList<String>`-backed sink,
asserting on captured output and the returned `BattleOutcome` with zero real
terminal interaction.

**Rationale**: The smallest possible seam — two plain function types, no new
interface, no DI framework — matching this project's established preference for the
simplest mechanism that satisfies the requirement (e.g. spec 005's plain
event-log-accumulation over pub/sub). `demo-console` is a JVM `application` module
(`build.gradle.kts` already confirmed), so ordinary kotest JUnit5 execution is
sufficient; nothing platform-specific is needed since it never runs on JS.

**Alternatives considered**:
- *A `ConsoleIO` interface with `readLine()`/`print()` methods*: rejected as
  unnecessary ceremony over two function parameters for a single-implementation
  seam — no polymorphism beyond "real" vs. "scripted" is ever needed.
- *Dependency-inject `System.in`/`System.out` streams directly*: rejected — forces
  every test to construct `BufferedReader`/`PrintStream` wrappers instead of plain
  Kotlin lambdas/lists.

## R3: The deterministic automatic action rule — home and shape

**Decision**: Two small, pure, deterministic functions in a new `core` file,
`core/.../ai/AutomaticActionRule.kt`:

```kotlin
fun selectAutomaticCommand(actor: Combatant, preferenceOrder: List<CommandKind>): CommandKind? =
    preferenceOrder.firstOrNull { it in actor.capabilities.commands }

fun selectAutomaticSkill(actor: Combatant): SkillId? =
    actor.capabilities.skills.minByOrNull { it.value }

fun selectAutomaticTarget(battle: BattleState, actorId: CombatantId): CombatantId? {
    val actor = battle.find(actorId) ?: return null
    return battle.participants
        .asSequence()
        .filter { it.combatant.allegiance != actor.combatant.allegiance && !it.isDefeated }
        .minWithOrNull(compareBy<BattleCombatant> { it.health.current }
            .thenBy { battle.participants.indexOf(it) })
        ?.combatant?.id
}
```

**Rationale**: These three functions are exactly and only what spec.md's Assumptions
describe ("first usable command in a fixed preference order... against the opposing
combatant with lowest current health, ties broken by participant order"), phrased
generically over already-published `core` types (`Combatant`, `BattleState`,
`CombatantId`, `CommandKind`, `SkillId`) — no dependency on `content`'s
`DemoActionDefinition`. `demo-console`'s loop combines these with `content`'s
`DEMO_ACTIONS` lookup to build a concrete `CombatAction`. Placing this in `core`
(rather than `demo-console`) matches the project's own stated vision (constitution
Principle IV: "human input vs. AI are interchangeable decision sources behind the
same interface") and is genuinely reusable by any future frontend, not just this
console demo — and it needs zero change to any existing `core` file to add (verified
by direct inspection of `Combatant`/`BattleState`/`CommandKind`, none of which are
touched).

`selectAutomaticSkill` sorts by `.value` explicitly rather than trusting
`Set<SkillId>` iteration order, because `capabilities.skills` is a plain `Set` with
no ordering guarantee across JVM/JS (constitution Principle I: determinism must not
depend on platform-specific collection iteration).

**Alternatives considered**:
- *A single function returning a ready-to-resolve `CombatAction`*: rejected — would
  require `core`'s AI rule to know the formula/targeting/element for the chosen
  command, which is exactly the content-specific knowledge `core` must never hold
  (Principle II); the split keeps `core`'s contribution to "which capability, which
  target," leaving "what that capability does" to `content` as designed in R1.
- *Placing this rule in `demo-console` instead*: rejected per spec.md's own framing
  of "human input vs AI are interchangeable decision sources" as a `core`-level
  concept, and because it satisfies constitution Principle I's determinism
  requirement more naturally as commonMain code covered by kotest on both JVM and JS.

## R4: Battle-outcome (victory/defeat) detection — home and shape

**Decision**: A new `core` file, `core/.../battle/BattleOutcome.kt`:

```kotlin
sealed interface BattleOutcome {
    data object Ongoing : BattleOutcome
    data object Victory : BattleOutcome
    data object Defeat : BattleOutcome
}

fun BattleState.outcome(): BattleOutcome {
    val playerWiped = participants.filter { it.combatant.allegiance == Allegiance.PLAYER }.all { it.isDefeated }
    val opponentWiped = participants.filter { it.combatant.allegiance == Allegiance.OPPONENT }.all { it.isDefeated }
    return when {
        playerWiped -> BattleOutcome.Defeat
        opponentWiped -> BattleOutcome.Victory
        else -> BattleOutcome.Ongoing
    }
}
```

**Rationale**: `playerWiped` is checked first, so a resolution that reduces both
sides to zero able combatants simultaneously reports `Defeat` — exactly spec.md's
Assumptions tie-break ("the player's side losing is the more conservative,
unambiguous default"). Pure, total (an empty allegiance group vacuously satisfies
`all { isDefeated }` == true, but the shipped demo roster always has at least one
combatant per side, so this edge is theoretical, not exercised). Placed in `core`
for the same reusability reasoning as R3 — "is this battle over" is a property of
`BattleState` alone, not of `demo-console`'s presentation concerns, and every future
frontend needs the identical answer.

**Alternatives considered**:
- *Plain code inline in `demo-console`'s loop*: rejected — this is a `core`-level
  rule (uniform across any frontend), and keeping it in `core` makes it testable via
  the same kotest JVM+JS discipline as every other resolution rule, rather than only
  JVM-testable inside `demo-console`.

## R5: Enemies cannot currently submit any action through `resolveAction` — a discovered defect

**Finding**: `core/.../combatant/Roster.kt`'s `RosterBuilder.addEnemy` (spec 001)
unconditionally constructs every enemy's `capabilities` with `commands = emptySet()`:

```kotlin
capabilities = CapabilitySet(
    skills = definition.skills,
    commands = emptySet(),   // <- always empty, for every enemy, no exception
    limitBreaks = emptySet(),
    aiProfile = archetype.aiProfile,
),
```

`resolveAction`'s very first capability gate is
`action.command !in actor.combatant.capabilities.commands` — since this set is always
empty for every enemy, **no `CombatAction`, for any `CommandKind`, can ever pass this
gate when the actor is an enemy.** Verified by direct source inspection of
`Roster.kt` and by confirming (via `Grep` across every `commonTest` file) that no
existing test in specs 001-007 ever constructs a `CombatAction` with an enemy as
`actorId` — every existing test that exercises `resolveAction` uses a character
(`cloud`/`aerith`) as the actor; enemies only ever appear as *targets*. This is
exactly the "turn loop has never been driven end-to-end" gap spec.md's own Input
section called out, now made concrete.

This is not an intentional design choice with test coverage protecting it — it is an
uncovered defect. Left as-is, it makes FR-003 ("an automatically-controlled combatant
MUST select and submit a valid action on its own") and FR-005/SC-001 ("the battle
MUST end with an unambiguous victory or defeat" — impossible if the enemy can never
deal damage) unsatisfiable, and it directly contradicts constitution Principle IV's
explicit requirement that "the engine treats party members, enemies, and temporary AI
allies uniformly in resolution rules regardless of who decides their actions."

**Decision**: Fix this without touching `Roster.kt` (or any other spec 001-007
file), using the same public, non-sealed `Combatant` interface-delegation pattern
spec 004's `EffectiveCombatant` already established (`core/.../status/EffectiveCombatant.kt`
— confirmed via direct read that `Combatant` is a plain public interface, safely
wrappable from any module). A new file, `content/.../demo/DemoRoster.kt`, builds the
shipped demo roster via `RosterBuilder` exactly as before, then wraps each enemy
combatant to grant it the commands its declared skills actually need:

```kotlin
private data class CommandGrantedCombatant(
    private val base: Combatant,
    private val grantedCommands: Set<CommandKind>,
) : Combatant by base {
    override val capabilities: CapabilitySet =
        base.capabilities.copy(commands = base.capabilities.commands + grantedCommands)
}

fun Combatant.withGrantedCommands(commands: Set<CommandKind>): Combatant =
    CommandGrantedCombatant(this, commands)
```

Applied to every `ENEMY`-kind combatant in the demo roster: always grant
`CommandKind.ATTACK`; additionally grant `CommandKind.SKILL` if
`capabilities.skills.isNotEmpty()`. For the shipped demo content this means the bomb
(no declared skills) gets exactly `ATTACK`.

**Rationale**: Zero source lines change in any spec 001-007 file — the fix is
entirely new code in a new file, in the same spirit as every prior spec's "wrap,
don't touch" pattern, just applied one level higher (at roster-assembly time instead
of at resolution time). Scoped to this feature's actual need (the shipped demo's one
enemy) rather than speculatively generalized (Principle V) — a future feature that
wants richer enemy commands can extend `EnemyDefinition`/`ArchetypeDefinition` with a
real data-driven `commands` field as a proper spec, at which point this workaround
becomes unnecessary and is deleted, not built around further.

**Alternatives considered**:
- *Modify `Roster.kt` directly to derive `commands` from something real*: rejected —
  there is no existing data source for it (`EnemyDefinition`/`ArchetypeDefinition`
  have no `commands` field at all), so any fix here would mean either inventing a
  hardcoded default inside a frozen spec 001 file (touching it) or adding a new
  schema field (a spec 001 data-shape change) — both break this project's
  established "zero source changes to any earlier spec's files" discipline for a
  problem this feature can fully solve by wrapping instead.
- *Leave enemies permanently passive (only ever a target, never an actor)*: rejected
  — directly contradicts FR-003/SC-001/constitution Principle IV; a battle the player
  can never lose is not "an unambiguous victory or defeat," it is a foregone
  conclusion, which fails SC-001's own bar.

## R6: Status effect tick / limit gauge / synergy wiring into the loop

**Decision**: Each turn, after `resolveAction` resolves (never on a rejected
submission), the loop performs, in order: (1) derive events via `eventsFromResolution`
and append to the `EventLog`; (2) run `detectSynergyTriggers` over the newly appended
events and, for each trigger, call `applySynergyBonus` + `eventsFromSynergyBonus`,
appending those too; (3) call `chargeLimitGauge` with the accumulated events so far;
(4) call `tickStatusEffects` once (per spec 004's established one-tick-per-granted-turn
rule) and append `eventsFromTick`; (5) recompute the effective battle state via
`deriveEffectiveBattleState` before the *next* turn's scheduling/resolution, exactly
as spec 004 already prescribes as the correct calling convention.

**Rationale**: This is not a new rule — it is composing exactly the calling
convention each of specs 004/005/006's own quickstart.md already documents, applied
repeatedly instead of once. No new decision is being made here; this entry exists so
tasks.md has one authoritative ordering to implement against.

**Alternatives considered**: none — this is a direct composition of already-decided
prior-spec conventions, not a new design choice.
