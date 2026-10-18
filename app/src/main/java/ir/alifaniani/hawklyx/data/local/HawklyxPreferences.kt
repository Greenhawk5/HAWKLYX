package ir.alifaniani.hawklyx.data.local

import android.content.Context
import android.content.SharedPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
import ir.alifaniani.hawklyx.data.model.HistoricalPointModel
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.model.HistorySummaryModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust local persistence for the last-known-good price observation and genuine historical points.
 * Ensures the app and home-screen widget immediately display valid data
 * upon restart, device reboot, or network failure.
 */
class HawklyxPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveObservation(
        observation: BrentPriceObservation,
        status: DataFreshnessStatus,
        nextRefreshAllowedTimeMs: Long,
        isMarketOpen: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            putString(KEY_CODE, observation.code)
            putString(KEY_NAME, observation.name)
            putFloat(KEY_PRICE, observation.price.toFloat())
            putString(KEY_CURRENCY, observation.currency)
            putString(KEY_UNIT, observation.unit)
            putString(KEY_FORMATTED, observation.formatted)
            putString(KEY_AS_OF, observation.asOf)
            putString(KEY_COLLECTED_AT, observation.collectedAt)
            putString(KEY_CREATED_AT, observation.createdAt)
            putString(KEY_FETCHED_AT, observation.fetchedAt)
            putBoolean(KEY_IS_STALE, observation.isStale)
            putBoolean(KEY_IS_SYNTHETIC, observation.isSynthetic)
            putString(KEY_SOURCE, observation.source)
            putString(KEY_STATUS, status.name)
            putBoolean(KEY_MARKET_OPEN, isMarketOpen)
            putLong(KEY_NEXT_REFRESH_ALLOWED, nextRefreshAllowedTimeMs)
            putLong(KEY_LAST_FETCH_TIME, now)
            apply()
        }

        // Record genuine historical observation
        recordHistoricalPoint(
            HistoricalObservationPoint(
                timestampMs = now,
                price = observation.price,
                formatted = observation.formatted,
                asOfIso = observation.asOf
            )
        )
    }

    fun saveStatusOnly(
        status: DataFreshnessStatus,
        nextRefreshAllowedTimeMs: Long,
        isMarketOpen: Boolean = true
    ) {
        prefs.edit().apply {
            putString(KEY_STATUS, status.name)
            putBoolean(KEY_MARKET_OPEN, isMarketOpen)
            putLong(KEY_NEXT_REFRESH_ALLOWED, nextRefreshAllowedTimeMs)
            apply()
        }
    }

    fun getLastObservation(): BrentPriceObservation? {
        if (!prefs.contains(KEY_PRICE)) {
            return null
        }
        val price = prefs.getFloat(KEY_PRICE, 0f).toDouble()
        if (price <= 0.0 || price.isNaN() || price.isInfinite()) return null

        return BrentPriceObservation(
            code = prefs.getString(KEY_CODE, "BRENT_CRUDE_USD") ?: "BRENT_CRUDE_USD",
            name = prefs.getString(KEY_NAME, "Brent Crude Oil") ?: "Brent Crude Oil",
            price = price,
            currency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
            unit = prefs.getString(KEY_UNIT, "USD / barrel") ?: "USD / barrel",
            formatted = prefs.getString(KEY_FORMATTED, "$%.2f".format(price)) ?: "$%.2f".format(price),
            asOf = prefs.getString(KEY_AS_OF, "") ?: "",
            collectedAt = prefs.getString(KEY_COLLECTED_AT, null),
            createdAt = prefs.getString(KEY_CREATED_AT, null),
            fetchedAt = prefs.getString(KEY_FETCHED_AT, "") ?: "",
            isStale = prefs.getBoolean(KEY_IS_STALE, false),
            isSynthetic = prefs.getBoolean(KEY_IS_SYNTHETIC, false),
            source = prefs.getString(KEY_SOURCE, "OilPriceAPI") ?: "OilPriceAPI"
        )
    }

    fun getLastStatus(): DataFreshnessStatus {
        val name = prefs.getString(KEY_STATUS, null) ?: return DataFreshnessStatus.NO_DATA
        return try {
            DataFreshnessStatus.valueOf(name)
        } catch (_: Exception) {
            DataFreshnessStatus.NO_DATA
        }
    }

    fun isMarketOpen(): Boolean {
        val status = getLastStatus()
        if (status == DataFreshnessStatus.MARKET_CLOSED) return false
        return prefs.getBoolean(KEY_MARKET_OPEN, true)
    }

    fun getNextRefreshAllowedTimeMs(): Long {
        return prefs.getLong(KEY_NEXT_REFRESH_ALLOWED, 0L)
    }

    fun getLastFetchTimeMs(): Long {
        return prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
    }

    fun getHistoricalPoints(): List<HistoricalObservationPoint> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<HistoricalObservationPoint>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoricalObservationPoint(
                        timestampMs = obj.getLong("t"),
                        price = obj.getDouble("p"),
                        formatted = obj.getString("f"),
                        asOfIso = obj.optString("a", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recordHistoricalPoint(point: HistoricalObservationPoint) {
        try {
            val existing = getHistoricalPoints().toMutableList()
            // Deduplicate: avoid appending identical observation
            val lastPoint = existing.lastOrNull()
            if (lastPoint != null && (lastPoint.asOfIso == point.asOfIso || (lastPoint.price == point.price && (point.timestampMs - lastPoint.timestampMs < 300_000L)))) {
                return
            }
            existing.add(point)
            // Limit to most recent MAX_HISTORY_POINTS
            val trimmed = if (existing.size > MAX_HISTORY_POINTS) {
                existing.takeLast(MAX_HISTORY_POINTS)
            } else {
                existing
            }

            val array = JSONArray()
            for (p in trimmed) {
                val obj = JSONObject()
                obj.put("t", p.timestampMs)
                obj.put("p", p.price)
                obj.put("f", p.formatted)
                obj.put("a", p.asOfIso)
                array.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    fun saveCachedHistoryResponse(response: HistoryResponseModel) {
        try {
            val root = JSONObject().apply {
                put("status", response.status)
                put("instrument", response.instrument)
                put("currency", response.currency)
                put("unit", response.unit)
                put("range", response.range.apiValue)
                put("resolution", response.resolution)
                put("collection_start_date", response.collectionStartDate)
                put("earliest_timestamp", response.earliestTimestamp)
                put("latest_timestamp", response.latestTimestamp)
                put("count", response.count)
                put("is_partial", response.isPartial)
                put("server_time", response.serverTime)
                put("message", response.message)

                response.summary?.let { s ->
                    val sumObj = JSONObject().apply {
                        put("first_price", s.firstPrice)
                        put("latest_price", s.latestPrice)
                        put("change_absolute", s.changeAbsolute)
                        put("change_percent", s.changePercent)
                        put("high", s.high)
                        put("low", s.low)
                    }
                    put("summary", sumObj)
                }

                val ptsArray = JSONArray()
                for (pt in response.points) {
                    val ptObj = JSONObject().apply {
                        put("timestamp", pt.timestamp)
                        put("timestampMs", pt.timestampMs)
                        put("price", pt.price)
                        put("formatted", pt.formatted)
                        put("as_of", pt.asOf)
                        put("fetched_at", pt.fetchedAt)
                        pt.open?.let { put("open", it) }
                        pt.high?.let { put("high", it) }
                        pt.low?.let { put("low", it) }
                        pt.close?.let { put("close", it) }
                        pt.sampleCount?.let { put("sample_count", it) }
                        pt.isComplete?.let { put("is_complete", it) }
                    }
                    ptsArray.put(ptObj)
                }
                put("points", ptsArray)
            }

            prefs.edit().apply {
                putString(KEY_CACHED_HISTORY_JSON, root.toString())
                putLong(KEY_CACHED_HISTORY_TIME, System.currentTimeMillis())
                apply()
            }
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    fun getCachedHistoryResponse(expectedRange: HistoryRange = HistoryRange.ONE_WEEK): HistoryResponseModel? {
        val jsonStr = prefs.getString(KEY_CACHED_HISTORY_JSON, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val rangeStr = json.optString("range", expectedRange.apiValue)
            val range = HistoryRange.fromApiValue(rangeStr)
            val status = json.optString("status", "empty")
            val instrument = json.optString("instrument", "BRENT_CRUDE_USD")
            val currency = json.optString("currency", "USD")
            val unit = json.optString("unit", "USD / barrel")
            val resolution = json.optString("resolution", "hourly")
            val collectionStartDate = if (json.isNull("collection_start_date")) null else json.optString("collection_start_date")
            val earliestTimestamp = if (json.isNull("earliest_timestamp")) null else json.optString("earliest_timestamp")
            val latestTimestamp = if (json.isNull("latest_timestamp")) null else json.optString("latest_timestamp")
            val count = json.optInt("count", 0)
            val isPartial = json.optBoolean("is_partial", false)
            val serverTime = json.optString("server_time", "")
            val message = if (json.isNull("message")) null else json.optString("message")

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

            val pointsArray = json.optJSONArray("points")
            val pointsList = mutableListOf<HistoricalPointModel>()
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    val ptObj = pointsArray.optJSONObject(i) ?: continue
                    pointsList.add(
                        HistoricalPointModel(
                            timestamp = ptObj.optString("timestamp", ""),
                            timestampMs = ptObj.optLong("timestampMs", 0L),
                            price = ptObj.optDouble("price", 0.0),
                            formatted = ptObj.optString("formatted", ""),
                            asOf = ptObj.optString("as_of", ""),
                            fetchedAt = ptObj.optString("fetched_at", ""),
                            open = if (ptObj.has("open") && !ptObj.isNull("open")) ptObj.optDouble("open") else null,
                            high = if (ptObj.has("high") && !ptObj.isNull("high")) ptObj.optDouble("high") else null,
                            low = if (ptObj.has("low") && !ptObj.isNull("low")) ptObj.optDouble("low") else null,
                            close = if (ptObj.has("close") && !ptObj.isNull("close")) ptObj.optDouble("close") else null,
                            sampleCount = if (ptObj.has("sample_count") && !ptObj.isNull("sample_count")) ptObj.optInt("sample_count") else null,
                            isComplete = if (ptObj.has("is_complete") && !ptObj.isNull("is_complete")) ptObj.optBoolean("is_complete") else null
                        )
                    )
                }
            }

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
        } catch (_: Exception) {
            null
        }
    }

    fun getCachedHistoryTimeMs(): Long {
        return prefs.getLong(KEY_CACHED_HISTORY_TIME, 0L)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "hawklyx_price_cache"
        private const val MAX_HISTORY_POINTS = 100

        private const val KEY_CODE = "code"
        private const val KEY_NAME = "name"
        private const val KEY_PRICE = "price"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_UNIT = "unit"
        private const val KEY_FORMATTED = "formatted"
        private const val KEY_AS_OF = "as_of"
        private const val KEY_COLLECTED_AT = "collected_at"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_IS_STALE = "is_stale"
        private const val KEY_IS_SYNTHETIC = "is_synthetic"
        private const val KEY_SOURCE = "source"
        private const val KEY_STATUS = "status"
        private const val KEY_MARKET_OPEN = "market_is_open"
        private const val KEY_NEXT_REFRESH_ALLOWED = "next_refresh_allowed"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_HISTORY = "history_json"
        private const val KEY_CACHED_HISTORY_JSON = "cached_history_json"
        private const val KEY_CACHED_HISTORY_TIME = "cached_history_time"
    }
}
