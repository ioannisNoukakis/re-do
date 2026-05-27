package me.noukakis.re_do.adapters.common.spring.rabbitmq.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import me.noukakis.re_do.common.model.TaskProgress

/**
 * Jackson mixin for [TaskProgress].
 *
 * Embeds a `kind` discriminator on the payload of a TEGTaskProgressMessage so the three variants
 * (indeterminate / bounded / llm_tokens) can be round-tripped over RabbitMQ.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = TaskProgress.Indeterminate::class, name = "indeterminate"),
    JsonSubTypes.Type(value = TaskProgress.Bounded::class, name = "bounded"),
    JsonSubTypes.Type(value = TaskProgress.LlmTokens::class, name = "llm_tokens"),
)
abstract class TaskProgressMixin
