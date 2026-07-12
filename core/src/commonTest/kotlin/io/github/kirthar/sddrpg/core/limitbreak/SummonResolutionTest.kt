package io.github.kirthar.sddrpg.core.limitbreak

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SummonResolutionTest : FunSpec({
    val catalog = SummonCatalog(listOf(meteorDefinition))

    test("sufficient resource resolves the summon's effect and deducts exactly its cost (US3 scenario 1)") {
        val battle = freshLimitBreakBattleState()
        val resources = battle.initialResourceState()
        val before = resources.current.getValue(cloudId)

        val result = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), catalog)
        val resolved = result.shouldBeInstanceOf<SummonResolutionResult.Resolved>()

        resolved.outcomes.single().targetId shouldBe bombId
        resolved.newResources.current[cloudId] shouldBe before - meteorDefinition.cost
    }

    test("insufficient resource rejects with nothing deducted (US3 scenario 2)") {
        val battle = freshLimitBreakBattleState()
        val resources = ResourceState(mapOf(cloudId to meteorDefinition.cost - 1))

        val result = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), catalog)
        val rejected = result.shouldBeInstanceOf<SummonResolutionResult.Rejected>()

        rejected.error shouldBe SummonError.InsufficientResource(cloudId, meteorDefinition.cost, meteorDefinition.cost - 1)
        resources.current[cloudId] shouldBe meteorDefinition.cost - 1
    }

    test("the battle's participant count is unchanged before and after a summon resolves (US3 scenario 3)") {
        val battle = freshLimitBreakBattleState()
        val resources = battle.initialResourceState()
        val before = battle.participants.size

        val result = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), catalog)
        val resolved = result.shouldBeInstanceOf<SummonResolutionResult.Resolved>()
        resolved.newState.participants.size shouldBe before
    }

    test("a cost exactly equal to available resource is allowed, leaving exactly zero") {
        val battle = freshLimitBreakBattleState()
        val resources = ResourceState(mapOf(cloudId to meteorDefinition.cost))

        val result = resolveSummon(battle, resources, cloudId, meteorId, setOf(bombId), catalog)
        val resolved = result.shouldBeInstanceOf<SummonResolutionResult.Resolved>()
        resolved.newResources.current[cloudId] shouldBe 0
    }

    test("an unknown SummonId is rejected") {
        val battle = freshLimitBreakBattleState()
        val resources = battle.initialResourceState()
        val unknownId = SummonId("unknown")

        val result = resolveSummon(battle, resources, cloudId, unknownId, setOf(bombId), catalog)
        result.shouldBeInstanceOf<SummonResolutionResult.Rejected>().error shouldBe SummonError.UnknownSummon(unknownId)
    }
})
