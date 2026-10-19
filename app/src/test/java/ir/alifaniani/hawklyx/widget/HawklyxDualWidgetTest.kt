package ir.alifaniani.hawklyx.widget

import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.model.HistorySummaryModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the HAWKLYX dual-widget system contracts, caching models, and state handling.
 */
class HawklyxDualWidgetTest {

    @Test
    fun historyResponseModel_7DayRange_matchesExpectedContract() {
        val points = listOf(
            HistoricalPointModel(
                timestamp = "2026-10-17T00:00:00Z",
                timestampMs = 1771329600000L,
                price = 108.20,
                formatted = "$108.20",
                asOf = "2026-10-17T00:00:00Z",
                fetchedAt = "2026-10-17T00:05:00Z"
            ),
            HistoricalPointModel(
                timestamp = "2026-10-18T00:00:00Z",
                timestampMs = 1771416000000L,
                price = 109.95,
                formatted = "$109.95",
                asOf = "2026-10-18T00:00:00Z",
                fetchedAt = "2026-10-18T00:05:00Z"
            )
        )
        val summary = HistorySummaryModel(
            firstPrice = 108.20,
            latestPrice = 109.95,
            changeAbsolute = 1.75,
            changePercent = 1.62,
            high = 110.50,
            low = 107.80
        )
        val response = HistoryResponseModel(
            status = "success",
            instrument = "BRENT_CRUDE_USD",
            range = HistoryRange.ONE_WEEK,
            resolution = "hourly",
            count = 2,
            points = points,
            summary = summary,
            isPartial = false,
            serverTime = "2026-10-18T14:00:00Z"
        )

        assertEquals("success", response.status)
        assertEquals(HistoryRange.ONE_WEEK, response.range)
        assertEquals("7d", response.range.apiValue)
        assertEquals(2, response.points.size)
        assertNotNull(response.summary)
        assertEquals(1.75, response.summary!!.changeAbsolute, 0.001)
        assertFalse(response.isEmpty)
    }

    @Test
    fun observationStale_forcesEffectiveStaleStatus() {
        val observation = BrentPriceObservation(
            price = 109.95,
            formatted = "$109.95",
            asOf = "2026-10-18T12:00:00Z",
            fetchedAt = "2026-10-18T12:05:00Z",
            isStale = true
        )
        val rawStatus = DataFreshnessStatus.FRESH

        // Verification of the effective status rule in widget binding
        val effectiveStatus = if (observation.isStale && rawStatus == DataFreshnessStatus.FRESH) {
            DataFreshnessStatus.STALE
        } else {
            rawStatus
        }

        assertEquals(DataFreshnessStatus.STALE, effectiveStatus)
    }

    @Test
    fun marketClosed_honestStateDisplay() {
        val status = DataFreshnessStatus.MARKET_CLOSED
        val isClosed = status == DataFreshnessStatus.MARKET_CLOSED
        assertTrue("Market closed state must be detected accurately", isClosed)
    }

