package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.model.Affinity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ElementalAdjustmentTest : StringSpec({

    "neutral leaves a damage delta unchanged" {
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.NEUTRAL) shouldBe -10
    }

    "weakness doubles a damage delta's magnitude" {
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.WEAKNESS) shouldBe -20
    }

    "resistance halves a damage delta's magnitude, rounding half away from zero" {
        applyElementalAdjustment(baseDelta = -11, stance = Affinity.RESISTANCE) shouldBe -6
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.RESISTANCE) shouldBe -5
    }

    "immunity zeroes any delta" {
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.IMMUNITY) shouldBe 0
        applyElementalAdjustment(baseDelta = 10, stance = Affinity.IMMUNITY) shouldBe 0
    }

    "absorption reverses a damage delta into a heal, same magnitude" {
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.ABSORPTION) shouldBe 10
    }

    "the table applies symmetrically to a heal-signed (positive) delta" {
        applyElementalAdjustment(baseDelta = 10, stance = Affinity.WEAKNESS) shouldBe 20
        applyElementalAdjustment(baseDelta = 10, stance = Affinity.RESISTANCE) shouldBe 5
        applyElementalAdjustment(baseDelta = 10, stance = Affinity.ABSORPTION) shouldBe -10
    }
})
