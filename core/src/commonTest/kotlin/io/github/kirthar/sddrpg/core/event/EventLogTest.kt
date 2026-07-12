package io.github.kirthar.sddrpg.core.event

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EventLogTest : FunSpec({
    test("a fresh EventLog's entries is empty, not an error (US1 scenario 1)") {
        EventLog().entries shouldBe emptyList()
        EventLog().turnsGranted shouldBe 0
    }

    test("append grows the log by exactly the new events, never losing or reordering existing entries (US1 scenario 7)") {
        val log = EventLog()
            .append(listOf(BattleEvent.DamageDealt(cloudId, bombId, 5, 45)))
            .append(listOf(BattleEvent.HealingApplied(aerithId, cloudId, 3, 23)))

        log.entries.map { it.event } shouldBe listOf(
            BattleEvent.DamageDealt(cloudId, bombId, 5, 45),
            BattleEvent.HealingApplied(aerithId, cloudId, 3, 23),
        )
    }

    test("two events appended in the same call before any TurnGranted share a turnIndex") {
        val log = EventLog().append(
            listOf(
                BattleEvent.DamageDealt(cloudId, bombId, 5, 45),
                BattleEvent.DamageDealt(aerithId, bombId, 3, 42),
            )
        )
        log.entries.map { it.turnIndex } shouldBe listOf(0, 0)
    }

    test("a TurnGranted within a batch bumps the index for everything appended after it in that same batch") {
        val log = EventLog().append(
            listOf(
                BattleEvent.DamageDealt(cloudId, bombId, 5, 45),
                BattleEvent.TurnGranted(aerithId),
                BattleEvent.DamageDealt(aerithId, bombId, 3, 42),
            )
        )
        log.entries.map { it.turnIndex } shouldBe listOf(0, 0, 1)
        log.turnsGranted shouldBe 1
    }

    test("a later append call continues from the running turnsGranted count") {
        val log = EventLog()
            .append(listOf(BattleEvent.TurnGranted(cloudId)))
            .append(listOf(BattleEvent.DamageDealt(cloudId, bombId, 5, 45)))
        log.entries.last().turnIndex shouldBe 1
        log.turnsGranted shouldBe 1
    }
})
