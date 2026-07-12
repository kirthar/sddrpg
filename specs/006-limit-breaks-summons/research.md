# Research: Limit Breaks & Summons

**Feature**: 006-limit-breaks-summons | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here. R1 is the central architectural question spec.md deliberately left
open ("how a limit-break/summon action is actually submitted and gated"); R4 is a
concrete Kotlin-language question resolved by direct compiler experiment rather than
assumption, the same discipline spec 005's R1 applied to `Combatant.kt`'s source.

## R1 — Submission and gating: reuse `CommandKind.SUMMON` where it already exists, wrap where it doesn't

**Decision**: Verifying `catalog/Definitions.kt`'s actual source found `CommandKind`
already has a `SUMMON` value (`ATTACK, SKILL, MAGIC, DEFEND, ITEM, SUMMON`) — spec 001
anticipated this feature. **Summons** synthesize a `CombatAction(command =
CommandKind.SUMMON, skillId = null, effectKind, formula, targeting)` from the
`SummonDefinition` and call `resolveAction` directly: `resolveAction`'s own existing
`action.command !in actor.combatant.capabilities.commands` gate is reused completely
unmodified for "is this combatant allowed to summon at all." The only gate this
feature adds is MP-sufficiency, checked *before* calling `resolveAction`, in a small
wrapper `resolveSummon(...)` that deducts the cost and only then delegates.

**Limit breaks** have no dedicated `CommandKind` (spec 001 has no `LIMIT_BREAK` value,
and adding one would edit `catalog/Definitions.kt` — forbidden, FR-012). They
synthesize `CombatAction(command = CommandKind.SKILL, skillId = null, ...)` instead.
The real gate — gauge ≥ threshold, checked against `combatant.capabilities.limitBreaks`
(already public per spec 001, no `classId`-style obstacle here) — is entirely this
feature's own, enforced by a wrapper `resolveLimitBreak(...)` *before* calling
`resolveAction`. Reusing `SKILL` as the underlying command is a content-authoring
convention (a class granting a limit break is expected to also grant `SKILL`) rather
than a hard technical requirement; if a class doesn't, `resolveAction`'s existing
`MissingCommand` rejection applies, which is a defensible fallback, not a bug.

