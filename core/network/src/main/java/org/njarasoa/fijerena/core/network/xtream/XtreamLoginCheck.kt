package org.njarasoa.fijerena.core.network.xtream

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.XtreamUserInfo

/**
 * What a source's Xtream panel says about one of its logins (`player_api.php`), and whether a new
 * login shares the source's catalogue. See docs/plans/20261005_shared-logins-plan.md.
 */
object XtreamLoginCheck {
    /** How many stream ids of the main login's catalogue a new login must also have, per type. */
    private const val SAMPLE_SIZE = 20

    /**
     * [active]: the panel lets the login play (`auth` 1, status "Active"); an expired, banned or
     * disabled login isn't. [expiresAtSec]: `exp_date` in Unix seconds, null when unlimited.
     * [activeCons] / [maxConnections]: streams open now and allowed, null when the panel omits them.
     */
    data class Status(
        val active: Boolean,
        val statusText: String,
        val expiresAtSec: Long?,
        val activeCons: Int?,
        val maxConnections: Int?,
    )

    sealed interface SamePanel {
        data class Ok(
            val status: Status,
        ) : SamePanel

        /** The panel didn't accept the username and password. */
        data object Refused : SamePanel

        /** The login works but isn't on the source's panel: its catalogue differs. */
        data object DifferentCatalogue : SamePanel

        /** The check couldn't finish (network, server error). */
        data class Failed(
            val error: Throwable,
        ) : SamePanel
    }

    fun statusOf(info: XtreamUserInfo): Status =
        Status(
            active = info.auth == 1 && info.status.equals("Active", ignoreCase = true),
            statusText = info.status,
            expiresAtSec = info.expDate?.toLongOrNull()?.takeIf { it > 0 },
            activeCons = info.activeCons?.toIntOrNull(),
            maxConnections = info.maxConnections?.toIntOrNull(),
        )

    /** [login]'s status on the panel at [url], or the failure. */
    suspend fun status(
        url: String,
        login: ProviderRepository.Login,
    ): Result<Status> =
        withService(url, login) { service ->
            suspendRunCatching { statusOf(service.authenticate().userInfo) }
        }

    /**
     * Whether [extra] can share [main]'s source at [url]: it signs in, and the first live and the
     * first VOD category of the main login's catalogue hold the same stream ids for it. Stream ids
     * belong to a panel, so matching ids mean a stream URL works with either login. The server
     * names aren't compared: one panel answers under several domains (bearsclub.online and
     * bearstv.online).
     */
    suspend fun samePanel(
        url: String,
        main: ProviderRepository.Login,
        extra: ProviderRepository.Login,
    ): SamePanel =
        withService(url, main) { mainService ->
            withService(url, extra) { extraService ->
                val signIn = suspendRunCatching { extraService.authenticate() }
                val error = signIn.exceptionOrNull()
                when {
                    error is SerializationException -> SamePanel.Refused
                    error != null -> SamePanel.Failed(error)
                    signIn.getOrThrow().userInfo.auth != 1 -> SamePanel.Refused
                    else -> compareCatalogues(mainService, extraService, statusOf(signIn.getOrThrow().userInfo))
                }
            }
        }

    private suspend fun compareCatalogues(
        mainService: XtreamApiService,
        extraService: XtreamApiService,
        status: Status,
    ): SamePanel {
        val samples =
            suspendRunCatching {
                coroutineScope {
                    val live =
                        async {
                            sample(mainService.getCategories().firstOrNull()?.categoryId, mainService::getStreams, extraService::getStreams)
                        }
                    val vod =
                        async {
                            sample(
                                mainService.getVodCategories().firstOrNull()?.categoryId,
                                mainService::getVodStreams,
                                extraService::getVodStreams,
                            )
                        }
                    listOfNotNull(live.await(), vod.await())
                }
            }
        return samples.fold(
            onSuccess = { pairs ->
                if (pairs.all { (mainIds, extraIds) ->
                        sharesCatalogue(mainIds, extraIds)
                    }
                ) {
                    SamePanel.Ok(status)
                } else {
                    SamePanel.DifferentCatalogue
                }
            },
            onFailure = { SamePanel.Failed(it) },
        )
    }

    /** The main login's ids in [categoryId] and the extra login's, or null without a category. */
    private suspend fun sample(
        categoryId: String?,
        mainStreams: suspend (String?) -> List<org.njarasoa.fijerena.core.player.model.XtreamStream>,
        extraStreams: suspend (String?) -> List<org.njarasoa.fijerena.core.player.model.XtreamStream>,
    ): Pair<List<Int>, Set<Int>>? =
        categoryId?.let { id ->
            mainStreams(id).map { it.streamId } to extraStreams(id).mapTo(HashSet()) { it.streamId }
        }

    /** The first [SAMPLE_SIZE] of [mainIds] all present in [extraIds]. An empty sample matches. */
    fun sharesCatalogue(
        mainIds: List<Int>,
        extraIds: Set<Int>,
    ): Boolean = mainIds.take(SAMPLE_SIZE).all { it in extraIds }

    private suspend fun <T> withService(
        url: String,
        login: ProviderRepository.Login,
        block: suspend (XtreamApiService) -> T,
    ): T {
        val service = XtreamApiService(url, login.username, login.password)
        return try {
            block(service)
        } finally {
            service.close()
        }
    }
}
