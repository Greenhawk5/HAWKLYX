package ir.alifaniani.hawklyx.data.local

import android.content.Context
import android.content.SharedPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
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
        nextRefreshAllowedTimeMs: Long
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

    fun saveStatusOnly(status: DataFreshnessStatus, nextRefreshAllowedTimeMs: Long) {
        prefs.edit().apply {
            putString(KEY_STATUS, status.name)
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
        private const val KEY_NEXT_REFRESH_ALLOWED = "next_refresh_allowed"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_HISTORY = "history_json"
    }
}
