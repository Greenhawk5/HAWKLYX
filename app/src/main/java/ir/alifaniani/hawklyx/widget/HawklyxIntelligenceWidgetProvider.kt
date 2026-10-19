package ir.alifaniani.hawklyx.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ir.alifaniani.hawklyx.MainActivity
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.repository.HawklyxRepository
import ir.alifaniani.hawklyx.domain.BrentMarketCalendar
import ir.alifaniani.hawklyx.domain.HistoricalPoint
import ir.alifaniani.hawklyx.domain.PriceChange24h
import ir.alifaniani.hawklyx.domain.PriceChangeCalculator
import ir.alifaniani.hawklyx.domain.PriceDirection
import ir.alifaniani.hawklyx.domain.SessionPriceCalculator
import ir.alifaniani.hawklyx.util.HawklyxTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Android Home-Screen Widget Provider for HAWKLYX Market Intelligence.
 * Displays the current Brent crude oil price along with an authentic 7-day historical chart.
 *
 * Guarantees:
 * - Uses strictly authentic edge historical data.
 * - Never synthesizes imaginary price curves or placeholder lines.
 * - Zero direct upstream OilPriceAPI requests.
 * - Coordinated with the single shared WorkManager periodic sync and 3-minute cooldown.
 */
class HawklyxIntelligenceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = HawklyxPreferences(context)
        val observation = prefs.getLastObservation()
        val status = prefs.getLastStatus()
        val historyResponse = prefs.getCachedHistoryResponse(HistoryRange.ONE_WEEK)

        for (appWidgetId in appWidgetIds) {
            updateWidget(
                context,
                appWidgetManager,
                appWidgetId,
                observation,
                status,
                historyResponse,
                isRefreshing = false
            )
        }

        // Asynchronously check if 7D history cache is stale (> 30 minutes) or empty
        val now = System.currentTimeMillis()
        val cachedTime = prefs.getCachedHistoryTimeMs()
        if (historyResponse == null || (now - cachedTime > 1_800_000L)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                HawklyxRepository.getInstance(context).loadHistory(HistoryRange.ONE_WEEK, forceRefresh = false)
            }
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
        const val REQUEST_CODE_OPEN_INTELLIGENCE = 1002
        const val REQUEST_CODE_REFRESH_INTELLIGENCE_BASE = 3000

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxIntelligenceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                val status = prefs.getLastStatus()
                val historyResponse = prefs.getCachedHistoryResponse(HistoryRange.ONE_WEEK)

                for (id in appWidgetIds) {
                    updateWidget(
                        context,
                        appWidgetManager,
                        id,
                        observation,
                        status,
                        historyResponse,
                        isRefreshing = false
                    )
                }
            }
        }

        fun updateRefreshingState(context: Context, isRefreshing: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxIntelligenceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                val status = if (isRefreshing) DataFreshnessStatus.REFRESHING else prefs.getLastStatus()
                val historyResponse = prefs.getCachedHistoryResponse(HistoryRange.ONE_WEEK)

                for (id in appWidgetIds) {
                    updateWidget(
                        context,
                        appWidgetManager,
                        id,
                        observation,
                        status,
                        historyResponse,
                        isRefreshing = isRefreshing
                    )
                }
            }
        }

        fun showCooldown(context: Context, remainingSeconds: Int) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxIntelligenceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                val prefs = HawklyxPreferences(context)
                val observation = prefs.getLastObservation()
                val historyResponse = prefs.getCachedHistoryResponse(HistoryRange.ONE_WEEK)

                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.hawklyx_intelligence_widget_layout)
                    setupClickIntents(context, views, id)
                    bindWidgetContent(context, views, observation, prefs.getLastStatus(), historyResponse, isRefreshing = false)
                    views.setTextViewText(R.id.widget_status_msg, "Cooldown (${remainingSeconds}s)")
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }

        internal fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            observation: BrentPriceObservation?,
            status: DataFreshnessStatus,
            historyResponse: HistoryResponseModel?,
            isRefreshing: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.hawklyx_intelligence_widget_layout)
            setupClickIntents(context, views, appWidgetId)
            bindWidgetContent(context, views, observation, status, historyResponse, isRefreshing)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupClickIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            // Main widget body click -> Launch MainActivity with explicit action and unique request code
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_FROM_WIDGET
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_INTELLIGENCE,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            // Refresh action click -> Broadcast ACTION_REFRESH with distinct request code
            val refreshIntent = Intent(context, HawklyxIntelligenceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH_INTELLIGENCE_BASE + appWidgetId,
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
            historyResponse: HistoryResponseModel?,
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

            // 1. Dominant Price & Currency presentation with dynamic market-state color
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

            // 2. Trend Metrics pill (from history summary if available)
            val summary = historyResponse?.summary
            if (summary != null) {
                val prefix = if (summary.changeAbsolute > 0) "+" else ""
                val trendText = "7D: ${prefix}${"%.2f".format(summary.changeAbsolute)} (${prefix}${"%.2f".format(summary.changePercent)}%)"
                views.setTextViewText(R.id.widget_change, trendText)
                val trendColor = if (summary.changeAbsolute >= 0) {
                    ContextCompat.getColor(context, R.color.hawklyx_emerald)
                } else {
                    ContextCompat.getColor(context, R.color.hawklyx_red)
                }
                views.setTextColor(R.id.widget_change, trendColor)
            } else {
                views.setTextViewText(R.id.widget_change, "7D TREND")
                views.setTextColor(R.id.widget_change, ContextCompat.getColor(context, R.color.hawklyx_text_secondary))
            }

            // 3. Status Badge with dynamic semantic background & text color
            views.setTextViewText(R.id.widget_status_badge, theme.badgeText)
            views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, theme.badgeTextColorRes))
            views.setInt(R.id.widget_status_badge, "setBackgroundResource", theme.badgeBgRes)

            // Calculate 24-hour price change from available history
            val allPoints = mutableListOf<HistoricalPoint>()
            historyResponse?.points?.let { allPoints.addAll(it) }
            allPoints.addAll(prefs.getHistoricalPoints())
            val priceChange = if (observation != null) {
                PriceChangeCalculator.calculate24hChange(observation.price, currentTs, allPoints)
            } else {
                PriceChange24h.unavailable()
            }

            // 4. Status Message with 24h change context if available
            val finalStatusMsg = if (priceChange.isAvailable) {
                "24h: ${priceChange.formattedWithArrow} • ${theme.statusMessage}"
            } else {
                theme.statusMessage
            }
            views.setTextViewText(R.id.widget_status_msg, finalStatusMsg)

            // 5. Chart Rendering or Honest Empty/Accumulation State
            val points = historyResponse?.points ?: emptyList()
            val isStaleChart = theme.isStaleOrOffline

            if (points.size >= 2) {
                val chartBitmap = WidgetChartRenderer.renderTrendChart(
                    points = points,
                    widthPx = 640,
                    heightPx = 260,
                    isStale = isStaleChart,
                    isMarketClosed = theme.isMarketClosed,
                    sessionDirection = sessionDirection,
                    sessionStartEpochMs = sessionInfo.sessionStartEpochMs
                )
                if (chartBitmap != null) {
                    views.setViewVisibility(R.id.widget_chart_image, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_chart_empty_container, View.GONE)
                    views.setImageViewBitmap(R.id.widget_chart_image, chartBitmap)
                } else {
                    showEmptyChartState(
                        views,
                        context.getString(R.string.history_accumulating),
                        context.getString(R.string.history_empty_note)
                    )
                }
            } else if (isRefreshing) {
                showEmptyChartState(
                    views,
                    context.getString(R.string.history_loading),
                    "Retrieving 7-day hourly market history..."
                )
            } else if (points.size == 1) {
                showEmptyChartState(
                    views,
                    context.getString(R.string.history_accumulating),
                    "1 of 2 observations recorded • Trend curve requires 2 points"
                )
            } else {
                showEmptyChartState(
                    views,
                    context.getString(R.string.history_no_data),
                    "Awaiting historical observations from edge service"
                )
            }
        }

        private fun showEmptyChartState(views: RemoteViews, title: String, subtitle: String) {
            views.setViewVisibility(R.id.widget_chart_image, View.GONE)
            views.setViewVisibility(R.id.widget_chart_empty_container, View.VISIBLE)
            views.setTextViewText(R.id.widget_chart_empty_title, title)
            views.setTextViewText(R.id.widget_chart_empty_subtitle, subtitle)
        }

        private fun formatObservationTime(asOfIso: String): String {
            if (asOfIso.isBlank()) return "Recently"
            return HawklyxTimeUtils.formatIsoToShortTime(asOfIso)
        }
    }
}
