package io.github.kirthar.sddrpg.content.dto

import io.github.kirthar.sddrpg.core.model.StatId
import io.github.kirthar.sddrpg.core.status.EffectKind
import io.github.kirthar.sddrpg.core.status.StatusEffectDefinition
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Serializable mirror of spec 004's [EffectKind] sealed interface. */
@Serializable
sealed interface StatusEffectKindDto {
    @Serializable
    @SerialName("StatModifier")
    data class StatModifier(val statId: StatId, val delta: Int) : StatusEffectKindDto

    @Serializable
    @SerialName("Incapacitate")
    data object Incapacitate : StatusEffectKindDto

    @Serializable
    @SerialName("DamageOverTime")
    data class DamageOverTime(val amountPerTick: Int) : StatusEffectKindDto
}

fun StatusEffectKindDto.toCore(): EffectKind = when (this) {
    is StatusEffectKindDto.StatModifier -> EffectKind.StatModifier(statId, delta)
    StatusEffectKindDto.Incapacitate -> EffectKind.Incapacitate
    is StatusEffectKindDto.DamageOverTime -> EffectKind.DamageOverTime(amountPerTick)
}

/** Serializable mirror of spec 004's [StatusEffectDefinition]. */
@Serializable
data class StatusEffectDefinitionDto(
    val id: String,
    val displayName: String,
    val kind: StatusEffectKindDto,
    val duration: Int,
)

fun StatusEffectDefinitionDto.toCore(): StatusEffectDefinition =
    StatusEffectDefinition(StatusEffectId(id), displayName, kind.toCore(), duration)
