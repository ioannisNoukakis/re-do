package me.noukakis.re_do.adapters.driving.scheduler.spring.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.constraints.NotBlank
import me.noukakis.re_do.scheduler.model.TEGArtefact

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TEGArtefactDTO.StringValueDTO::class, name = "STRING_VALUE"),
    JsonSubTypes.Type(value = TEGArtefactDTO.FileDTO::class, name = "FILE"),
)
sealed class TEGArtefactDTO {
    abstract fun toDomain(): TEGArtefact

    data class StringValueDTO(
        @field:NotBlank(message = "Artefact name must not be blank")
        val name: String?,
        @field:NotBlank(message = "Artefact value must not be blank")
        val value: String?,
    ) : TEGArtefactDTO() {
        override fun toDomain() = TEGArtefact.TEGArtefactStringValue(name!!, value!!)
    }

    data class FileDTO(
        @field:NotBlank(message = "Artefact name must not be blank")
        val name: String?,
        @field:NotBlank(message = "Artefact ref must not be blank")
        val ref: String?,
        @field:NotBlank(message = "Artefact storedWith must not be blank")
        val storedWith: String?,
    ) : TEGArtefactDTO() {
        override fun toDomain() = TEGArtefact.TEGArtefactFile(name!!, ref!!, storedWith!!)
    }
}