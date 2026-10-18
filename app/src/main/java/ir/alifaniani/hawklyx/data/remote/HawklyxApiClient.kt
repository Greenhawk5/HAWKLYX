package ir.alifaniani.hawklyx.data.remote

import android.util.Log
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class HawklyxApiException(
    message: String,
    val httpStatusCode: Int = 0,
    cause: Throwable? = null
) : Exception(message, cause)

data class HawklyxNetworkResponse(
    val status: DataFreshnessStatus,
    val observation: BrentPriceObservation?,
    val message: String,
    val nextRefreshAllowedTimeMs: Long,
    val httpStatusCode: Int = 200,
    val requestDurationMs: Long = 0L
)

class HawklyxApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 12000,
    private val readTimeoutMs: Int = 12000
) {

    fun fetchLatestPrice(force: Boolean = false): Result<HawklyxNetworkResponse> {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        val endpoint = if (force) "$baseUrl/api/v1/price/latest?force=true" else "$baseUrl/api/v1/price/latest"

        return try {
            logDiagnostic("Initiating price request (force=$force)")
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HAWKLYX-Android/1.0")
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = if (stream != null) {
                BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            } else {
                ""
            }

            val duration = System.currentTimeMillis() - startTime
            logDiagnostic("Received HTTP $responseCode in ${duration}ms")

            parseResponseBody(responseBody, responseCode, duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logDiagnostic("Network request failed after ${duration}ms: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    internal fun parseResponseBody(
        responseBody: String,
        responseCode: Int,
        durationMs: Long = 0L
    ): Result<HawklyxNetworkResponse> {
        if (responseBody.isBlank()) {
            if (responseCode !in 200..299) {
                val status = when (responseCode) {
                    429 -> DataFreshnessStatus.QUOTA_EXHAUSTED
                    503 -> DataFreshnessStatus.PROVIDER_UNAVAILABLE
                    else -> DataFreshnessStatus.PROVIDER_UNAVAILABLE
                }
                return Result.success(
                    HawklyxNetworkResponse(
                        status = status,
                        observation = null,
                        message = "Edge service returned HTTP $responseCode with empty body.",
                        nextRefreshAllowedTimeMs = 0L,
                        httpStatusCode = responseCode,
                        requestDurationMs = durationMs
                    )
                )
            }
            return Result.failure(HawklyxApiException("Empty response received from edge service", responseCode))
        }

        return try {
            val json = JSONObject(responseBody)
            val statusStr = json.optString("status", "no_data")
            val message = json.optString("message", "")
            val nextRefreshIso = json.optString("next_refresh_allowed_at", "")
            val nextRefreshMs = parseIsoToMillis(nextRefreshIso)

            var status = parseStatus(statusStr)
            val dataObj = json.optJSONObject("data")

            val observation = if (dataObj != null) {
                val rawPrice = dataObj.optDouble("price", 0.0)
                if (rawPrice > 0.0 && !rawPrice.isNaN() && !rawPrice.isInfinite()) {
                    val isStale = dataObj.optBoolean("is_stale", false)
                    if (isStale && status == DataFreshnessStatus.FRESH) {
                        status = DataFreshnessStatus.STALE
                    }
                    BrentPriceObservation(
                        code = dataObj.optString("code", "BRENT_CRUDE_USD"),
                        name = dataObj.optString("name", "Brent Crude Oil"),
                        price = rawPrice,
                        currency = dataObj.optString("currency", "USD"),
                        unit = dataObj.optString("unit", "USD / barrel"),
                        formatted = dataObj.optString("formatted", "$%.2f".format(rawPrice)),
                        asOf = dataObj.optString("as_of", ""),
                        collectedAt = if (dataObj.isNull("collected_at")) null else dataObj.optString("collected_at"),
                        createdAt = if (dataObj.isNull("created_at")) null else dataObj.optString("created_at"),
                        fetchedAt = dataObj.optString("fetched_at", ""),
                        isStale = isStale,
                        isSynthetic = dataObj.optBoolean("is_synthetic", false),
                        source = dataObj.optString("source", "OilPriceAPI")
                    )
                } else {
                    null
                }
            } else {
                null
            }

            Result.success(
                HawklyxNetworkResponse(
                    status = status,
                    observation = observation,
                    message = message,
                    nextRefreshAllowedTimeMs = nextRefreshMs,
                    httpStatusCode = responseCode,
                    requestDurationMs = durationMs
                )
            )
        } catch (e: Exception) {
            Result.failure(HawklyxApiException("Malformed JSON response from edge service: ${e.message}", responseCode, e))
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

    private fun logDiagnostic(msg: String) {
        // Safe diagnostic logging: never logs authorization headers, keys, or secrets
        try {
            Log.d(TAG, "[Diagnostic] $msg")
        } catch (_: Exception) {
            // Android Log may not be mocked in pure JVM tests
        }
    }

    companion object {
        private const val TAG = "HawklyxApiClient"
        const val DEFAULT_BASE_URL = "https://hawklyx-backend.hawkroid.workers.dev"
    }
}
