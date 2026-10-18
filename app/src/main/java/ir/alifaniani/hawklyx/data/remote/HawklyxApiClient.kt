package ir.alifaniani.hawklyx.data.remote

import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class HawklyxNetworkResponse(
    val status: DataFreshnessStatus,
    val observation: BrentPriceObservation?,
    val message: String,
    val nextRefreshAllowedTimeMs: Long
)

class HawklyxApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 10000
) {

    fun fetchLatestPrice(force: Boolean = false): Result<HawklyxNetworkResponse> {
        return try {
            val endpoint = if (force) "$baseUrl/api/v1/price/latest?force=true" else "$baseUrl/api/v1/price/latest"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HAWKLYX-Android/1.0")
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val responseBody = reader.use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseBody)
            val statusStr = json.optString("status", "no_data")
            val message = json.optString("message", "")
            val nextRefreshIso = json.optString("next_refresh_allowed_at", "")
            val nextRefreshMs = parseIsoToMillis(nextRefreshIso)

            val status = parseStatus(statusStr)
            val dataObj = json.optJSONObject("data")

            val observation = if (dataObj != null) {
                val price = dataObj.optDouble("price", 0.0)
                BrentPriceObservation(
                    code = dataObj.optString("code", "BRENT_CRUDE_USD"),
                    name = dataObj.optString("name", "Brent Crude Oil"),
                    price = price,
                    currency = dataObj.optString("currency", "USD"),
                    unit = dataObj.optString("unit", "USD / barrel"),
                    formatted = dataObj.optString("formatted", "$%.2f".format(price)),
                    asOf = dataObj.optString("as_of", ""),
                    collectedAt = if (dataObj.isNull("collected_at")) null else dataObj.optString("collected_at"),
                    createdAt = if (dataObj.isNull("created_at")) null else dataObj.optString("created_at"),
                    fetchedAt = dataObj.optString("fetched_at", ""),
                    isStale = dataObj.optBoolean("is_stale", false),
                    isSynthetic = dataObj.optBoolean("is_synthetic", false),
                    source = dataObj.optString("source", "OilPriceAPI")
                )
            } else {
                null
            }

            Result.success(
                HawklyxNetworkResponse(
                    status = status,
                    observation = observation,
                    message = message,
                    nextRefreshAllowedTimeMs = nextRefreshMs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStatus(statusStr: String): DataFreshnessStatus {
        return when (statusStr.lowercase()) {
            "fresh" -> DataFreshnessStatus.FRESH
            "stale" -> DataFreshnessStatus.STALE
            "refreshing" -> DataFreshnessStatus.REFRESHING
            "provider_unavailable" -> DataFreshnessStatus.PROVIDER_UNAVAILABLE
            "quota_exhausted" -> DataFreshnessStatus.QUOTA_EXHAUSTED
            "invalid_provider_response" -> DataFreshnessStatus.INVALID_RESPONSE
            else -> DataFreshnessStatus.NO_DATA
        }
    }

    private fun parseIsoToMillis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        // Default target Cloudflare Worker URL or configurable local dev endpoint
        const val DEFAULT_BASE_URL = "https://hawklyx-backend.workers.dev"
    }
}
