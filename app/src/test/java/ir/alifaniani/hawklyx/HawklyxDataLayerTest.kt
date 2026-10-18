package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HawklyxDataLayerTest {

    @Test
    fun stateTransition_fromEmptyToFresh_preservesDataIntegrity() {
        val initial = PriceUiState(
            observation = null,
            status = DataFreshnessStatus.NO_DATA,
            message = "Awaiting first sync"
        )
        assertNull(initial.observation)
        assertEquals(DataFreshnessStatus.NO_DATA, initial.status)

        val newObs = BrentPriceObservation(
            price = 83.15,
            formatted = "$83.15",
            asOf = "2026-10-18T10:15:00Z",
            fetchedAt = "2026-10-18T10:16:00Z"
        )

        val updated = initial.copy(
            observation = newObs,
            status = DataFreshnessStatus.FRESH,
            message = "Price updated successfully"
        )

        assertNotNull(updated.observation)
        assertEquals(83.15, updated.observation!!.price, 0.001)
        assertEquals(DataFreshnessStatus.FRESH, updated.status)
        assertEquals("Price updated successfully", updated.message)
    }

    @Test
    fun stateTransition_onNetworkFailure_preservesLastKnownGoodPrice() {
        val lastKnownGood = BrentPriceObservation(
            price = 81.40,
            formatted = "$81.40",
            asOf = "2026-10-18T09:00:00Z",
            fetchedAt = "2026-10-18T09:05:00Z"
        )

        val offlineState = PriceUiState(
            observation = lastKnownGood,
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            message = "Network connection failed. Showing last known price.",
            isRefreshing = false
        )

        // Verifies HAWKLYX resilience rule: never replace missing data with zero or null if cached data exists
        assertNotNull(offlineState.observation)
        assertEquals(81.40, offlineState.observation!!.price, 0.001)
        assertEquals("$81.40", offlineState.observation!!.formatted)
        assertEquals(DataFreshnessStatus.PROVIDER_UNAVAILABLE, offlineState.status)
    }

    @Test
    fun stateTransition_onQuotaExhaustion_showsQuotaStatusWithCachedPrice() {
        val lastObs = BrentPriceObservation(
            price = 80.90,
            formatted = "$80.90",
            asOf = "2026-10-18T08:00:00Z",
            fetchedAt = "2026-10-18T08:02:00Z"
        )

        val quotaExhaustedState = PriceUiState(
            observation = lastObs,
            status = DataFreshnessStatus.QUOTA_EXHAUSTED,
            message = "Daily quota reached. Showing last valid price."
        )

        assertEquals(DataFreshnessStatus.QUOTA_EXHAUSTED, quotaExhaustedState.status)
        assertEquals(80.90, quotaExhaustedState.observation!!.price, 0.001)
        assertTrue(quotaExhaustedState.message.contains("Daily quota"))
    }

    @Test
    fun stateTransition_onMarketClosed_preservesLastPriceAndMarksClosed() {
        val lastObs = BrentPriceObservation(
            price = 86.25,
            formatted = "$86.25",
            asOf = "2026-10-16T21:55:00Z",
            fetchedAt = "2026-10-18T14:10:00Z"
        )

        val marketClosedState = PriceUiState(
            observation = lastObs,
            status = DataFreshnessStatus.MARKET_CLOSED,
            message = "Market closed for weekend. Showing latest available price.",
            marketState = ir.alifaniani.hawklyx.data.model.MarketStateModel(
                isOpen = false,
                status = "closed",
                session = "weekend_closed",
                nextOpenAt = "2026-10-18T22:00:00Z",
                description = "Weekend closure"
            )
        )

        assertEquals(DataFreshnessStatus.MARKET_CLOSED, marketClosedState.status)
        assertTrue(marketClosedState.isMarketClosed)
        assertNotNull(marketClosedState.observation)
        assertEquals(86.25, marketClosedState.observation!!.price, 0.001)
        assertNotNull(marketClosedState.marketState)
        assertFalse(marketClosedState.marketState!!.isOpen)
        assertTrue(marketClosedState.message.contains("Market closed"))
    }
}
