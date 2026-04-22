package org.njarasoa.fijerena.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonElement?.asString(): String? {
    if (this == null) return null
    if (this is JsonPrimitive) {
        return this.contentOrNull
    }
    return this.toString()
}

fun String?.toJsonPrimitive(): kotlinx.serialization.json.JsonPrimitive? {
    if (this == null) return null
    return kotlinx.serialization.json.JsonPrimitive(this)
}
