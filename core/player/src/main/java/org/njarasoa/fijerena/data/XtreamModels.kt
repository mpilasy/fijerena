package org.njarasoa.fijerena.data

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class XtreamCategory(
    @SerialName("category_id") val id: String,
    @SerialName("category_name") val name: String
)

@kotlinx.serialization.Serializable
data class XtreamStream(
    @SerialName("stream_id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("direct_source") val directSource: String? = null
)
