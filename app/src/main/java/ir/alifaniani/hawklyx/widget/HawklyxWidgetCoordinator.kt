package ir.alifaniani.hawklyx.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.repository.HawklyxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single, unified coordinator for all HAWKLYX home-screen widgets.
 *
 * Guarantees:
 * - Synchronizes updates across both Market Snapshot and Market Intelligence widgets.
 * - Deduplicates refresh actions: multiple taps trigger a single background synchronization.
 * - Enforces the 3-minute cooldown across all widgets.
 * - Never hits upstream OilPriceAPI directly; coordinates exclusively with HawklyxRepository.
 */
object HawklyxWidgetCoordinator {

    private val refreshMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Determines whether any Market Intelligence widgets are currently active on the home screen.
     */
    fun hasIntelligenceWidgets(context: Context): Boolean {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HawklyxIntelligenceWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(component).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Notifies all active HAWKLYX widgets to re-bind from local storage.
     */
    fun notifyAllWidgets(context: Context) {
        HawklyxWidgetProvider.notifyDataChanged(context)
        HawklyxIntelligenceWidgetProvider.notifyDataChanged(context)
    }

    /**
     * Updates the refreshing state across all active HAWKLYX widgets.
     */
    fun updateRefreshingState(context: Context, isRefreshing: Boolean) {
        HawklyxWidgetProvider.updateRefreshingState(context, isRefreshing)
        HawklyxIntelligenceWidgetProvider.updateRefreshingState(context, isRefreshing)
    }

    /**
     * Centralized refresh handler triggered by ACTION_REFRESH from any widget instance.
     */
    fun handleRefreshAction(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
        val prefs = HawklyxPreferences(context)
        val now = System.currentTimeMillis()
        val nextAllowed = prefs.getNextRefreshAllowedTimeMs()

        // 1. Enforce strict 3-minute cooldown
        if (now < nextAllowed) {
            val remainingSec = ((nextAllowed - now) / 1000).toInt().coerceAtLeast(1)
            HawklyxWidgetProvider.showCooldown(context, remainingSec)
            HawklyxIntelligenceWidgetProvider.showCooldown(context, remainingSec)
            pendingResult?.finish()
            return
        }

        // Deduplicate: if a refresh is already actively in-flight, ignore duplicate clicks
        if (!refreshMutex.tryLock()) {
            pendingResult?.finish()
            return
        }

        // 2. Immediately reflect syncing state across both widgets
        updateRefreshingState(context, isRefreshing = true)

        // 3. Asynchronously execute synchronized refresh
        scope.launch {
            try {
                val repository = HawklyxRepository.getInstance(context)
                // Synchronize price and authentic history if widgets active
                repository.refreshPrice(force = true)
            } catch (_: Exception) {
                // Network or repository exception handled; ensure widgets are updated with last-known good state
            } finally {
                // Finalize and broadcast fresh data to both widgets
                notifyAllWidgets(context)
                refreshMutex.unlock()
                pendingResult?.finish()
            }
        }
    }
}
