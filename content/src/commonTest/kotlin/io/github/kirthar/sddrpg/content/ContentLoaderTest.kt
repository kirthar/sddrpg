package io.github.kirthar.sddrpg.content

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ContentLoaderTest : FunSpec({
    test("a complete valid content set loads as one successful package containing every catalog's content (US1 scenario 1)") {
        val result = loadContentPack(completeContentText)
        val valid = result.shouldBeInstanceOf<ContentLoadResult.Valid>()

        valid.pack.statusEffects.effects shouldHaveSize 1
        valid.pack.synergies.synergies shouldHaveSize 1
        valid.pack.limitBreaks.limitBreaks shouldHaveSize 1
        valid.pack.summons.summons shouldHaveSize 1
    }

    test("a problem in exactly one catalog kind is reported, identifying which catalog and which entry (US1 scenario 2)") {
        val result = loadContentPack(duplicateStatusEffectContentText)
        val invalid = result.shouldBeInstanceOf<ContentLoadResult.Invalid>()

        invalid.problems shouldHaveSize 1
        invalid.problems.single().shouldBeInstanceOf<ContentProblem.StatusEffectProblem>()
    }

    test("unrelated problems in several different catalog kinds are all reported together, never only the first (US1 scenario 3)") {
        val result = loadContentPack(multipleUnrelatedProblemsContentText)
        val invalid = result.shouldBeInstanceOf<ContentLoadResult.Invalid>()

        invalid.problems.filterIsInstance<ContentProblem.StatusEffectProblem>() shouldHaveSize 1
        invalid.problems.filterIsInstance<ContentProblem.LimitBreakProblem>() shouldHaveSize 1
        invalid.problems shouldHaveSize 2
    }

    test("an omitted optional catalog loads successfully as empty (US1 scenario 4)") {
        val result = loadContentPack(minimalContentText)
        val valid = result.shouldBeInstanceOf<ContentLoadResult.Valid>()

        valid.pack.statusEffects.effects shouldBe emptyList()
        valid.pack.synergies.synergies shouldBe emptyList()
        valid.pack.limitBreaks.limitBreaks shouldBe emptyList()
        valid.pack.summons.summons shouldBe emptyList()
    }

    test("malformed JSON is reported as MalformedContent, distinct from a business-rule problem, and loading stops there") {
        val result = loadContentPack(malformedContentText)
        val invalid = result.shouldBeInstanceOf<ContentLoadResult.Invalid>()

        invalid.problems shouldHaveSize 1
        invalid.problems.single().shouldBeInstanceOf<ContentProblem.MalformedContent>()
    }
})
