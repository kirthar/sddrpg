package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_ACTIONS
import io.github.kirthar.sddrpg.content.demo.find
import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.ai.selectAutomaticCommand
import io.github.kirthar.sddrpg.core.ai.selectAutomaticSkill
import io.github.kirthar.sddrpg.core.ai.selectAutomaticTarget
import io.github.kirthar.sddrpg.core.battle.BattleOutcome
import io.github.kirthar.sddrpg.core.battle.outcome
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.DecisionSource
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.applySynergyBonus
import io.github.kirthar.sddrpg.core.event.detectSynergyTriggers
import io.github.kirthar.sddrpg.core.event.eventsFromApply
import io.github.kirthar.sddrpg.core.event.eventsFromGaugeCharge
import io.github.kirthar.sddrpg.core.event.eventsFromLimitBreak
import io.github.kirthar.sddrpg.core.event.eventsFromResolution
import io.github.kirthar.sddrpg.core.event.eventsFromSchedule
import io.github.kirthar.sddrpg.core.event.eventsFromSummon
import io.github.kirthar.sddrpg.core.event.eventsFromSynergyBonus
import io.github.kirthar.sddrpg.core.event.eventsFromTick
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakResolutionResult
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.limitbreak.ResourceState
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.limitbreak.SummonResolutionResult
import io.github.kirthar.sddrpg.core.limitbreak.chargeLimitGauge
import io.github.kirthar.sddrpg.core.limitbreak.resolveLimitBreak
import io.github.kirthar.sddrpg.core.limitbreak.resolveSummon
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.AtbScheduleState
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.deriveEffectiveBattleState
import io.github.kirthar.sddrpg.core.status.tickStatusEffects

/** One submitted action, ready to resolve (spec 008 data-model.md, extended for US3). */
private sealed interface Submission {
    data class Basic(
        val action: CombatAction,
        val targetIds: Set<CombatantId>,
        val statusEffectId: StatusEffectId?,
    ) : Submission
    data class LimitBreak(val actorId: CombatantId, val targetIds: Set<CombatantId>) : Submission
    data class Summon(val actorId: CombatantId, val summonId: SummonId, val targetIds: Set<CombatantId>) : Submission
}

/** The common shape every submission kind resolves down to, for shared post-processing. */
private data class TurnResolution(
    val newBattle: BattleState,
    val actionEvents: List<BattleEvent>,
    val newGauges: LimitGaugeState,
    val newResources: ResourceState,
    val statusEffectId: StatusEffectId? = null,
    val statusEffectTargets: Set<CombatantId> = emptySet(),
)

/** "Basic attack first" (spec.md Assumptions): the fixed preference order for automatic combatants. */
private val AUTOMATIC_COMMAND_PREFERENCE = listOf(
    CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC, CommandKind.SUMMON, CommandKind.ITEM, CommandKind.DEFEND,
)

private fun automaticSubmission(battle: BattleState, actorId: CombatantId): Submission? {
    val actor = battle.find(actorId)?.combatant ?: return null
    val command = selectAutomaticCommand(actor, AUTOMATIC_COMMAND_PREFERENCE) ?: return null
    val skillId = if (command == CommandKind.ATTACK) null else selectAutomaticSkill(actor)
    val definition = DEMO_ACTIONS.find(command, skillId) ?: return null
    val targetId = selectAutomaticTarget(battle, actorId) ?: return null
    return Submission.Basic(
        action = CombatAction(
            actorId = actorId,
            command = definition.command,
            skillId = definition.skillId,
            effectKind = definition.effectKind,
            element = definition.element,
            formula = definition.formula,
            targeting = definition.targeting,
        ),
        targetIds = setOf(targetId),
        statusEffectId = definition.appliesStatusEffect,
    )
}

/**
 * Prompts, parses, and gates exactly as `resolveAction`/`resolveLimitBreak`/
 * `resolveSummon` gate (FR-002): reprompts on unparseable input or on rejection,
 * never crashes, never consumes the turn on failure. Returns `null` on EOF
 * (`readInput` returning `null`). Recognizes, by name: "attack", any known skill id
 * (e.g. "cleave", "fira"), any limit break id the actor's class grants (e.g.
 * "omnislash"), or any shipped summon id (e.g. "meteor") -- spec 008 US3.
 */
