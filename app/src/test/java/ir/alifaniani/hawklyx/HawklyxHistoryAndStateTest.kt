package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
import ir.alifaniani.hawklyx.data.model.PriceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HawklyxHistoryAndStateTest {

    @Test
    fun priceUiState_containsAllEnhancedFields() {
        val historyPoint1 = HistoricalObservationPoint(
            timestampMs = 1771459200000L,
            price = 108.50,
            formatted = "$108.50",
            asOfIso = "2026-10-18T13:45:00Z"
        )
        val historyPoint2 = HistoricalObservationPoint(
            timestampMs = 1771460100000L,
            price = 109.95,
            formatted = "$109.95",
            asOfIso = "2026-10-18T14:00:00Z"
        )

        val state = PriceUiState(
            observation = BrentPriceObservation(
                price = 109.95,
                formatted = "$109.95",
                asOf = "2026-10-18T14:00:00Z",
                fetchedAt = "2026-10-18T14:02:00Z"
            ),
            status = DataFreshnessStatus.FRESH,
            message = "Price updated successfully",
            isRefreshing = false,
            nextRefreshAllowedTimeMs = 1771460280000L,
            cooldownRemainingSeconds = 120,
            nextScheduledCadenceTimeMs = 1771461000000L,
            cadenceRemainingSeconds = 720,
            history = listOf(historyPoint1, historyPoint2),
            lastSyncAttemptTimeMs = 1771460100000L,
            lastSyncSuccess = true
        )

        assertEquals(2, state.history.size)
        assertEquals(120, state.cooldownRemainingSeconds)
        assertEquals(720, state.cadenceRemainingSeconds)
        assertTrue(state.lastSyncSuccess)
        assertNotNull(state.observation)
        assertEquals(109.95, state.observation!!.price, 0.001)
    }

    @Test
    fun historicalPoint_retainsExactValues() {
        val pt = HistoricalObservationPoint(
            timestampMs = 1729260000000L,
            price = 74.29,
            formatted = "$74.29",
            asOfIso = "2026-10-18T14:00:00.000Z"
        )
        assertEquals(1729260000000L, pt.timestampMs)
        assertEquals(74.29, pt.price, 0.001)
        assertEquals("$74.29", pt.formatted)
        assertEquals("2026-10-18T14:00:00.000Z", pt.asOfIso)
    }
}