**Rationale**: This is the same "wrap, don't touch" shape as spec 004's R1
(`deriveEffectiveBattleState` feeds `resolveAction` an adjusted input) and spec 005's
R1 (pure derivation over `resolveAction`'s already-produced output) applied at the
call-construction end: neither `resolveAction`, `CombatAction`, nor `CommandKind` are
modified; this feature only ever *constructs* values of existing public shapes and
calls existing public functions. Discovering `CommandKind.SUMMON` already exists
avoids inventing a parallel, redundant capability-gating mechanism for summons
specifically — reusing what's already there is simpler and more honest than wrapping
something that doesn't need wrapping.

**Alternatives considered**: adding `CommandKind.LIMIT_BREAK` (touches an existing
spec 001 file — rejected, FR-012, even though it would be the most semantically
precise fit); giving `CombatAction` a new nullable `limitBreakId`/`summonId` field
(touches an existing spec 002 file — rejected, same discipline as spec 004/005's
rejected "extend the existing type" alternatives); requiring `resolveAction` itself to
know about gauges/resources (impossible without modifying it — explicitly forbidden).

## R2 — `LimitGaugeState`: charges by scanning already-derived `BattleEvent.DamageDealt`, never hooking `resolveAction`

**Decision**: `LimitGaugeState(gauges: Map<CombatantId, Int> = emptyMap())`, mirroring
spec 004's `StatusEffectState` shape. `chargeLimitGauge(state, battle, events,
catalog): LimitGaugeState` is a pure function taking the `List<BattleEvent>` a caller
already derived via spec 005's `eventsFromResolution`/`eventsFromTick` (any source —
this feature doesn't care which) and, for each `DamageDealt` event, looks up
`battle.find(targetId)!!.combatant.capabilities.limitBreaks.firstOrNull()` (R5) to
find that combatant's active limit break id, then its threshold in `catalog`, and adds
`event.amount` to that combatant's gauge, clamped at the threshold
(`(current + amount).coerceAtMost(threshold)`). A combatant whose class declares no
limit break (`firstOrNull()` returns null) is skipped entirely — no gauge entry is
ever created for them (US1 scenario 3).

**Rationale**: The spec's own Assumptions explicitly ask for this — reusing spec 005's
event log "as the accrual data source rather than inventing a second observation
mechanism." Since `BattleEvent.DamageDealt` already carries `targetId` and `amount`
regardless of source (a resolved action, a damage-over-time tick, or even a future
synergy bonus), one scan handles every damage source uniformly with no special-casing,
and — critically — `resolveAction`/`tickStatusEffects` need zero awareness that limit
breaks exist, extending the exact "wrap, don't touch" discipline spec 005 established
for events one layer further.

**Alternatives considered**: charging directly inside a `resolveLimitBreak`-adjacent
wrapper around every damage-producing call site (would require call sites to
remember to charge gauges in multiple places — event-scanning centralizes it in one
pure function instead, matching Principle I's single-source-of-truth spirit); charging
from raw `ResolutionOutcome`s instead of `BattleEvent`s (would require this feature to
also understand `StatusTickResult`'s shape directly, duplicating exactly what spec 005
already unified into one event shape — pointless).

## R3 — `ResourceState`: a new parallel Map, since `BattleState`/`HealthTrack` never tracked MP

**Decision**: `ResourceState(current: Map<CombatantId, Int> = emptyMap())`,
initialized once per battle via `fun BattleState.initialResourceState(): ResourceState`
that reads each participant's `combatant.stats[CoreStats.MP]` (spec 001's `CoreStats`
already declares `MP` — confirmed in `model/Stats.kt`) — the same "snapshot at
battle-state-construction time" shape `Roster.toBattleState()` (spec 002) already
established for `HealthTrack`. `deductResource(state, combatantId, amount):
ResourceState` subtracts and is only ever called after a sufficiency check (`current
>= cost`) has already passed in `resolveSummon`.

**Rationale**: `HealthTrack`/`BattleCombatant` (spec 002) have no MP field, and adding
one would touch an existing spec 002 file — rejected, same discipline as every prior
spec's "no changes to existing public signatures." A parallel `Map<CombatantId, Int>`
state, live alongside `BattleState` the same way `StatusEffectState` (spec 004) and
`LimitGaugeState` (R2) already do, costs nothing and needs no cross-validation against
anything but the summon catalog's cost field.

**Alternatives considered**: extending `HealthTrack`/`BattleCombatant` with an `mp`
field (touches an existing spec 002 file — rejected, FR-012); reusing spec 004's
`StatusEffectState` shape for resources too (semantically wrong — resources aren't
effects with a duration/kind, just a plain integer pool; forcing them through that
shape would be a worse fit than a dedicated, simpler `Map<CombatantId, Int>`).

## R4 — New `BattleEvent` variants require the same package, verified empirically, not the same file

**Decision**: Confirmed by direct compiler experiment (not assumption): a throwaway
`data class ProbeEvent(...) : BattleEvent` declared in a *different* package
(`io.github.kirthar.sddrpg.core.event.probe`) fails with `error: A class can only
extend a sealed class or interface declared in the same package`; the identical
declaration in a *new file* inside the *same* package
(`io.github.kirthar.sddrpg.core.event`) compiles cleanly against the unmodified
`BattleEvent.kt`. `GaugeFull`, `LimitBreakUsed`, and `SummonCast` therefore live in a
new `event/LimitBreakSummonEvents.kt` — not a `limitbreak`-package file, and not an
edit to `BattleEvent.kt`. Grepping every `when` expression over `BattleEvent` in the
existing codebase (`SynergyResolution.kt`'s `qualifyingActorAndTarget`, the only one)
confirmed it already has an `else -> null` branch — adding new variants breaks no
existing exhaustiveness anywhere.

**Rationale**: Kotlin's sealed-type same-package restriction is a hard compiler rule,
not a style convention — verifying it directly (the same "check the actual source/
behavior before writing a requirement around it" discipline spec 005's R1 used on
`Combatant.kt`) avoids either wrongly assuming it's impossible (and inventing an
unnecessary workaround) or wrongly assuming any package will do (and hitting a
compile error mid-implementation). The one-new-file-same-package placement keeps
`BattleEvent.kt` itself byte-for-byte untouched (FR-012) while still growing the
closed event set additively.

**Alternatives considered**: a second, feature-specific sealed type
(`LimitBreakEvent`) instead of extending `BattleEvent` (would fragment the single
event record FR-010 explicitly asks these occurrences to appear in — a caller would
need to inspect two separate logs, defeating spec 005's whole "one place to observe
everything" design goal); moving `BattleEvent`'s declaration itself (touches spec
005's file — rejected outright).

## R5 — "Active" limit break: `capabilities.limitBreaks.firstOrNull()`, no `classId` needed

**Decision**: A combatant's active limit break (spec.md Assumptions: "one active per
combatant, v1") is read as `combatant.capabilities.limitBreaks.firstOrNull()` —
`CapabilitySet.limitBreaks: Set<LimitBreakId>` is already public on spec 001's
`Combatant` interface (unlike `classId`, which spec 005's R1-adjacent discovery found
was *not* public). No workaround or reinterpretation is needed here, unlike spec 005's
skill-based pivot.

**Rationale**: Directly observable through the existing public API, zero new surface
needed, and consistent with how spec 002/004 already read `capabilities.skills`/
`capabilities.commands` the same way for their own gating.

**Alternatives considered**: none seriously — this was a straightforward confirmation,
not a genuine design fork (checked precisely because spec 005 taught the lesson that
"is this actually public" must be verified, not assumed).
