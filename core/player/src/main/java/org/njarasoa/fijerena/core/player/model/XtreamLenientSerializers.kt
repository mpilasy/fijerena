@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Panels send a number as a number, as text ("12") or as "" (no value), and an empty list or object
// as [] or {}. Json's own number and list readers fail on "" and on the wrong bracket, and one such
// field failed the whole list, a film's details, a show's episodes or the login. These serializers
// read what is usable and give "no value" for the rest. See the provider data tests plan, Phase 3.

/** The text of a number field: null when the field is null, an object or a list. */
private fun numberText(decoder: Decoder): String? =
    ((decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive)
        ?.takeUnless { it is JsonNull }
        ?.content
        ?.trim()

/** An Int sent as a number or as text; 0 when it is "", null or not a number. */
object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XtreamLenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int = numberText(decoder)?.toIntOrNull() ?: 0

    override fun serialize(
        encoder: Encoder,
        value: Int,
    ) = encoder.encodeInt(value)
}

/** An Int sent as a number or as text; null when it is "", null or not a number. */
object LenientIntOrNullSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XtreamLenientIntOrNull", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? = numberText(decoder)?.toIntOrNull()

    override fun serialize(
        encoder: Encoder,
        value: Int?,
    ) = encoder.encodeNullableSerializableValue(Int.serializer(), value)
}

/** A Long sent as a number or as text; null when it is "", null or not a number. */
object LenientLongOrNullSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XtreamLenientLongOrNull", PrimitiveKind.LONG).nullable

    override fun deserialize(decoder: Decoder): Long? = numberText(decoder)?.toLongOrNull()

    override fun serialize(
        encoder: Encoder,
        value: Long?,
    ) = encoder.encodeNullableSerializableValue(Long.serializer(), value)
}

/** A Double sent as a number or as text; null when it is "", null or not a number. */
object LenientDoubleOrNullSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XtreamLenientDoubleOrNull", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? = numberText(decoder)?.toDoubleOrNull()

    override fun serialize(
        encoder: Encoder,
        value: Double?,
    ) = encoder.encodeNullableSerializableValue(Double.serializer(), value)
}

/**
 * A list that a panel may send as a list, as an object (`{}` when it is empty; its values are the
 * items), as one item where the list belongs (`"backdrop_path": "https://…"`) or as "".
 */
open class LenientListSerializer<T>(
    private val element: KSerializer<T>,
) : KSerializer<List<T>> {
    private val list = ListSerializer(element)

    override val descriptor: SerialDescriptor = list.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder
        return jsonDecoder
            ?.let { json -> listItems(json.decodeJsonElement()).map { json.json.decodeFromJsonElement(element, it) } }
            ?: list.deserialize(decoder)
    }

    override fun serialize(
        encoder: Encoder,
        value: List<T>,
    ) = list.serialize(encoder, value)
}

private fun listItems(element: JsonElement): Collection<JsonElement> =
    when (element) {
        is JsonArray -> element
        is JsonObject -> element.values
        is JsonPrimitive -> if (element is JsonNull || element.content.isBlank()) emptyList() else listOf(element)
    }

/** A list of texts (picture URLs, output formats); see [LenientListSerializer]. */
object LenientTextListSerializer : LenientListSerializer<String?>(String.serializer().nullable)

/** The seasons of a show; see [LenientListSerializer]. */
object LenientSeasonListSerializer : LenientListSerializer<Season?>(Season.serializer().nullable)

/** An object that a panel sends as `[]` (or anything else that is not an object) when it has none. */
open class ObjectOrNullSerializer<T : Any>(
    private val inner: KSerializer<T>,
) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = inner.descriptor.nullable

    override fun deserialize(decoder: Decoder): T? {
        val jsonDecoder = decoder as? JsonDecoder
        return if (jsonDecoder == null) {
            inner.deserialize(decoder)
        } else {
            (jsonDecoder.decodeJsonElement() as? JsonObject)?.let { jsonDecoder.json.decodeFromJsonElement(inner, it) }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: T?,
    ) = encoder.encodeNullableSerializableValue(inner, value)
}

/** A film's `info`; see [ObjectOrNullSerializer]. */
object MovieInfoOrNullSerializer : ObjectOrNullSerializer<MovieInfo>(MovieInfo.serializer())

/** A film's `movie_data`; see [ObjectOrNullSerializer]. */
object MovieDataOrNullSerializer : ObjectOrNullSerializer<MovieData>(MovieData.serializer())

/** A show's `info`; see [ObjectOrNullSerializer]. */
object SeriesDetailsOrNullSerializer : ObjectOrNullSerializer<SeriesDetails>(SeriesDetails.serializer())
