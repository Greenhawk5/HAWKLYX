package ir.alifaniani.hawklyx

import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.remote.HawklyxApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HawklyxApiClientParsingTest {

    private val client = HawklyxApiClient()

    @Test
    fun parseResponseBody_successfulFreshResponse_parsesCorrectly() {
        val payload = """
            {
              "status": "fresh",
              "data": {
                "code": "BRENT_CRUDE_USD",
                "name": "Brent Crude Oil",
                "price": 109.95,
                "currency": "USD",
                "unit": "USD / barrel",
                "formatted": "$109.95",
                "as_of": "2026-10-16T21:59:00.000Z",
                "collected_at": "2026-10-18T14:01:24.481Z",
                "created_at": "2026-10-18T14:01:24.481Z",
                "fetched_at": "2026-10-18T14:10:39.070Z",
                "is_stale": false,
                "is_synthetic": false,
                "source": "market_reporting"
              },
              "message": "Brent crude price updated successfully from provider.",
              "next_refresh_allowed_at": "2026-10-18T14:13:39.070Z",
              "server_time": "2026-10-18T14:10:39.070Z"
            }
        """.trimIndent()

        val result = client.parseResponseBody(payload, 200, 150L)
        assertTrue(result.isSuccess)

        val response = result.getOrThrow()
        assertEquals(DataFreshnessStatus.FRESH, response.status)
        assertNotNull(response.observation)
        assertEquals(109.95, response.observation!!.price, 0.001)
        assertEquals("$109.95", response.observation!!.formatted)
        assertFalse(response.observation!!.isStale)
        assertEquals("market_reporting", response.observation!!.source)
        assertTrue(response.nextRefreshAllowedTimeMs > 0L)
    }

    @Test
    fun parseResponseBody_staleFlag_setsStatusToStaleEvenIfRootSaysFresh() {
        val payload = """
            {
              "status": "fresh",
              "data": {
                "code": "BRENT_CRUDE_USD",
                "price": 82.40,
                "formatted": "$82.40",
                "as_of": "2026-10-15T18:00:00.000Z",
                "fetched_at": "2026-10-18T14:00:00.000Z",
                "is_stale": true,
                "is_synthetic": false
              },
              "message": "Preserving last known price.",
              "next_refresh_allowed_at": "2026-10-18T14:03:00.000Z"
            }
        """.trimIndent()

        val result = client.parseResponseBody(payload, 200, 100L)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()

        assertEquals(DataFreshnessStatus.STALE, response.status)
        assertTrue(response.observation!!.isStale)
    }

    @Test
    fun parseResponseBody_cooldownResponseWithCachedData_preservesObservation() {
        val payload = """
            {
              "status": "fresh",
              "data": {
                "code": "BRENT_CRUDE_USD",
                "price": 109.95,
                "formatted": "$109.95",
                "as_of": "2026-10-16T21:59:00.000Z",
                "fetched_at": "2026-10-18T14:10:39.070Z",
                "is_stale": false,
                "is_synthetic": false
              },
              "message": "Manual refresh is cooling down. Showing latest available price.",
              "next_refresh_allowed_at": "2026-10-18T14:13:39.070Z",
              "server_time": "2026-10-18T14:11:15.756Z"
            }
        """.trimIndent()

        val result = client.parseResponseBody(payload, 200, 80L)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()

        assertEquals(DataFreshnessStatus.FRESH, response.status)
        assertEquals(109.95, response.observation!!.price, 0.001)
        assertTrue(response.message.contains("cooling down"))
    }

    @Test
    fun parseResponseBody_quotaExhaustedEnvelope_mapsProperStatus() {
        val payload = """
            {
              "status": "quota_exhausted",
              "data": null,
              "message": "Price updates are temporarily unavailable due to daily quota. Showing last valid price.",
              "next_refresh_allowed_at": "2026-10-18T16:00:00.000Z"
            }
        """.trimIndent()

        val result = client.parseResponseBody(payload, 200, 70L)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()

        assertEquals(DataFreshnessStatus.QUOTA_EXHAUSTED, response.status)
        assertNull(response.observation)
    }

    @Test
    fun parseResponseBody_providerUnavailable503_mapsProperStatus() {
        val payload = """
            {
              "status": "provider_unavailable",
              "data": null,
              "message": "Upstream price provider temporarily unreachable.",
              "next_refresh_allowed_at": "2026-10-18T14:30:00.000Z"
            }
        """.trimIndent()

        val result = client.parseResponseBody(payload, 503, 60L)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()

        assertEquals(DataFreshnessStatus.PROVIDER_UNAVAILABLE, response.status)
        assertNull(response.observation)
    }

    @Test
    fun parseResponseBody_nonPositivePrice_rejectsAsInvalidObservation() {
        val payloadZero = """
            {
              "status": "fresh",
              "data": {
                "price": 0.0,
                "formatted": "$0.00"
              }
            }
        """.trimIndent()

        val resultZero = client.parseResponseBody(payloadZero, 200)
        assertTrue(resultZero.isSuccess)
        assertNull(resultZero.getOrThrow().observation)

        val payloadNeg = """
            {
              "status": "fresh",
              "data": {
                "price": -15.50,
                "formatted": "$-15.50"
              }
            }
        """.trimIndent()

        val resultNeg = client.parseResponseBody(payloadNeg, 200)
        assertTrue(resultNeg.isSuccess)
        assertNull(resultNeg.getOrThrow().observation)
    }

    @Test
    fun parseResponseBody_emptyBody_handlesGracefully() {
        val empty200 = client.parseResponseBody("", 200)
        assertTrue(empty200.isFailure)

        val empty503 = client.parseResponseBody("", 503)
        assertTrue(empty503.isSuccess)
        assertEquals(DataFreshnessStatus.PROVIDER_UNAVAILABLE, empty503.getOrThrow().status)
        assertNull(empty503.getOrThrow().observation)

        val empty429 = client.parseResponseBody("", 429)
        assertTrue(empty429.isSuccess)
        assertEquals(DataFreshnessStatus.QUOTA_EXHAUSTED, empty429.getOrThrow().status)
    }

    @Test
    fun parseResponseBody_malformedJson_failsWithParseException() {
        val malformed = client.parseResponseBody("{ invalid_json: true ", 200)
        assertTrue(malformed.isFailure)
    }
}
