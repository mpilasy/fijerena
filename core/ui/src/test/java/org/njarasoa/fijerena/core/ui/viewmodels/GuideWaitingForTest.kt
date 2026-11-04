package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.ActiveSourceProgress
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager.MultiSourceState

/** A card that waits names the guide sources a refresh is downloading, importing or indexing now. */
class GuideWaitingForTest {
    private val provider = ProviderEntity(id = 1, name = "Xtream", url = "http://a", username = "u")
    private val cards =
        listOf(10L, 11L, 12L).map { id ->
            EpgManagementViewModel.GuideCard(
                EpgSourceEntity(id = id, url = "http://g/$id.xml", label = "G$id"),
                listOf(provider),
                null,
                true,
            )
        }

    @Test
    fun `while a refresh downloads or imports, a waiting card names those guide sources`() {
        val state =
            MultiSourceState.Processing(
                completedCount = 0,
                totalSources = 2,
                activeSourceLabels = listOf("G10"),
                activeProgress = mapOf(10L to ActiveSourceProgress(10, "G10", "Ingesting")),
            )

        val working = EpgManagementViewModel.workingOn(state)

        assertEquals(listOf("G10"), EpgManagementViewModel.waitingFor(cards, working, self = 11).map { it.label })
        // A card does not wait for itself.
        assertEquals(emptyList<String>(), EpgManagementViewModel.waitingFor(cards, working, self = 10).map { it.label })
    }

    @Test
    fun `while the guide index takes an import in, a waiting card names the guide sources it indexes`() {
        val state = MultiSourceState.Finalizing(phase = "Swapping to primary guide…", indexingSourceIds = setOf(10L, 12L))

        val working = EpgManagementViewModel.workingOn(state)

        assertEquals(listOf("G10", "G12"), EpgManagementViewModel.waitingFor(cards, working, self = 11).map { it.label })
    }

    @Test
    fun `with no refresh at work, a waiting card names nothing`() {
        assertEquals(emptySet<Long>(), EpgManagementViewModel.workingOn(MultiSourceState.Pending))
    }
}
