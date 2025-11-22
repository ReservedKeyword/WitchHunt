package com.reservedkeyword.witchhunt.utils

import com.google.gson.*
import java.lang.reflect.Type
import java.time.Instant

class InstantAdapter : JsonDeserializer<Instant>, JsonSerializer<Instant> {
    override fun deserialize(
        json: JsonElement?,
        typeOf: Type?,
        context: JsonDeserializationContext?
    ): Instant {
        return Instant.parse(json?.asString ?: throw JsonParseException("Invalid Instant format detected"))
    }

    override fun serialize(
        src: Instant?,
        typeOf: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.toString() ?: throw JsonParseException("Instant cannot be null"))
    }
}
