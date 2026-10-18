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

    @Test
    fun priceUiState_marketClosedEvaluation() {
        val openState = PriceUiState(
            status = DataFreshnessStatus.FRESH,
            marketState = ir.alifaniani.hawklyx.data.model.MarketStateModel(
                isOpen = true,
                status = "open",
                session = "regular_trading"
            )
        )
        assertFalse(openState.isMarketClosed)

        val closedByStatus = PriceUiState(
            status = DataFreshnessStatus.MARKET_CLOSED
        )
        assertTrue(closedByStatus.isMarketClosed)

        val closedByModel = PriceUiState(
            status = DataFreshnessStatus.FRESH,
            marketState = ir.alifaniani.hawklyx.data.model.MarketStateModel(
                isOpen = false,
                status = "closed",
                session = "weekend_closed"
            )
        )
        assertTrue(closedByModel.isMarketClosed)
    }

    @Test
    fun priceUiState_refreshActionEnablement_rules() {
        fun isRefreshActive(state: PriceUiState): Boolean {
            val isClosed = state.isMarketClosed
            val isCooldownActive = state.cooldownRemainingSeconds > 0
            return !isClosed && !state.isRefreshing && !isCooldownActive
        }

        // Open, idle, no cooldown -> enabled
        val readyState = PriceUiState(
            status = DataFreshnessStatus.FRESH,
            isRefreshing = false,
            cooldownRemainingSeconds = 0
        )
        assertTrue(isRefreshActive(readyState))

        // Market closed -> disabled
        val closedState = readyState.copy(status = DataFreshnessStatus.MARKET_CLOSED)
        assertFalse(isRefreshActive(closedState))

        // Syncing -> disabled
        val syncingState = readyState.copy(isRefreshing = true)
        assertFalse(isRefreshActive(syncingState))

        // Cooldown active -> disabled
        val cooldownState = readyState.copy(cooldownRemainingSeconds = 45)
        assertFalse(isRefreshActive(cooldownState))
    }

    @Test
    fun marketHistoryPriceColorResolver_followsSemanticMarketStateRules() {
        val validObs = BrentPriceObservation(
            price = 92.50,
            formatted = "$92.50",
            asOf = "2026-10-18T14:00:00Z",
            fetchedAt = "2026-10-18T14:05:00Z"
        )

        // 1. Market open and fresh -> HawklyxEmerald
        val openColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald, openColor)

        // 2. Market closed (by isMarketClosed flag) -> HawklyxAmber
        val closedFlagColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.FRESH,
            isMarketClosed = true
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxAmber, closedFlagColor)

        // 3. Market closed (by status) -> HawklyxAmber
        val closedStatusColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.MARKET_CLOSED,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxAmber, closedStatusColor)

        // 4. Stale quote -> HawklyxAmber
        val staleColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.STALE,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxAmber, staleColor)

        // 5. Offline / Provider unavailable -> HawklyxAmber
        val offlineColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxAmber, offlineColor)

        // 6. Quota exhausted -> HawklyxTextSecondary
        val quotaColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = validObs,
            status = DataFreshnessStatus.QUOTA_EXHAUSTED,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary, quotaColor)

        // 7. No data -> HawklyxTextMuted
        val noDataColor = ir.alifaniani.hawklyx.ui.components.MarketHistoryPriceColorResolver.resolve(
            observation = null,
            status = DataFreshnessStatus.NO_DATA,
            isMarketClosed = false
        )
        assertEquals(ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted, noDataColor)
    }
}