private fun humanSubmission(
    battle: BattleState,
    actorId: CombatantId,
    gauges: LimitGaugeState,
    resources: ResourceState,
    content: ContentPack,
    readInput: () -> String?,
    emit: (String) -> Unit,
): Submission? {
    val actor = battle.find(actorId)!!.combatant
    while (true) {
        emit("${actor.displayName}'s turn. Enter an action and target (e.g. \"attack Bomb\"):")
        val line = readInput() ?: return null
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) {
            emit("Could not parse that. Expected: <action> <target name>.")
            continue
        }
        val actionToken = tokens[0]
        val targetName = tokens.drop(1).joinToString(" ")

        val target = battle.participants.firstOrNull { it.combatant.displayName.equals(targetName, ignoreCase = true) }
        if (target == null) {
            emit("Unknown target \"$targetName\".")
            continue
        }
        val targetId = target.combatant.id

        val limitBreakId = LimitBreakId(actionToken)
        val summonId = SummonId(actionToken)

        when {
            content.limitBreaks.limitBreaks.any { it.id == limitBreakId } -> {
                val probe = resolveLimitBreak(battle, gauges, actorId, setOf(targetId), content.limitBreaks)
                if (probe is LimitBreakResolutionResult.Rejected) {
                    emit("That's not valid right now: ${probe.error}")
                    continue
                }
                return Submission.LimitBreak(actorId, setOf(targetId))
            }
            content.summons.summons.any { it.id == summonId } -> {
                val probe = resolveSummon(battle, resources, actorId, summonId, setOf(targetId), content.summons)
                if (probe is SummonResolutionResult.Rejected) {
                    emit("That's not valid right now: ${probe.error}")
                    continue
                }
                return Submission.Summon(actorId, summonId, setOf(targetId))
            }
            else -> {
                val definition = if (actionToken.equals("attack", ignoreCase = true)) {
                    DEMO_ACTIONS.find(CommandKind.ATTACK, null)
                } else {
                    DEMO_ACTIONS.firstOrNull { it.skillId?.value.equals(actionToken, ignoreCase = true) }
                }
                if (definition == null) {
                    emit("Unknown action \"$actionToken\".")
                    continue
                }

                val action = CombatAction(
                    actorId = actorId,
                    command = definition.command,
                    skillId = definition.skillId,
                    effectKind = definition.effectKind,
                    element = definition.element,
                    formula = definition.formula,
                    targeting = definition.targeting,
                )
                val probe = resolveAction(battle, action, setOf(targetId))
                if (probe is ActionResolutionResult.Rejected) {
                    emit("That's not valid right now: ${probe.error.toDisplayText(battle)}")
                    continue
                }
                return Submission.Basic(action, setOf(targetId), definition.appliesStatusEffect)
            }
        }
    }
}

/** Resolves any [Submission] kind down to the shared [TurnResolution] shape, or `null` if rejected. */
private fun resolveSubmission(
    battle: BattleState,
    gauges: LimitGaugeState,
    resources: ResourceState,
    submission: Submission,
    content: ContentPack,
): TurnResolution? = when (submission) {
    is Submission.Basic -> {
        val result = resolveAction(battle, submission.action, submission.targetIds)
        if (result !is ActionResolutionResult.Resolved) null else TurnResolution(
            newBattle = result.newState,
            actionEvents = eventsFromResolution(battle, submission.action, result),
            newGauges = gauges,
            newResources = resources,
            statusEffectId = submission.statusEffectId,
            statusEffectTargets = submission.targetIds,
        )
    }
    is Submission.LimitBreak -> {
        val actor = battle.find(submission.actorId)!!.combatant
        val limitBreakId = actor.capabilities.limitBreaks.first()
        val result = resolveLimitBreak(battle, gauges, submission.actorId, submission.targetIds, content.limitBreaks)
        if (result !is LimitBreakResolutionResult.Resolved) null else {
            val definition = content.limitBreaks.limitBreaks.first { it.id == limitBreakId }
            TurnResolution(
                newBattle = result.newState,
                actionEvents = eventsFromLimitBreak(battle, submission.actorId, limitBreakId, definition.effectKind, result),
                newGauges = result.newGauges,
                newResources = resources,
            )
        }
    }
    is Submission.Summon -> {
        val result = resolveSummon(battle, resources, submission.actorId, submission.summonId, submission.targetIds, content.summons)
        if (result !is SummonResolutionResult.Resolved) null else {
            val definition = content.summons.summons.first { it.id == submission.summonId }
            TurnResolution(
                newBattle = result.newState,
                actionEvents = eventsFromSummon(battle, submission.actorId, submission.summonId, definition.effectKind, result),
                newGauges = gauges,
                newResources = result.newResources,
            )
        }
    }
}

/**
 * The console-I/O-injectable turn-by-turn driver (spec 008 FR-007). Never calls
 * `readLine()`/`println()` directly -- all I/O flows through [readInput]/[emit], so
 * this is testable with scripted values and no real console. [session]`.battle` is
 * always the *raw*, never-effective-wrapped state; a fresh stat-adjusted view is
 * derived from it each turn and never persisted (research.md R6/data-model.md).
 */
