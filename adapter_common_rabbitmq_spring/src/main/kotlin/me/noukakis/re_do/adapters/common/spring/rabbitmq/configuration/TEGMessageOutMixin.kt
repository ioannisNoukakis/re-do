package me.noukakis.re_do.adapters.common.spring.rabbitmq.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlin.time.Duration
import me.noukakis.re_do.common.model.TEGMessageOut
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

/**
 * Jackson mixin for [TEGMessageOut].
 *
 * [TEGMessageOut] is a sealed interface that lives in the core domain and must remain free of
 * framework dependencies.  This mixin teaches Jackson how to serialize / deserialize it by
 * embedding a `type` discriminator field in the JSON payload.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TEGMessageOut.TEGRunTaskMessage::class, name = "run_task"),
)
abstract class TEGMessageOutMixin {

    /**
     * Mixin for [TEGMessageOut.TEGRunTaskMessage] that teaches Jackson to handle [Duration]
     * via [DurationSerializer] / [DurationDeserializer] without touching any other [Long] field.
     *
     * The annotation must be on an abstract getter (not a constructor parameter) so that
     * Jackson's mixin mechanism picks it up correctly.
     */
    abstract class TEGRunTaskMessageMixin {
        @get:JsonSerialize(using = DurationSerializer::class)
        @get:JsonDeserialize(using = DurationDeserializer::class)
        abstract val timeout: Duration
    }
}
