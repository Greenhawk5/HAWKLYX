package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.model.HistorySummaryModel
import ir.alifaniani.hawklyx.data.model.HistoryUiState
import ir.alifaniani.hawklyx.data.remote.HawklyxApiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HawklyxHistoryPipelineTest {

    private val apiClient = HawklyxApiClient()

    @Test
    fun parseHistoryResponse_hourlyResolution_parsesSuccessfully() {
        val payload = """
            {
              "status": "success",
              "instrument": "BRENT_CRUDE_USD",
              "currency": "USD",
              "unit": "USD / barrel",
              "range": "7d",
              "resolution": "hourly",
              "collection_start_date": "2026-10-10T00:00:00.000Z",
              "earliest_timestamp": "2026-10-12T10:00:00Z",
              "latest_timestamp": "2026-10-18T14:00:00Z",
              "count": 2,
              "points": [
                {
                  "timestamp": "2026-10-12T10:00:00Z",
                  "price": 82.50,
                  "formatted": "$82.50",
                  "as_of": "2026-10-12T10:14:22Z",
                  "fetched_at": "2026-10-12T10:15:00Z"
                },
                {
                  "timestamp": "2026-10-18T14:00:00Z",
                  "price": 85.10,
                  "formatted": "$85.10",
                  "as_of": "2026-10-18T14:05:00Z",
                  "fetched_at": "2026-10-18T14:05:10Z"
                }
              ],
              "summary": {
                "first_price": 82.50,
                "latest_price": 85.10,
                "change_absolute": 2.60,
                "change_percent": 3.15,
                "high": 85.10,
                "low": 82.50
              },
              "is_partial": false,
              "server_time": "2026-10-18T14:20:00Z"
            }
        """.trimIndent()

        val result = apiClient.parseHistoryResponseBody(payload, 200, HistoryRange.ONE_WEEK)
        assertTrue(result.isSuccess)

        val model = result.getOrThrow()
        assertEquals("success", model.status)
        assertEquals("BRENT_CRUDE_USD", model.instrument)
        assertEquals(HistoryRange.ONE_WEEK, model.range)
        assertEquals("hourly", model.resolution)
        assertEquals(2, model.count)
        assertEquals(2, model.points.size)
        assertFalse(model.isPartial)

        // Point checks
        val p1 = model.points[0]
        assertEquals(82.50, p1.price, 0.001)
        assertEquals("$82.50", p1.formatted)
        assertFalse(p1.isDailyAggregate)
        assertNull(p1.open)
        assertNull(p1.close)

        // Summary checks
        assertNotNull(model.summary)
        assertEquals(82.50, model.summary!!.firstPrice, 0.001)
        assertEquals(85.10, model.summary!!.latestPrice, 0.001)
        assertEquals(2.60, model.summary!!.changeAbsolute, 0.001)
        assertEquals(3.15, model.summary!!.changePercent, 0.001)
        assertEquals(85.10, model.summary!!.high, 0.001)
        assertEquals(82.50, model.summary!!.low, 0.001)
    }

    @Test
    fun parseHistoryResponse_dailyOhlcResolution_parsesOhlcAndCompleteness() {
        val payload = """
            {
              "status": "success",
              "instrument": "BRENT_CRUDE_USD",
              "range": "6m",
              "resolution": "daily",
              "count": 2,
              "points": [
                {
                  "timestamp": "2026-10-16",
                  "price": 83.50,
                  "formatted": "$83.50",
                  "as_of": "2026-10-16T22:00:00Z",
                  "fetched_at": "2026-10-16T22:00:00Z",
                  "open": 81.00,
                  "high": 84.20,
                  "low": 80.50,
                  "close": 83.50,
                  "sample_count": 22,
                  "is_complete": true
                },
                {
                  "timestamp": "2026-10-17",
                  "price": 84.10,
                  "formatted": "$84.10",
                  "as_of": "2026-10-17T12:00:00Z",
                  "fetched_at": "2026-10-17T12:00:00Z",
                  "open": 83.50,
                  "high": 84.50,
                  "low": 83.00,
                  "close": 84.10,
                  "sample_count": 6,
                  "is_complete": false
                }
              ],
              "summary": {
                "first_price": 81.00,
                "latest_price": 84.10,
                "change_absolute": 3.10,
                "change_percent": 3.83,
                "high": 84.50,
                "low": 80.50
              },
              "is_partial": true,
              "collection_start_date": "2026-10-01T00:00:00Z",
              "server_time": "2026-10-18T14:20:00Z"
            }
        """.trimIndent()

        val result = apiClient.parseHistoryResponseBody(payload, 200, HistoryRange.SIX_MONTHS)
        assertTrue(result.isSuccess)

        val model = result.getOrThrow()
        assertEquals(HistoryRange.SIX_MONTHS, model.range)
        assertEquals("daily", model.resolution)
        assertTrue(model.isPartial)

        val pt1 = model.points[0]
        assertTrue(pt1.isDailyAggregate)
        assertEquals(81.00, pt1.open!!, 0.001)
        assertEquals(84.20, pt1.high!!, 0.001)
        assertEquals(80.50, pt1.low!!, 0.001)
        assertEquals(83.50, pt1.close!!, 0.001)
        assertEquals(22, pt1.sampleCount)
        assertEquals(true, pt1.isComplete)

        val pt2 = model.points[1]
        assertTrue(pt2.isDailyAggregate)
        assertEquals(6, pt2.sampleCount)
        assertEquals(false, pt2.isComplete)
    }

    @Test
    fun parseHistoryResponse_emptyResponse_handledCleanly() {
        val payload = """
            {
              "status": "empty",
              "instrument": "BRENT_CRUDE_USD",
              "currency": "USD",
              "unit": "USD / barrel",
              "range": "30d",
              "resolution": "hourly",
              "collection_start_date": null,
              "earliest_timestamp": null,
              "latest_timestamp": null,
              "count": 0,
              "points": [],
              "summary": null,
              "is_partial": false,
              "server_time": "2026-10-18T14:20:00Z",
              "message": "Historical data is accumulating. Showing empty history."
            }
        """.trimIndent()

        val result = apiClient.parseHistoryResponseBody(payload, 200, HistoryRange.ONE_MONTH)
        assertTrue(result.isSuccess)

        val model = result.getOrThrow()
        assertEquals("empty", model.status)
        assertTrue(model.isEmpty)
        assertEquals(0, model.count)
        assertTrue(model.points.isEmpty())
        assertNull(model.summary)
        assertNull(model.collectionStartDate)
        assertNotNull(model.message)
    }

    @Test
    fun parseHistoryResponse_malformedPayload_failsGracefully() {
        val result = apiClient.parseHistoryResponseBody("not a valid json string", 200)
        assertTrue(result.isFailure)
    }

    @Test
    fun historyRange_enumMappings_areAccurate() {
        assertEquals("1d", HistoryRange.ONE_DAY.apiValue)
        assertEquals("1D", HistoryRange.ONE_DAY.label)

        assertEquals("7d", HistoryRange.ONE_WEEK.apiValue)
        assertEquals("1W", HistoryRange.ONE_WEEK.label)

        assertEquals("30d", HistoryRange.ONE_MONTH.apiValue)
        assertEquals("1M", HistoryRange.ONE_MONTH.label)

        assertEquals("6m", HistoryRange.SIX_MONTHS.apiValue)
        assertEquals("6M", HistoryRange.SIX_MONTHS.label)

        assertEquals("1y", HistoryRange.ONE_YEAR.apiValue)
        assertEquals("1Y", HistoryRange.ONE_YEAR.label)

        assertEquals(HistoryRange.ONE_DAY, HistoryRange.fromApiValue("1d"))
        assertEquals(HistoryRange.ONE_WEEK, HistoryRange.fromApiValue("7D"))
        assertEquals(HistoryRange.SIX_MONTHS, HistoryRange.fromApiValue("6m"))
        assertEquals(HistoryRange.ONE_MONTH, HistoryRange.fromApiValue("unknown_range"))
    }

    @Test
    fun historyUiState_preservesStaleDataOnFailure() {
        val samplePoints = listOf(
            HistoricalPointModel(
                timestamp = "2026-10-18T10:00:00Z",
                timestampMs = 1771460100000L,
                price = 84.50,
                formatted = "$84.50",
                asOf = "2026-10-18T10:00:00Z",
                fetchedAt = "2026-10-18T10:01:00Z"
            )
        )
        val sampleModel = HistoryResponseModel(
            status = "success",
            count = 1,
            points = samplePoints
        )

        // Successful initial state
        var uiState = HistoryUiState(
            selectedRange = HistoryRange.ONE_MONTH,
            isLoading = false,
            isStale = false,
            data = sampleModel,
            errorMessage = null
        )

        assertFalse(uiState.isStale)
        assertNotNull(uiState.data)

        // Simulated network failure: preserve data and set isStale=true
        uiState = uiState.copy(
            isLoading = false,
            isStale = true,
            errorMessage = "Displaying cached history (offline)"
        )

        assertTrue(uiState.isStale)
        assertEquals(1, uiState.data?.points?.size)
        assertEquals(84.50, uiState.data?.points?.get(0)?.price ?: 0.0, 0.001)
        assertNotNull(uiState.errorMessage)
    }

    @Test
    fun historicalPointModel_dailyDateString_parsesUtcMidnightTimestamp() {
        val payload = """
            {
              "status": "success",
              "range": "6m",
              "resolution": "daily",
              "count": 1,
              "points": [
                {
                  "timestamp": "2026-10-18",
                  "price": 84.25,
                  "formatted": "$84.25",
                  "as_of": "2026-10-18T22:00:00Z",
                  "fetched_at": "2026-10-18T22:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val result = apiClient.parseHistoryResponseBody(payload, 200, HistoryRange.SIX_MONTHS)
        assertTrue(result.isSuccess)

        val pt = result.getOrThrow().points[0]
        assertEquals("2026-10-18", pt.timestamp)
        // Must parse into positive UTC epoch millis without throwing exception
        assertTrue(pt.timestampMs > 0L)
    }
}
