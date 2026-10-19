package ir.alifaniani.hawklyx.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ir.alifaniani.hawklyx.MainActivity
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.repository.HawklyxRepository
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.domain.BrentMarketCalendar
import ir.alifaniani.hawklyx.domain.HistoricalPoint
import ir.alifaniani.hawklyx.domain.PriceChange24h
import ir.alifaniani.hawklyx.domain.PriceChangeCalculator
import ir.alifaniani.hawklyx.domain.PriceChangeDirection
import ir.alifaniani.hawklyx.domain.PriceDirection
import ir.alifaniani.hawklyx.domain.SessionPriceCalculator
import ir.alifaniani.hawklyx.util.HawklyxTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Android Home-Screen Widget Provider for HAWKLYX.
 * The primary user experience for glanceable, resilient Brent crude oil prices.
 */
class HawklyxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = HawklyxPreferences(context)
        val observation = prefs.getLastObservation()
        val status = prefs.getLastStatus()

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, observation, status, isRefreshing = false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            HawklyxWidgetCoordinator.handleRefreshAction(context, pendingResult)
        }
    }

    companion object {
        const val ACTION_REFRESH = "ir.alifaniani.hawklyx.ACTION_REFRESH"
        const val ACTION_OPEN_FROM_WIDGET = "ir.alifaniani.hawklyx.ACTION_OPEN_FROM_WIDGET"
        const val REQUEST_CODE_OPEN_SNAPSHOT = 1001
        const val REQUEST_CODE_REFRESH_SNAPSHOT_BASE = 2000

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                val status = prefs.getLastStatus()
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id, observation, status, isRefreshing = false)
                }
            }
        }

        fun updateRefreshingState(context: Context, isRefreshing: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                val status = if (isRefreshing) DataFreshnessStatus.REFRESHING else prefs.getLastStatus()
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id, observation, status, isRefreshing = isRefreshing)
                }
            }
        }

        fun showCooldown(context: Context, remainingSeconds: Int) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.hawklyx_widget_layout)
                    setupClickIntents(context, views, id)
                    bindWidgetContent(context, views, observation, prefs.getLastStatus(), isRefreshing = false)
                    views.setTextViewText(R.id.widget_status_msg, "Cooldown (${remainingSeconds}s)")
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            observation: BrentPriceObservation?,
            status: DataFreshnessStatus,
            isRefreshing: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.hawklyx_widget_layout)
            setupClickIntents(context, views, appWidgetId)
            bindWidgetContent(context, views, observation, status, isRefreshing)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupClickIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            // Setup Main Click -> Open MainActivity with explicit action and dedicated request code
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_FROM_WIDGET
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_SNAPSHOT,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            // Setup Refresh Button Click -> Broadcast ACTION_REFRESH with distinct request code
            val refreshIntent = Intent(context, HawklyxWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH_SNAPSHOT_BASE + appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)
        }

        private fun bindWidgetContent(
            context: Context,
            views: RemoteViews,
            observation: BrentPriceObservation?,
            status: DataFreshnessStatus,
            isRefreshing: Boolean
        ) {
            val prefs = HawklyxPreferences(context)
            val isMarketOpenPref = prefs.isMarketOpen()
            val currentTs = if (observation != null && observation.asOf.isNotBlank()) {
                try { Instant.parse(observation.asOf).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
            } else System.currentTimeMillis()

            val sessionInfo = BrentMarketCalendar.getSessionInfo(currentTs)
            val isMarketOpen = isMarketOpenPref && sessionInfo.isMarketOpen && status != DataFreshnessStatus.MARKET_CLOSED

            val sessionDirection = if (observation != null && observation.price > 0.0 && isMarketOpen) {
                val isStale = observation.isStale || status == DataFreshnessStatus.STALE ||
                        status == DataFreshnessStatus.PROVIDER_UNAVAILABLE || status == DataFreshnessStatus.INVALID_RESPONSE
                val refPrice = prefs.getSessionReferencePrice()
                SessionPriceCalculator.calculateSessionPrice(
                    currentPrice = observation.price,
                    referencePrice = refPrice,
                    referenceTimestampMs = prefs.getSessionReferenceTimestampMs(),
                    sessionId = sessionInfo.sessionId,
                    isMarketOpen = true,
                    isStale = isStale
                ).direction
            } else {
                PriceDirection.NEUTRAL
            }

            val theme = WidgetThemeResolver.resolve(
                status = status,
                observation = observation,
                isMarketOpen = isMarketOpenPref,
                isRefreshing = isRefreshing,
                sessionDirection = sessionDirection
            )

            // 1. Dominant Price & Currency display
            if (observation != null) {
                views.setTextViewText(R.id.widget_price, observation.formatted)
                views.setTextColor(R.id.widget_price, ContextCompat.getColor(context, theme.priceColorRes))
                views.setTextViewText(R.id.widget_unit, observation.unit)
                val formattedTime = formatObservationTime(observation.asOf)
                views.setTextViewText(R.id.widget_updated, "Observed: $formattedTime")
            } else {
                views.setTextViewText(R.id.widget_price, "--.--")
                views.setTextColor(R.id.widget_price, ContextCompat.getColor(context, theme.priceColorRes))
                views.setTextViewText(R.id.widget_unit, context.getString(R.string.unit_usd_bbl))
                views.setTextViewText(R.id.widget_updated, "Awaiting first sync")
            }

            // 2. Status Badge with dynamic semantic background & text color
            views.setTextViewText(R.id.widget_status_badge, theme.badgeText)
            views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, theme.badgeTextColorRes))
            views.setInt(R.id.widget_status_badge, "setBackgroundResource", theme.badgeBgRes)

            // Calculate 24-hour price change from available history
            val allPoints = mutableListOf<HistoricalPoint>()
            prefs.getCachedHistoryResponse(HistoryRange.ONE_WEEK)?.points?.let { allPoints.addAll(it) }
            allPoints.addAll(prefs.getHistoricalPoints())
            val priceChange = if (observation != null) {
                PriceChangeCalculator.calculate24hChange(observation.price, currentTs, allPoints)
            } else {
                PriceChange24h.unavailable()
            }

            // 3. Cadence / Market State Pill
            if (priceChange.isAvailable && !theme.isMarketClosed) {
                views.setTextViewText(R.id.widget_cadence_pill, "24h ${priceChange.formattedPercentage}")
                val changeColorRes = when (priceChange.direction) {
                    PriceChangeDirection.POSITIVE -> R.color.hawklyx_emerald
                    PriceChangeDirection.NEGATIVE -> R.color.hawklyx_red
                    PriceChangeDirection.NEUTRAL -> R.color.hawklyx_text_secondary
                }
                views.setTextColor(R.id.widget_cadence_pill, ContextCompat.getColor(context, changeColorRes))
            } else {
                views.setTextViewText(R.id.widget_cadence_pill, theme.cadencePillText)
                val cadencePillColor = if (theme.isMarketClosed) R.color.hawklyx_amber else R.color.hawklyx_text_secondary
                views.setTextColor(R.id.widget_cadence_pill, ContextCompat.getColor(context, cadencePillColor))
            }

            // 4. Status message with 24h change context if market is closed
            val finalStatusMsg = if (priceChange.isAvailable && theme.isMarketClosed) {
                "24h: ${priceChange.formattedWithArrow} • ${theme.statusMessage}"
            } else {
                theme.statusMessage
            }
            views.setTextViewText(R.id.widget_status_msg, finalStatusMsg)
        }

        private fun formatObservationTime(asOfIso: String): String {
            if (asOfIso.isBlank()) return "Recently"
            return HawklyxTimeUtils.formatIsoToShortTime(asOfIso)
        }
    }
}
