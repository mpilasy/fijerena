package org.njarasoa.fijerena.core.ui.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.profile.SwitchLock
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCategoryExclusionSync
import org.njarasoa.fijerena.core.player.domain.ContentType

/** What a TV can do with a [SyncPayloads.PlayRequest]. */
sealed interface PlayResolution {
    /** The TV can open [route], for [profileId]. */
    data class Playable(
        val route: Screen.Player,
        val profileId: String,
        val profileName: String,
    ) : PlayResolution

    /** The TV refuses the request with [result], one of the [PlayAnswer] codes. */
    data class Refused(
        val result: String,
    ) : PlayResolution
}

/**
 * Checks a request on the target TV and builds the player route (the send to TV plan, ST-D7 and
 * ST-D8): the source exists on this TV, the profile exists and has that source on, and the
 * profile's content filters and Parental controls allow the item, with the same rules as the lists.
 */
class PlayRequestResolver(
    private val facts: Facts,
) {
    /** The checks on this TV's data: a seam for the tests. */
    interface Facts {
        suspend fun providerId(providerKey: String): Long?

        suspend fun profileName(profileId: String): String?

        suspend fun profileHasSource(
            profileId: String,
            providerId: Long,
        ): Boolean

        /** This TV can change to [profileId] with no PIN: a child lock in force refuses it. */
        suspend fun canSwitchTo(profileId: String): Boolean

        suspend fun allowed(
            profileId: String,
            providerId: Long,
            request: SyncPayloads.PlayRequest,
        ): Boolean
    }

    suspend fun resolve(request: SyncPayloads.PlayRequest): PlayResolution {
        val providerId = facts.providerId(request.providerKey)
        val profileName = facts.profileName(request.profileId)
        return when {
            providerId == null -> PlayResolution.Refused(PlayAnswer.NO_SOURCE)
            profileName == null -> PlayResolution.Refused(PlayAnswer.NO_PROFILE)
            !facts.profileHasSource(request.profileId, providerId) -> PlayResolution.Refused(PlayAnswer.NO_SOURCE)
            !facts.canSwitchTo(request.profileId) -> PlayResolution.Refused(PlayAnswer.NOT_ALLOWED)
            !facts.allowed(request.profileId, providerId, request) -> PlayResolution.Refused(PlayAnswer.NOT_ALLOWED)
            else -> PlayResolution.Playable(routeOf(request, providerId), request.profileId, profileName)
        }
    }

    companion object {
        /** The player route for [request] on the source [providerId]; live has no position. */
        fun routeOf(
            request: SyncPayloads.PlayRequest,
            providerId: Long,
        ) = Screen.Player(
            streamId = request.streamId,
            streamName = request.streamName,
            categoryId = request.categoryId,
            contentType = request.contentType,
            episodeId = request.episodeId,
            episodeExtension = request.episodeExtension,
            seriesId = request.seriesId,
            seriesName = request.seriesName,
            providerId = providerId,
            startPositionMs = request.positionMs?.takeIf { request.contentType != ContentType.LIVE_TV },
        )

        /**
         * The rules of the lists for one item: a hidden title stays out for a child; a category the
         * content filters hide stays out, unless a Parental controls exception lets a child see
         * the title. [categoryName] null: this TV does not know the category. A grown-up can open
         * it, as the catalogue may not be loaded yet; a child cannot ([knownCatalogue]).
         */
        fun itemAllowed(
            filters: CategoryFilters,
            categoryName: String?,
            films: Boolean,
            titles: List<String>,
            knownCatalogue: Boolean,
        ): Boolean {
            val child = filters.child
            return when {
                child != null && titles.any { child.hidesTitle(it) } -> {
                    false
                }

                categoryName == null -> {
                    child == null || !knownCatalogue
                }

                filters.shouldShowCategory(categoryName, films) -> {
                    true
                }

                else -> {
                    child != null &&
                        XtreamCategoryExclusionSync.exceptionsReach(filters, child, categoryName, films) &&
                        titles.any { child.allowsTitle(it) }
                }
            }
        }

        /** The real checks, on this TV's databases. */
        fun forDevice(context: Context): PlayRequestResolver = PlayRequestResolver(DeviceFacts(context.applicationContext))
    }
}

private class DeviceFacts(
    private val context: Context,
) : PlayRequestResolver.Facts {
    private val settingsDb get() = SettingsDatabase.getInstance(context)

    override suspend fun providerId(providerKey: String): Long? = settingsDb.settingsSyncDao().providerByKey(providerKey)?.id

    override suspend fun profileName(profileId: String): String? =
        settingsDb
            .profileDao()
            .getAll()
            .firstOrNull { it.id == profileId }
            ?.name

    override suspend fun profileHasSource(
        profileId: String,
        providerId: Long,
    ): Boolean = ProviderRepository(context).profileSourcesList(profileId).any { it.id == providerId }

    override suspend fun canSwitchTo(profileId: String): Boolean =
        ProfileRepository(context).needsAdminPinToSwitch(profileId) is SwitchLock.Free

    override suspend fun allowed(
        profileId: String,
        providerId: Long,
        request: SyncPayloads.PlayRequest,
    ): Boolean {
        val provider = settingsDb.providerDao().getProviderById(providerId)
        val filters = CategoryFiltersStore(context).get(providerId, profileId)
        val xtream = provider?.type == "XTREAM"
        val titles = listOfNotNull(request.streamName, request.seriesName, request.title).filter { it.isNotBlank() }
        val type =
            when (request.contentType) {
                ContentType.MOVIES -> XtreamCategoryEntity.TYPE_VOD
                ContentType.TV_SHOWS -> XtreamCategoryEntity.TYPE_SERIES
                else -> XtreamCategoryEntity.TYPE_LIVE
            }
        val categoryName = if (xtream) categoryName(providerId, type, request) else null
        return PlayRequestResolver.itemAllowed(
            filters,
            categoryName,
            films = type == XtreamCategoryEntity.TYPE_VOD,
            titles,
            knownCatalogue = xtream,
        )
    }

    /** The category's name on this TV, from the id the phone sent or, when blank, the catalogue's. */
    private suspend fun categoryName(
        providerId: Long,
        type: String,
        request: SyncPayloads.PlayRequest,
    ): String? =
        withContext(Dispatchers.IO) {
            val dao = XtreamDatabase.getInstance(context).categoryDao()
            val categoryId = request.categoryId.ifBlank { catalogueCategoryId(providerId, type, request).orEmpty() }
            dao.getAllCategoriesIncludingExcluded(providerId, type).firstOrNull { it.categoryId == categoryId }?.categoryName
        }

    private fun catalogueCategoryId(
        providerId: Long,
        type: String,
        request: SyncPayloads.PlayRequest,
    ): String? {
        val dao = XtreamDatabase.getInstance(context).categoryDao()
        val series = type == XtreamCategoryEntity.TYPE_SERIES
        val id = (if (series) request.seriesId else request.streamId)?.toIntOrNull()
        return when {
            id == null -> null
            series -> dao.getSeriesCategoryIds(providerId, listOf(id))[id]
            else -> dao.getStreamCategoryIds(providerId, type, listOf(id))[id]
        }
    }
}
