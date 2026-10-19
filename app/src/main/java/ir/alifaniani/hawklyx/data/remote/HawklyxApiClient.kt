package ir.alifaniani.hawklyx.data.remote

import android.util.Log
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistorySummaryModel
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
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
    val requestDurationMs: Long = 0L,
    val marketState: ir.alifaniani.hawklyx.data.model.MarketStateModel? = null
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
                setRequestProperty("User-Agent", "HAWKLYX-Android/1.0.0")
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

            val marketObj = json.optJSONObject("market")
            val marketState = if (marketObj != null) {
                ir.alifaniani.hawklyx.data.model.MarketStateModel(
                    isOpen = marketObj.optBoolean("is_open", true),
                    status = marketObj.optString("status", "open"),
                    session = marketObj.optString("session", "regular"),
                    nextOpenAt = if (marketObj.isNull("next_open_at")) null else marketObj.optString("next_open_at"),
                    description = marketObj.optString("description", "")
                )
            } else null

            Result.success(
                HawklyxNetworkResponse(
                    status = status,
                    observation = observation,
                    message = message,
                    nextRefreshAllowedTimeMs = nextRefreshMs,
                    httpStatusCode = responseCode,
                    requestDurationMs = durationMs,
                    marketState = marketState
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
            "market_closed" -> DataFreshnessStatus.MARKET_CLOSED
            "refreshing" -> DataFreshnessStatus.REFRESHING
            "provider_unavailable" -> DataFreshnessStatus.PROVIDER_UNAVAILABLE
            "quota_exhausted" -> DataFreshnessStatus.QUOTA_EXHAUSTED
            "invalid_provider_response" -> DataFreshnessStatus.INVALID_RESPONSE
            else -> DataFreshnessStatus.NO_DATA
        }
    }

    fun fetchHistory(
        range: HistoryRange = HistoryRange.ONE_MONTH,
        resolution: String? = null,
        instrument: String? = null
    ): Result<HistoryResponseModel> {
        val startTime = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        val queryParams = mutableListOf("range=${range.apiValue}")
        if (!resolution.isNullOrBlank()) {
            queryParams.add("resolution=$resolution")
        }
        if (!instrument.isNullOrBlank()) {
            queryParams.add("instrument=$instrument")
        }
        val endpoint = "$baseUrl/api/v1/price/history?${queryParams.joinToString("&")}"

        return try {
            logDiagnostic("Initiating history request (range=${range.apiValue})")
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
            logDiagnostic("Received HTTP $responseCode for history in ${duration}ms")

            parseHistoryResponseBody(responseBody, responseCode, range)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logDiagnostic("History request failed after ${duration}ms: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    internal fun parseHistoryResponseBody(
        responseBody: String,
        responseCode: Int,
        fallbackRange: HistoryRange = HistoryRange.ONE_MONTH
    ): Result<HistoryResponseModel> {
        if (responseBody.isBlank()) {
            return Result.failure(HawklyxApiException("Empty response received from edge history service", responseCode))
        }

        return try {
            val json = JSONObject(responseBody)
            val status = json.optString("status", "empty")
            val instrument = json.optString("instrument", "BRENT_CRUDE_USD")
            val currency = json.optString("currency", "USD")
            val unit = json.optString("unit", "USD / barrel")
            val rangeStr = json.optString("range", fallbackRange.apiValue)
            val range = HistoryRange.fromApiValue(rangeStr)
            val resolution = json.optString("resolution", "hourly")
            val collectionStartDate = if (json.isNull("collection_start_date")) null else json.optString("collection_start_date")
            val earliestTimestamp = if (json.isNull("earliest_timestamp")) null else json.optString("earliest_timestamp")
            val latestTimestamp = if (json.isNull("latest_timestamp")) null else json.optString("latest_timestamp")
            val count = json.optInt("count", 0)
            val isPartial = json.optBoolean("is_partial", false)
            val serverTime = json.optString("server_time", "")
            val message = if (json.isNull("message")) null else json.optString("message")

            // Parse Summary if present
            val summaryObj = json.optJSONObject("summary")
            val summary = if (summaryObj != null) {
                HistorySummaryModel(
                    firstPrice = summaryObj.optDouble("first_price", 0.0),
                    latestPrice = summaryObj.optDouble("latest_price", 0.0),
                    changeAbsolute = summaryObj.optDouble("change_absolute", 0.0),
                    changePercent = summaryObj.optDouble("change_percent", 0.0),
                    high = summaryObj.optDouble("high", 0.0),
                    low = summaryObj.optDouble("low", 0.0)
                )
            } else null

            // Parse Points array
            val pointsArray = json.optJSONArray("points")
            val pointsList = mutableListOf<HistoricalPointModel>()
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    val ptObj = pointsArray.optJSONObject(i) ?: continue
                    val timestamp = ptObj.optString("timestamp", "")
                    val timestampMs = parseIsoToMillis(timestamp)
                    val price = ptObj.optDouble("price", 0.0)
                    val formatted = ptObj.optString("formatted", "$%.2f".format(price))
                    val asOf = ptObj.optString("as_of", "")
                    val fetchedAt = ptObj.optString("fetched_at", "")

                    val open = if (ptObj.has("open") && !ptObj.isNull("open")) ptObj.optDouble("open") else null
                    val high = if (ptObj.has("high") && !ptObj.isNull("high")) ptObj.optDouble("high") else null
                    val low = if (ptObj.has("low") && !ptObj.isNull("low")) ptObj.optDouble("low") else null
                    val close = if (ptObj.has("close") && !ptObj.isNull("close")) ptObj.optDouble("close") else null
                    val sampleCount = if (ptObj.has("sample_count") && !ptObj.isNull("sample_count")) ptObj.optInt("sample_count") else null
                    val isComplete = if (ptObj.has("is_complete") && !ptObj.isNull("is_complete")) ptObj.optBoolean("is_complete") else null

                    pointsList.add(
                        HistoricalPointModel(
                            timestamp = timestamp,
                            timestampMs = timestampMs,
                            price = price,
                            formatted = formatted,
                            asOf = asOf,
                            fetchedAt = fetchedAt,
                            open = open,
                            high = high,
                            low = low,
                            close = close,
                            sampleCount = sampleCount,
                            isComplete = isComplete
                        )
                    )
                }
            }

            Result.success(
                HistoryResponseModel(
                    status = status,
                    instrument = instrument,
                    currency = currency,
                    unit = unit,
                    range = range,
                    resolution = resolution,
                    collectionStartDate = collectionStartDate,
                    earliestTimestamp = earliestTimestamp,
                    latestTimestamp = latestTimestamp,
                    count = count,
                    points = pointsList,
                    summary = summary,
                    isPartial = isPartial,
                    serverTime = serverTime,
                    message = message
                )
            )
        } catch (e: Exception) {
            Result.failure(HawklyxApiException("Malformed history response from edge service: ${e.message}", responseCode, e))
        }
    }

    private fun parseIsoToMillis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            if (isoString.length == 10 && isoString.contains("-")) {
                Instant.parse(isoString + "T00:00:00Z").toEpochMilli()
            } else {
                Instant.parse(isoString).toEpochMilli()
            }
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
