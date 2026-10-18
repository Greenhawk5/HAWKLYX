package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrentPriceModelTest {

    @Test
    fun brentPriceObservation_hasCorrectDefaultsAndProperties() {
        val obs = BrentPriceObservation(
            price = 82.50,
            formatted = "$82.50",
            asOf = "2026-10-18T10:00:00Z",
            fetchedAt = "2026-10-18T10:05:00Z"
        )

        assertEquals("BRENT_CRUDE_USD", obs.code)
        assertEquals("Brent Crude Oil", obs.name)
        assertEquals(82.50, obs.price, 0.001)
        assertEquals("USD", obs.currency)
        assertEquals("USD / barrel", obs.unit)
        assertEquals("$82.50", obs.formatted)
        assertFalse(obs.isStale)
        assertFalse(obs.isSynthetic)
        assertEquals("OilPriceAPI", obs.source)
    }

    @Test
    fun priceUiState_calculatesCooldownAndStatusProperly() {
        val state = PriceUiState(
            observation = null,
            status = DataFreshnessStatus.NO_DATA,
            message = "Initial state",
            isRefreshing = false,
            nextRefreshAllowedTimeMs = 1000L,
            cooldownRemainingSeconds = 45
        )

        assertEquals(DataFreshnessStatus.NO_DATA, state.status)
        assertEquals(45, state.cooldownRemainingSeconds)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun dataFreshnessStatus_coversAllRequiredSpecificationStates() {
        val values = DataFreshnessStatus.values().map { it.name }
        assertTrue(values.contains("FRESH"))
        assertTrue(values.contains("STALE"))
        assertTrue(values.contains("REFRESHING"))
        assertTrue(values.contains("PROVIDER_UNAVAILABLE"))
        assertTrue(values.contains("QUOTA_EXHAUSTED"))
        assertTrue(values.contains("NO_DATA"))
        assertTrue(values.contains("INVALID_RESPONSE"))
    }
}
