package me.noukakis.re_do.adapters.driving.scheduler.spring.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGArtefactType
import me.noukakis.re_do.common.model.TEGTask
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

data class TegTaskDTO(
    @field:NotBlank(message = "Task name must not be blank")
    val name: String?,

    @field:NotBlank(message = "Task implementation name must not be blank")
    val implementationName: String?,

    @field:NotNull(message = "Inputs must not be null")
    @field:Valid
    val inputs: List<TegArtefactDefinitionDTO>?,

    @field:NotNull(message = "Outputs must not be null")
    @field:Valid
    val outputs: List<TegArtefactDefinitionDTO>?,

    @field:NotNull(message = "Arguments must not be null")
    @field:Valid
    val arguments: List<String>?,

    @field:NotNull(message = "Timeout must not be null")
    @field:Valid
    val timeout: DurationDTO?,
) {
    fun toDomain(): TEGTask {
        return TEGTask(
            name = name!!,
            implementationName = implementationName!!,
            inputs = inputs!!.map { it.toDomain() },
            outputs = outputs!!.map { it.toDomain() },
            arguments = arguments!!,
            timeout = timeout!!.toDuration(),
        )
    }
}

data class TegArtefactDefinitionDTO(
    @field:NotBlank(message = "Artefact name must not be blank")
    val name: String?,

    @field:NotNull(message = "Artefact type must not be null")
    val type: TegArtefactTypeDTO?,
) {
    fun toDomain(): TEGArtefactDefinition {
        return TEGArtefactDefinition(
            name = name!!,
            type = when (type!!) {
                TegArtefactTypeDTO.STRING_VALUE -> TEGArtefactType.STRING_VALUE
                TegArtefactTypeDTO.FILE -> TEGArtefactType.FILE
            }
        )
    }
}

enum class TegArtefactTypeDTO {
    STRING_VALUE,
    FILE,
}

data class DurationDTO(
    @field:NotNull(message = "Amount must not be null")
    @field:Positive(message = "Amount must be positive")
    val amount: Long?,

    @field:NotNull(message = "Temporal unit must not be null")
    val temporalUnit: TemporalUnitDTO?,
) {
    fun toDuration(): Duration {
        val chronoUnit = when (temporalUnit!!) {
            TemporalUnitDTO.MILLIS -> ChronoUnit.MILLIS
            TemporalUnitDTO.SECONDS -> ChronoUnit.SECONDS
            TemporalUnitDTO.MINUTES -> ChronoUnit.MINUTES
            TemporalUnitDTO.HOURS -> ChronoUnit.HOURS
            TemporalUnitDTO.DAYS -> ChronoUnit.DAYS
        }
        return java.time.Duration.of(amount!!, chronoUnit).toKotlinDuration()
    }
}

enum class TemporalUnitDTO {
    MILLIS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
}