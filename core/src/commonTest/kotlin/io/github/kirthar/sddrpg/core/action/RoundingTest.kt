package io.github.kirthar.sddrpg.core.action

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RoundingTest : StringSpec({

    "ties round away from zero, both signs" {
        roundHalfAwayFromZero(7.5) shouldBe 8
        roundHalfAwayFromZero(-7.5) shouldBe -8
    }

    "non-ties round to nearest as usual" {
        roundHalfAwayFromZero(7.4) shouldBe 7
        roundHalfAwayFromZero(7.6) shouldBe 8
        roundHalfAwayFromZero(-7.4) shouldBe -7
        roundHalfAwayFromZero(-7.6) shouldBe -8
    }

    "exact integers are unchanged" {
        roundHalfAwayFromZero(10.0) shouldBe 10
        roundHalfAwayFromZero(0.0) shouldBe 0
    }
})
