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
import io.github.kirthar.sddrpg.core.event.eventsFromResolution
import io.github.kirthar.sddrpg.core.event.eventsFromSchedule
import io.github.kirthar.sddrpg.core.event.eventsFromSynergyBonus
import io.github.kirthar.sddrpg.core.event.eventsFromTick
import io.github.kirthar.sddrpg.core.limitbreak.chargeLimitGauge
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.AtbScheduleState
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.deriveEffectiveBattleState
import io.github.kirthar.sddrpg.core.status.tickStatusEffects

/** One submitted action, ready to resolve (spec 008 data-model.md). */
private data class Submission(
    val action: CombatAction,
    val targetIds: Set<CombatantId>,
    val statusEffectId: StatusEffectId?,
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
    return Submission(
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
 * Prompts, parses, and gates exactly as `resolveAction` gates (FR-002): reprompts on
 * unparseable input or on rejection, never crashes, never consumes the turn on
 * failure. Returns `null` on EOF (`readInput` returning `null`).
 */
private fun humanSubmission(
    battle: BattleState,
    actorId: CombatantId,
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

        val definition = if (actionToken.equals("attack", ignoreCase = true)) {
            DEMO_ACTIONS.find(CommandKind.ATTACK, null)
        } else {
            DEMO_ACTIONS.firstOrNull { it.skillId?.value.equals(actionToken, ignoreCase = true) }
        }
        if (definition == null) {
            emit("Unknown action \"$actionToken\".")
            continue
        }

        val target = battle.participants.firstOrNull { it.combatant.displayName.equals(targetName, ignoreCase = true) }
        if (target == null) {
            emit("Unknown target \"$targetName\".")
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
        val probe = resolveAction(battle, action, setOf(target.combatant.id))
        if (probe is ActionResolutionResult.Rejected) {
            emit("That's not valid right now: ${probe.error.toDisplayText(battle)}")
            continue
        }
        return Submission(action, setOf(target.combatant.id), definition.appliesStatusEffect)
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
            humanSubmission(effectiveBattle, actorId, readInput, emit)
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

        if (submission != null) {
            val result = resolveAction(battle, submission.action, submission.targetIds)
            if (result is ActionResolutionResult.Resolved) {
                val actionEvents = eventsFromResolution(battle, submission.action, result)
                battle = result.newState
                log = log.append(actionEvents)
                actionEvents.forEach { emit(it.toDisplayText(battle)) }

                val allNewEvents = mutableListOf<BattleEvent>()
                allNewEvents += actionEvents

                if (submission.statusEffectId != null) {
                    val applyEvents = submission.targetIds.flatMap { targetId ->
                        effects = applyStatusEffect(effects, targetId, submission.statusEffectId, content.statusEffects)
                        eventsFromApply(targetId, submission.statusEffectId)
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
            // A Rejected result here (automatic path only -- human already validated
            // via humanSubmission's own probe) is treated as a skipped turn: no crash.
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

        current = BattleSession(rawBattle, newSchedule, effects, gauges, current.resources, log)
    }
}
