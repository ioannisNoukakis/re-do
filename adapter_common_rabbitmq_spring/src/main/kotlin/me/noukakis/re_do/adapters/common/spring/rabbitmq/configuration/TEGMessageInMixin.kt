package me.noukakis.re_do.adapters.common.spring.rabbitmq.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import me.noukakis.re_do.common.model.TEGMessageIn

/**
 * Jackson mixin for [TEGMessageIn].
 *
 * [TEGMessageIn] is a sealed interface that lives in the core domain and must remain free of
 * framework dependencies.  This mixin teaches Jackson how to serialize / deserialize it by
 * embedding a `type` discriminator field in the JSON payload.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TEGMessageIn.TEGTaskResultMessage::class, name = "task_result"),
    JsonSubTypes.Type(value = TEGMessageIn.TEGTaskFailedMessage::class, name = "task_failed"),
    JsonSubTypes.Type(value = TEGMessageIn.TEGTaskProgressMessage::class, name = "task_progress"),
    JsonSubTypes.Type(value = TEGMessageIn.TEGTaskLogMessage::class, name = "task_log"),
)
abstract class TEGMessageInMixin

