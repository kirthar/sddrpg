package io.github.kirthar.sddrpg.core.schedule

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class CeilDivTest : StringSpec({

    "exact division has no remainder effect" {
        ceilDiv(10, 5) shouldBe 2
        ceilDiv(0, 7) shouldBe 0
    }

    "a remainder rounds up to the next integer" {
        ceilDiv(10, 3) shouldBe 4 // 3.33 -> 4
        ceilDiv(1, 7) shouldBe 1
    }

    "small negative numerators (bounded overshoot) resolve to 0" {
        ceilDiv(-1, 7) shouldBe 0
        ceilDiv(-6, 7) shouldBe 0
    }

    "property: ceilDiv(n, d) * d is always >= n for positive n, d" {
        checkAll(Arb.int(0..10_000), Arb.int(1..1000)) { n, d ->
            (ceilDiv(n, d) * d >= n) shouldBe true
        }
    }
})
