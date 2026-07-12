package io.github.kirthar.sddrpg.content.dto

import io.github.kirthar.sddrpg.core.event.SynergyBonus
import io.github.kirthar.sddrpg.core.event.SynergyDefinition
import io.github.kirthar.sddrpg.core.event.SynergyId
import io.github.kirthar.sddrpg.core.model.SkillId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Serializable mirror of spec 005's [SynergyBonus] sealed interface. */
@Serializable
sealed interface SynergyBonusDto {
    @Serializable
    @SerialName("BonusDamage")
    data class BonusDamage(val amount: Int) : SynergyBonusDto
}

fun SynergyBonusDto.toCore(): SynergyBonus = when (this) {
    is SynergyBonusDto.BonusDamage -> SynergyBonus.BonusDamage(amount)
}

/** Serializable mirror of spec 005's [SynergyDefinition]. */
@Serializable
data class SynergyDefinitionDto(
    val id: String,
    val firstSkillId: SkillId,
    val secondSkillId: SkillId,
    val window: Int,
    val bonus: SynergyBonusDto,
)

fun SynergyDefinitionDto.toCore(): SynergyDefinition =
    SynergyDefinition(SynergyId(id), firstSkillId, secondSkillId, window, bonus.toCore())
