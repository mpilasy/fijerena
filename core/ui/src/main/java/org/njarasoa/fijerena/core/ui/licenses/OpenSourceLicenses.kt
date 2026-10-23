package org.njarasoa.fijerena.core.ui.licenses

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A licence: its name, where it is published, and its text (blank when none was bundled). */
data class LicenseText(
    val name: String,
    val url: String?,
    val content: String,
)

/** One library or font the app ships, with its licences. */
data class OpenSourceEntry(
    val id: String,
    val name: String,
    val version: String?,
    val licenses: List<LicenseText>,
)

/**
 * Settings → About → Open-source licences: every library the app ships and the fonts it bundles,
 * from the list the AboutLibraries Gradle plugin writes at build time (`R.raw.aboutlibraries` of
 * each app; extra entries and licence texts in config/aboutlibraries). Read once per process.
 */
object OpenSourceLicenses {
    @Volatile
    private var cached: List<OpenSourceEntry>? = null

    /** The entries in [rawRes], by name. */
    suspend fun load(
        context: Context,
        @RawRes rawRes: Int,
    ): List<OpenSourceEntry> =
        cached ?: withContext(Dispatchers.IO) {
            parse(
                context.resources
                    .openRawResource(rawRes)
                    .bufferedReader()
                    .use { it.readText() },
            )
        }.also { cached = it }

    /** The id prefix of the app's own entry (config/aboutlibraries/libraries/fijerena.json): listed first. */
    const val APP_ID_PREFIX = "app:"

    /** The entries of a generated list: the app's own first, then by name; an unreadable one gives none. */
    fun parse(json: String): List<OpenSourceEntry> {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyList()
        val licenses =
            root["licenses"]?.let { it as? JsonObject }.orEmpty().mapValues { (id, value) ->
                val license = value.jsonObject
                LicenseText(
                    name = license.string("name") ?: id,
                    url = license.string("url"),
                    content = license.string("content").orEmpty(),
                )
            }
        val libraries = root["libraries"] as? JsonArray ?: return emptyList()
        return libraries
            .mapNotNull { element ->
                val library = element as? JsonObject ?: return@mapNotNull null
                val id = library.string("uniqueId") ?: return@mapNotNull null
                OpenSourceEntry(
                    id = id,
                    name = library.string("name") ?: id,
                    version = library.string("artifactVersion")?.takeIf { it.isNotBlank() },
                    licenses =
                        library["licenses"]
                            ?.jsonArray
                            .orEmpty()
                            .mapNotNull { licenses[it.jsonPrimitive.contentOrNull] },
                )
            }.sortedWith(compareBy<OpenSourceEntry> { !it.id.startsWith(APP_ID_PREFIX) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
