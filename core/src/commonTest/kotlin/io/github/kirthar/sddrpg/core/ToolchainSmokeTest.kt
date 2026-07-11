package io.github.kirthar.sddrpg.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

/**
 * Proves the test toolchain (kotest engine, assertions, property testing) runs
 * on every target. Not a business test.
 */
class ToolchainSmokeTest : StringSpec({

    "kotest engine and assertions are wired" {
        ENGINE_NAME shouldBe "sddrpg-core"
    }

    "kotest property testing is wired" {
        checkAll<Int, Int> { a, b ->
            a + b shouldBe b + a
        }
    }
})