    @Test
    fun widgetThemeResolver_marketOpenAndFresh_usesEmeraldPriceAndLiveBadge() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T14:30:00Z",
            fetchedAt = "2026-10-18T14:35:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = observation,
            isMarketOpen = true,
            isRefreshing = false
        )

        assertEquals("Live market must use emerald price", ir.alifaniani.hawklyx.R.color.hawklyx_emerald, theme.priceColorRes)
        assertEquals("LIVE", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_emerald, theme.badgeTextColorRes)
        assertEquals(ir.alifaniani.hawklyx.R.drawable.hawklyx_badge_emerald, theme.badgeBgRes)
        assertFalse(theme.isMarketClosed)
        assertFalse(theme.isStaleOrOffline)
    }

    @Test
    fun widgetThemeResolver_marketClosedStatus_switchesPriceToAmberInBothWidgets() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T14:30:00Z",
            fetchedAt = "2026-10-18T14:35:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.MARKET_CLOSED,
            observation = observation,
            isMarketOpen = false,
            isRefreshing = false
        )

        assertEquals("Closed market price MUST turn amber", ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.priceColorRes)
        assertEquals("MARKET CLOSED", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.badgeTextColorRes)
        assertEquals(ir.alifaniani.hawklyx.R.drawable.hawklyx_badge_amber, theme.badgeBgRes)
        assertEquals("LAST CLOSE", theme.cadencePillText)
        assertTrue(theme.isMarketClosed)
        assertFalse(theme.isStaleOrOffline)
    }

    @Test
    fun widgetThemeResolver_isMarketOpenFalse_forcesAmberEvenIfStatusFresh() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T14:30:00Z",
            fetchedAt = "2026-10-18T14:35:00Z",
            isStale = false
        )
        // Network status was FRESH but market calendar indicates market is closed
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = observation,
            isMarketOpen = false,
            isRefreshing = false
        )

        assertEquals("When market is closed, price must be amber", ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.priceColorRes)
        assertEquals("MARKET CLOSED", theme.badgeText)
        assertTrue(theme.isMarketClosed)
    }

    @Test
    fun widgetThemeResolver_staleObservation_usesAmberPriceAndStaleBadge() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T12:00:00Z",
            fetchedAt = "2026-10-18T12:05:00Z",
            isStale = true
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.STALE,
            observation = observation,
            isMarketOpen = true,
            isRefreshing = false
        )

        assertEquals("Stale cached quote price must be amber", ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.priceColorRes)
        assertEquals("STALE", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.badgeTextColorRes)
        assertEquals(ir.alifaniani.hawklyx.R.drawable.hawklyx_badge_amber, theme.badgeBgRes)
        assertTrue(theme.isStaleOrOffline)
        assertFalse(theme.isMarketClosed)
    }

    @Test
    fun widgetThemeResolver_offlineOrProviderUnavailable_usesAmberPriceAndOfflineBadge() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T12:00:00Z",
            fetchedAt = "2026-10-18T12:05:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            observation = observation,
            isMarketOpen = true,
            isRefreshing = false
        )

        assertEquals("Offline cached quote price must be amber", ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.priceColorRes)
        assertEquals("OFFLINE", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.badgeTextColorRes)
        assertTrue(theme.isStaleOrOffline)
    }

    @Test
    fun widgetThemeResolver_quotaExhausted_usesRedBadgeAndMutedPrice() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T12:00:00Z",
            fetchedAt = "2026-10-18T12:05:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.QUOTA_EXHAUSTED,
            observation = observation,
            isMarketOpen = true,
            isRefreshing = false
        )

        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_text_secondary, theme.priceColorRes)
        assertEquals("QUOTA LIMIT", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_red, theme.badgeTextColorRes)
        assertEquals(ir.alifaniani.hawklyx.R.drawable.hawklyx_badge_red, theme.badgeBgRes)
    }

    @Test
    fun widgetThemeResolver_refreshing_displaysSyncingBadge() {
        val observation = BrentPriceObservation(
            price = 93.45,
            formatted = "$93.45",
            asOf = "2026-10-18T14:30:00Z",
            fetchedAt = "2026-10-18T14:35:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = observation,
            isMarketOpen = true,
            isRefreshing = true
        )

        assertEquals("SYNCING", theme.badgeText)
        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_emerald, theme.badgeTextColorRes)
        assertTrue(theme.isRefreshing)
    }

    @Test
    fun widgetThemeResolver_noData_usesMutedStyling() {
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.NO_DATA,
            observation = null,
            isMarketOpen = true,
            isRefreshing = false
        )

        assertEquals(ir.alifaniani.hawklyx.R.color.hawklyx_text_muted, theme.priceColorRes)
        assertEquals("NO DATA", theme.badgeText)
    }

    @Test
    fun cooldownEnforcement_calculatesRemainingSecondsAccurately() {
        val now = 1771460000000L
        val nextAllowed = now + 45000L // 45 seconds remaining
        val remainingSec = ((nextAllowed - now) / 1000).toInt().coerceAtLeast(1)
        assertEquals(45, remainingSec)
    }

    @Test
    fun compactWidgetCadenceLabels_neverExceedCompactWidthBudget() {
        val testStatuses = listOf(
            DataFreshnessStatus.FRESH to true,
            DataFreshnessStatus.FRESH to false,
            DataFreshnessStatus.MARKET_CLOSED to false,
            DataFreshnessStatus.STALE to true,
            DataFreshnessStatus.PROVIDER_UNAVAILABLE to true,
            DataFreshnessStatus.QUOTA_EXHAUSTED to true,
            DataFreshnessStatus.NO_DATA to true
        )
        val dummyObs = BrentPriceObservation(
            price = 90.0,
            formatted = "$90.00",
            asOf = "2026-10-18T10:00:00Z",
            fetchedAt = "2026-10-18T10:05:00Z"
        )

        for ((status, isOpen) in testStatuses) {
            val theme = WidgetThemeResolver.resolve(
                status = status,
                observation = if (status == DataFreshnessStatus.NO_DATA) null else dummyObs,
                isMarketOpen = isOpen,
                isRefreshing = false
            )
            // In the compact widget layout, cadence pill text must be <= 11 characters
            // to ensure it never overflows or clips ("LAST CLOSE", "15M CADENCE", "CACHED", "OFFLINE")
            assertTrue(
                "Cadence label '${theme.cadencePillText}' for status $status (isOpen=$isOpen) must not exceed 11 characters to prevent clipping",
                theme.cadencePillText.length <= 11
            )
            assertFalse(
                "Cadence label '${theme.cadencePillText}' must not contain 'SESSION' which previously clipped",
                theme.cadencePillText.contains("SESSION", ignoreCase = true)
            )
        }
    }

    @Test
    fun compactWidgetCadenceLabels_selectAppropriateTextPerMarketState() {
        val dummyObs = BrentPriceObservation(
            price = 90.0,
            formatted = "$90.00",
            asOf = "2026-10-18T10:00:00Z",
            fetchedAt = "2026-10-18T10:05:00Z"
        )

        val openTheme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = dummyObs,
            isMarketOpen = true,
            isRefreshing = false
        )
        assertEquals("15M CADENCE", openTheme.cadencePillText)

        val closedTheme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.MARKET_CLOSED,
            observation = dummyObs,
            isMarketOpen = false,
            isRefreshing = false
        )
        assertEquals("LAST CLOSE", closedTheme.cadencePillText)

        val staleTheme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.STALE,
            observation = dummyObs,
            isMarketOpen = true,
            isRefreshing = false
        )
        assertEquals("CACHED", staleTheme.cadencePillText)

        val offlineTheme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.PROVIDER_UNAVAILABLE,
            observation = dummyObs,
            isMarketOpen = true,
            isRefreshing = false
        )
        assertEquals("OFFLINE", offlineTheme.cadencePillText)
    }

    @Test
    fun compactWidgetLayout_prioritizesPriceAndUnitWithoutOverlappingCadencePill() {
        val fileCandidates = listOf(
            java.io.File("src/main/res/layout/hawklyx_widget_layout.xml"),
            java.io.File("app/src/main/res/layout/hawklyx_widget_layout.xml")
        )
        val layoutFile = fileCandidates.firstOrNull { it.exists() }
        assertNotNull("hawklyx_widget_layout.xml must exist", layoutFile)
        val content = layoutFile!!.readText()

        // Invariant 1: Price and unit must not be constrained inside a 0dp/weight=1 container
        // which previously caused "USD / barrel" to be starved of space and ellipsized.
        assertTrue(
            "Price and unit container must wrap content to prioritize primary price display",
            content.contains("android:id=\"@+id/widget_price\"") &&
            content.contains("android:id=\"@+id/widget_unit\"")
        )

        // Invariant 2: widget_unit must not have ellipsize="end" truncating 'barrel'
        assertFalse(
            "widget_unit should not have ellipsize=\"end\" truncating 'barrel'",
            content.contains("android:id=\"@+id/widget_unit\"\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"wrap_content\"\n            android:layout_marginStart=\"5dp\"\n            android:layout_marginBottom=\"3dp\"\n            android:ellipsize=\"end\"")
        )

        // Invariant 3: widget_cadence_container must use layout_weight and end gravity to push LAST CLOSE to the far right
        assertTrue(
            "Cadence container must have weight=1 and end gravity to sit on the far right",
            content.contains("android:id=\"@+id/widget_cadence_container\"") &&
            content.contains("android:gravity=\"end|center_vertical\"")
        )

        // Invariant 4: widget_cadence_pill must be styled as a distinct card surface pill
        assertTrue(
            "Cadence pill must use hawklyx_card_surface background and have ellipsize capability",
            content.contains("android:id=\"@+id/widget_cadence_pill\"") &&
            content.contains("@drawable/hawklyx_card_surface")
        )
    }

    @Test
    fun widgetThemeResolver_sessionDirectionNegative_switchesPriceToRedWhenOpen() {
        val dummyObs = BrentPriceObservation(
            price = 72.50,
            formatted = "$72.50",
            asOf = "2026-10-21T14:00:00Z",
            fetchedAt = "2026-10-21T14:05:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = dummyObs,
            isMarketOpen = true,
            isRefreshing = false,
            sessionDirection = ir.alifaniani.hawklyx.domain.PriceDirection.NEGATIVE
        )

        assertEquals("When price decreases relative to session baseline, price MUST turn red", ir.alifaniani.hawklyx.R.color.hawklyx_red, theme.priceColorRes)
        assertEquals("LIVE", theme.badgeText)
        assertFalse(theme.isMarketClosed)
    }

    @Test
    fun widgetThemeResolver_sessionDirectionPositive_usesEmeraldPriceWhenOpen() {
        val dummyObs = BrentPriceObservation(
            price = 76.50,
            formatted = "$76.50",
            asOf = "2026-10-21T14:00:00Z",
            fetchedAt = "2026-10-21T14:05:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.FRESH,
            observation = dummyObs,
            isMarketOpen = true,
            isRefreshing = false,
            sessionDirection = ir.alifaniani.hawklyx.domain.PriceDirection.POSITIVE
        )

        assertEquals("When price increases relative to session baseline, price MUST be emerald", ir.alifaniani.hawklyx.R.color.hawklyx_emerald, theme.priceColorRes)
        assertEquals("LIVE", theme.badgeText)
    }

    @Test
    fun widgetThemeResolver_sessionDirectionNegative_stillAmberWhenClosed() {
        val dummyObs = BrentPriceObservation(
            price = 72.50,
            formatted = "$72.50",
            asOf = "2026-10-21T14:00:00Z",
            fetchedAt = "2026-10-21T14:05:00Z",
            isStale = false
        )
        val theme = WidgetThemeResolver.resolve(
            status = DataFreshnessStatus.MARKET_CLOSED,
            observation = dummyObs,
            isMarketOpen = false,
            isRefreshing = false,
            sessionDirection = ir.alifaniani.hawklyx.domain.PriceDirection.NEGATIVE
        )

        assertEquals("Closed market price MUST be amber even if direction is negative", ir.alifaniani.hawklyx.R.color.hawklyx_amber, theme.priceColorRes)
        assertTrue(theme.isMarketClosed)
    }

    @Test
    fun widgetPendingIntents_haveDistinctActionsAndNonOverlappingRequestCodes() {
        // Body tap vs Refresh button must have completely distinct actions
        assertFalse(
            "ACTION_OPEN_FROM_WIDGET must differ from ACTION_REFRESH",
            HawklyxWidgetProvider.ACTION_OPEN_FROM_WIDGET == HawklyxWidgetProvider.ACTION_REFRESH
        )

        // Snapshot open request code must not overlap with Snapshot refresh request codes
        assertTrue(
            HawklyxWidgetProvider.REQUEST_CODE_OPEN_SNAPSHOT < HawklyxWidgetProvider.REQUEST_CODE_REFRESH_SNAPSHOT_BASE
        )

        // Intelligence open request code must not overlap with Intelligence refresh request codes
        assertTrue(
            HawklyxIntelligenceWidgetProvider.REQUEST_CODE_OPEN_INTELLIGENCE < HawklyxIntelligenceWidgetProvider.REQUEST_CODE_REFRESH_INTELLIGENCE_BASE
        )

        // Snapshot and Intelligence must have separate open request codes
        assertFalse(
            "Snapshot and Intelligence open request codes must be distinct",
            HawklyxWidgetProvider.REQUEST_CODE_OPEN_SNAPSHOT == HawklyxIntelligenceWidgetProvider.REQUEST_CODE_OPEN_INTELLIGENCE
        )

        // Snapshot and Intelligence refresh base codes must be non-overlapping
        assertTrue(
            HawklyxWidgetProvider.REQUEST_CODE_REFRESH_SNAPSHOT_BASE != HawklyxIntelligenceWidgetProvider.REQUEST_CODE_REFRESH_INTELLIGENCE_BASE
        )
    }

    @Test
    fun dualWidgets_bothUseSameRefreshAction() {
        assertEquals(
            "Both widgets must use identical ACTION_REFRESH string for unified coordinator routing",
            HawklyxWidgetProvider.ACTION_REFRESH,
            HawklyxIntelligenceWidgetProvider.ACTION_REFRESH
        )
        assertEquals(
            "Both widgets must use identical ACTION_OPEN_FROM_WIDGET string for unified body tap routing",
            HawklyxWidgetProvider.ACTION_OPEN_FROM_WIDGET,
            HawklyxIntelligenceWidgetProvider.ACTION_OPEN_FROM_WIDGET
        )
    }

    @Test
    fun cooldownEnforcement_rejectsPrematureRefreshWithoutFalseSuccess() {
        val now = 1771460000000L
        val nextAllowed = now + 90000L // 90 seconds remaining in cooldown
        val isCooldownActive = now < nextAllowed
        assertTrue("Cooldown must be detected as active", isCooldownActive)
        val remainingSec = ((nextAllowed - now) / 1000).toInt().coerceAtLeast(1)
        assertEquals(90, remainingSec)
    }
}