fun runBattleLoop(
    session: BattleSession,
    content: ContentPack,
    readInput: () -> String?,
    emit: (String) -> Unit,
): BattleOutcome {
    var current = session

    while (true) {
        val outcome = current.battle.outcome()
        if (outcome != BattleOutcome.Ongoing) return outcome

        val effectiveBattle = deriveEffectiveBattleState(current.battle, current.effects, content.statusEffects)

        val scheduled = ActiveTimeBattleScheduler.nextTurn(current.schedule, effectiveBattle)
        if (scheduled !is ScheduleResult.Ready<AtbScheduleState>) return effectiveBattle.outcome()
        val ready: ScheduleResult.Ready<AtbScheduleState> = scheduled

        val actorId = ready.combatantId
        val actor = effectiveBattle.find(actorId)!!.combatant

        val turnGrantedEvents = eventsFromSchedule(ready)
        var log = current.log.append(turnGrantedEvents)
        turnGrantedEvents.forEach { emit(it.toDisplayText(effectiveBattle)) }

        val submission: Submission? = if (actor.decisionSource is DecisionSource.Human) {
            humanSubmission(effectiveBattle, actorId, current.gauges, current.resources, content, readInput, emit)
        } else {
            automaticSubmission(effectiveBattle, actorId)
        }

        if (submission == null && actor.decisionSource is DecisionSource.Human) {
            // EOF while awaiting a human submission: end gracefully, no crash (edge case).
            return effectiveBattle.outcome()
        }

        var battle = effectiveBattle
        var effects = current.effects
        var gauges = current.gauges
        var resources = current.resources

        if (submission != null) {
            val resolution = resolveSubmission(battle, gauges, resources, submission, content)
            if (resolution != null) {
                val actionEvents = resolution.actionEvents
                battle = resolution.newBattle
                gauges = resolution.newGauges
                resources = resolution.newResources
                log = log.append(actionEvents)
                actionEvents.forEach { emit(it.toDisplayText(battle)) }

                val allNewEvents = mutableListOf<BattleEvent>()
                allNewEvents += actionEvents

                if (resolution.statusEffectId != null) {
                    val applyEvents = resolution.statusEffectTargets.flatMap { targetId ->
                        effects = applyStatusEffect(effects, targetId, resolution.statusEffectId, content.statusEffects)
                        eventsFromApply(targetId, resolution.statusEffectId)
                    }
                    log = log.append(applyEvents)
                    applyEvents.forEach { emit(it.toDisplayText(battle)) }
                    allNewEvents += applyEvents
                }

                val triggers = detectSynergyTriggers(actionEvents, log, battle, content.synergies)
                for (trigger in triggers) {
                    val before = battle
                    battle = applySynergyBonus(battle, trigger.targetId, trigger.definition.bonus)
                    val bonusEvents = eventsFromSynergyBonus(before, trigger.targetId, trigger.definition.bonus, battle)
                    val triggerEvent = BattleEvent.SynergyTriggered(
                        trigger.definition.id, trigger.firstActorId, trigger.secondActorId, trigger.targetId,
                    )
                    val synergyEvents = listOf(triggerEvent) + bonusEvents
                    log = log.append(synergyEvents)
                    synergyEvents.forEach { emit(it.toDisplayText(battle)) }
                    allNewEvents += synergyEvents
                }

                val beforeGauges = gauges
                gauges = chargeLimitGauge(beforeGauges, battle, allNewEvents, content.limitBreaks)
                val gaugeEvents = eventsFromGaugeCharge(battle, beforeGauges, gauges, content.limitBreaks)
                log = log.append(gaugeEvents)
                gaugeEvents.forEach { emit(it.toDisplayText(battle)) }
            }
            // A rejected resolution here (automatic path only -- human already
            // validated via humanSubmission's own probe) is treated as a skipped turn.
        }

        // One status-effect tick per granted turn (spec 004 FR-007), regardless of
        // whether the actor's own submission resolved, was skipped, or was rejected.
        val tickResult = tickStatusEffects(effects, battle, content.statusEffects)
        val tickEvents = eventsFromTick(effects, battle, content.statusEffects, tickResult)
        log = log.append(tickEvents)
        tickEvents.forEach { emit(it.toDisplayText(tickResult.newBattle)) }
        battle = tickResult.newBattle
        effects = tickResult.newEffects

        val newSchedule = ActiveTimeBattleScheduler.markSpent(ready.advancedSchedule, actorId)

        // Merge this turn's health changes back onto the *raw* (never wrapped)
        // combatants before persisting, so next turn's `deriveEffectiveBattleState`
        // call never compounds a prior turn's stat modifiers.
        val rawBattle = BattleState(
            current.battle.participants.map { rawParticipant ->
                val updated = battle.find(rawParticipant.combatant.id)!!
                rawParticipant.copy(health = updated.health)
            }
        )

        current = BattleSession(rawBattle, newSchedule, effects, gauges, resources, log)
    }
}
