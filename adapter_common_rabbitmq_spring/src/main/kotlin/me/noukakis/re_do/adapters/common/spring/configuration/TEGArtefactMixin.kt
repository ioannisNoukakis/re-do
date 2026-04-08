package me.noukakis.re_do.adapters.common.spring.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import me.noukakis.re_do.scheduler.model.TEGArtefact

/**
 * Jackson mixin for [TEGArtefact].
 *
 * [TEGArtefact] is a sealed interface that lives in the core domain and must remain free of
 * framework dependencies.  This mixin teaches Jackson how to serialize / deserialize it by
 * embedding a `type` discriminator field in the JSON payload.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TEGArtefact.TEGArtefactFile::class, name = "file"),
    JsonSubTypes.Type(value = TEGArtefact.TEGArtefactStringValue::class, name = "string"),
)
abstract class TEGArtefactMixin

