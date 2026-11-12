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
                val version = library.string("artifactVersion")?.takeIf { it.isNotBlank() }
                val override = library.string("tag") == LICENCE_OVERRIDE_TAG
                val licenseIds = library["licenses"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
                // A correction for a library this app does not ship comes without a version: leave it out.
                if (override && version == null) return@mapNotNull null
                OpenSourceEntry(
                    id = id,
                    name = library.string("name") ?: id,
                    version = version,
                    licenses = (if (override) withoutGeneric(licenseIds) else licenseIds).mapNotNull { licenses[it] },
                )
            }.sortedWith(compareBy<OpenSourceEntry> { !it.id.startsWith(APP_ID_PREFIX) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /**
     * The tag of a config/aboutlibraries entry that corrects a dependency's licences: the plugin
     * merges it into the dependency and adds its licences to those of the POM. Its own licences
     * (`MIT-slf4j`, with the library's copyright line) replace the generic ones (`MIT`, a template).
     * In an app that does not ship the dependency, the plugin adds it alone, with no version.
     */
    const val LICENCE_OVERRIDE_TAG = "licence-override"

    /** [ids] without each generic licence that has a specific one (`MIT` when `MIT-slf4j` is there). */
    private fun withoutGeneric(ids: List<String>): List<String> = ids.filterNot { id -> ids.any { it.startsWith("$id-") } }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
