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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, HawklyxWidgetProvider::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val prefs = HawklyxPreferences(context)
            val observation = prefs.getLastObservation()
            val now = System.currentTimeMillis()
            val nextAllowed = prefs.getNextRefreshAllowedTimeMs()

            // Respect 3-minute manual cooldown in widget
            if (now < nextAllowed) {
                val remainingSec = ((nextAllowed - now) / 1000).toInt().coerceAtLeast(1)
                for (id in allIds) {
                    val views = RemoteViews(context.packageName, R.layout.hawklyx_widget_layout)
                    setupClickIntents(context, views, id)
                    bindWidgetContent(context, views, observation, prefs.getLastStatus(), isRefreshing = false)
                    views.setTextViewText(R.id.widget_status_msg, "Cooldown (${remainingSec}s)")
                    appWidgetManager.updateAppWidget(id, views)
                }
                return
            }

            // Immediately reflect refreshing state on screen
            for (id in allIds) {
                updateWidget(context, appWidgetManager, id, observation, DataFreshnessStatus.REFRESHING, isRefreshing = true)
            }

            // Asynchronously execute refresh via repository
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                HawklyxRepository.getInstance(context).refreshPrice(force = true)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "ir.alifaniani.hawklyx.ACTION_REFRESH"

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
            // Setup Main Click -> Open MainActivity
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            // Setup Refresh Button Click -> Broadcast ACTION_REFRESH
            val refreshIntent = Intent(context, HawklyxWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
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
            // Price & Currency display
            if (observation != null) {
                views.setTextViewText(R.id.widget_price, observation.formatted)
                views.setTextViewText(R.id.widget_unit, observation.unit)
                val formattedTime = formatObservationTime(observation.asOf)
                views.setTextViewText(R.id.widget_updated, "Observed: $formattedTime")
            } else {
                views.setTextViewText(R.id.widget_price, "--.--")
                views.setTextViewText(R.id.widget_unit, context.getString(R.string.unit_usd_bbl))
                views.setTextViewText(R.id.widget_updated, "Awaiting first sync")
            }

            // Status Badge & Footer
            if (isRefreshing) {
                views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_refreshing))
                views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_emerald))
                views.setTextViewText(R.id.widget_status_msg, "Syncing...")
            } else {
                when (status) {
                    DataFreshnessStatus.FRESH -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_fresh))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_emerald))
                        views.setTextViewText(R.id.widget_status_msg, context.getString(R.string.tap_to_refresh))
                    }
                    DataFreshnessStatus.STALE -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_stale))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_amber))
                        views.setTextViewText(R.id.widget_status_msg, context.getString(R.string.tap_to_refresh))
                    }
                    DataFreshnessStatus.QUOTA_EXHAUSTED -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_quota_limit))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_red))
                        views.setTextViewText(R.id.widget_status_msg, "Daily limit")
                    }
                    DataFreshnessStatus.PROVIDER_UNAVAILABLE, DataFreshnessStatus.INVALID_RESPONSE -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_unavailable))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_amber))
                        views.setTextViewText(R.id.widget_status_msg, "Offline")
                    }
                    DataFreshnessStatus.NO_DATA -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_no_data))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_text_muted))
                        views.setTextViewText(R.id.widget_status_msg, context.getString(R.string.tap_to_refresh))
                    }
                    DataFreshnessStatus.REFRESHING -> {
                        views.setTextViewText(R.id.widget_status_badge, context.getString(R.string.status_refreshing))
                        views.setTextColor(R.id.widget_status_badge, ContextCompat.getColor(context, R.color.hawklyx_emerald))
                        views.setTextViewText(R.id.widget_status_msg, "Syncing...")
                    }
                }
            }
        }

        private fun formatObservationTime(asOfIso: String): String {
            if (asOfIso.isBlank()) return "Recently"
            return try {
                val instant = Instant.parse(asOfIso)
                val formatter = DateTimeFormatter.ofPattern("HH:mm (z)", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            } catch (_: Exception) {
                asOfIso.take(16).replace("T", " ")
            }
        }
    }
}
