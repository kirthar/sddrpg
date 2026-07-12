package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/** FR-007/SC-005: the same content text, loaded twice, always produces structurally identical results. */
class ContentLoaderDeterminismTest : StringSpec({
    "property: replaying loadContentPack on the same text twice yields structurally identical results" {
        checkAll(
            Arb.element(
                listOf(
                    completeContentText,
                    minimalContentText,
                    duplicateStatusEffectContentText,
                    multipleUnrelatedProblemsContentText,
                    malformedContentText,
                    DEMO_CONTENT_JSON,
                )
            )
        ) { text ->
            val first = loadContentPack(text)
            val second = loadContentPack(text)
            // Compared as fingerprints, not via shouldBe on the whole result: spec
            // 001's ValidatedCatalog (inside ContentPack) is backed by a plain class
            // with reference equality, not a data class, so two separately-loaded
            // instances are never `==` even when their underlying data is identical
            // -- a pre-existing property of that spec 001 type, out of scope to
            // change here (FR-009). Every other field is a proper data class.
            fingerprint(first) shouldBe fingerprint(second)
        }
    }
})

/** A structurally-comparable projection of [ContentLoadResult], sidestepping ValidatedCatalog's reference equality. */
private fun fingerprint(result: ContentLoadResult): Any = when (result) {
    is ContentLoadResult.Invalid -> result.problems
    is ContentLoadResult.Valid -> listOf(
        result.pack.statusEffects,
        result.pack.synergies,
        result.pack.limitBreaks,
        result.pack.summons,
        result.pack.catalog.customStats,
        result.pack.catalog.elements,
    )
}
