package me.noukakis.re_do.adapters.common.spring.configuration

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.ser.std.StdSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wire representation of [Duration] used in JSON payloads.
 *
 * Wrapping the raw [Long] in a named data class avoids a broad override of how every [Long] is
 * handled by Jackson; only fields explicitly annotated with [DurationSerializer] /
 * [DurationDeserializer] are affected.
 */
data class SerializedKotlinDuration(val millis: Long)

/** Serializes a [Duration] as a [SerializedKotlinDuration] JSON object, e.g. `{"millis":5000}`. */
class DurationSerializer : StdSerializer<Duration>(Duration::class.java) {
    override fun serialize(value: Duration, gen: JsonGenerator, provider: SerializationContext) {
        gen.writeStartObject()
        gen.writeName("millis")
        gen.writeNumber(value.inWholeMilliseconds)
        gen.writeEndObject()
    }
}

/** Deserializes a [SerializedKotlinDuration] JSON object back into a [Duration]. */
class DurationDeserializer : StdDeserializer<Duration>(Duration::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Duration {
        val node = p.readValueAsTree<JsonNode>()
        return node.get("millis").longValue().milliseconds
    }
}
