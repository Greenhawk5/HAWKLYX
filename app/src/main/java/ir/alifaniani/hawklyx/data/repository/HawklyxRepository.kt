package ir.alifaniani.hawklyx.data.repository

import android.content.Context
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import ir.alifaniani.hawklyx.data.remote.HawklyxApiClient
import ir.alifaniani.hawklyx.data.schedule.UtcRefreshSchedule
import ir.alifaniani.hawklyx.widget.HawklyxWidgetProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Brent Crude price data.
 * Coordinates local caching, network synchronization, cooldown enforcement,
 * request deduplication, history persistence, and widget notifications.
 */
class HawklyxRepository(
    private val context: Context,
    private val preferences: HawklyxPreferences = HawklyxPreferences(context),
    private val apiClient: HawklyxApiClient = HawklyxApiClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val refreshMutex = Mutex()
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<PriceUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): PriceUiState {
        val cached = preferences.getLastObservation()
        val status = preferences.getLastStatus()
        val nextRefresh = preferences.getNextRefreshAllowedTimeMs()
        val history = preferences.getHistoricalPoints()
        val now = System.currentTimeMillis()

        val cooldownSec = if (nextRefresh > now) ((nextRefresh - now) / 1000).toInt() else 0
        val nextCadenceMs = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(now)

        return if (cached != null) {
            PriceUiState(
                observation = cached,
                status = if (status == DataFreshnessStatus.NO_DATA) DataFreshnessStatus.STALE else status,
                message = "Displaying verified cached price observation",
                isRefreshing = false,
                nextRefreshAllowedTimeMs = nextRefresh,
                cooldownRemainingSeconds = cooldownSec,
                nextScheduledCadenceTimeMs = nextCadenceMs,
                cadenceRemainingSeconds = cadenceSec,
                history = history,
                lastSyncAttemptTimeMs = preferences.getLastFetchTimeMs(),
                lastSyncSuccess = true
            )
        } else {
            PriceUiState(
                observation = null,
                status = DataFreshnessStatus.NO_DATA,
                message = "Awaiting first synchronization",
                isRefreshing = false,
                nextRefreshAllowedTimeMs = nextRefresh,
                cooldownRemainingSeconds = cooldownSec,
                nextScheduledCadenceTimeMs = nextCadenceMs,
                cadenceRemainingSeconds = cadenceSec,
                history = history,
                lastSyncAttemptTimeMs = 0L,
                lastSyncSuccess = false
            )
        }
    }

    /**
     * Updates countdown timer values derived deterministically from target epoch timestamps.
     * Prevents clock drift without issuing network requests.
     */
    fun updateCountdowns(nowMs: Long = System.currentTimeMillis()) {
        _uiState.update { current ->
            val nextRefresh = current.nextRefreshAllowedTimeMs
            val cooldownSec = if (nextRefresh > nowMs) ((nextRefresh - nowMs) / 1000).toInt() else 0
            val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(nowMs)
            val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(nowMs)

            current.copy(
                cooldownRemainingSeconds = cooldownSec,
                nextScheduledCadenceTimeMs = nextCadence,
                cadenceRemainingSeconds = cadenceSec
            )
        }
    }

    /**
     * Synchronizes the latest price observation from the edge service.
     * Enforces the 3-minute cooldown on manual forced refreshes and deduplicates concurrent calls.
     */
    suspend fun refreshPrice(force: Boolean = false): PriceUiState {
        return withContext(ioDispatcher) {
            refreshMutex.withLock {
                val now = System.currentTimeMillis()
                val nextAllowed = preferences.getNextRefreshAllowedTimeMs()

                // Check manual refresh cooldown
                if (force && now < nextAllowed) {
                    val remainingSec = ((nextAllowed - now) / 1000).toInt().coerceAtLeast(1)
                    _uiState.update { current ->
                        current.copy(
                            message = "Refresh cooldown active (${remainingSec}s remaining). Showing latest available price.",
                            cooldownRemainingSeconds = remainingSec,
                            isRefreshing = false
                        )
                    }
                    return@withContext _uiState.value
                }

                // Reflect refreshing state while strictly preserving visible price and history
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        message = "Retrieving Brent crude price from edge service..."
                    )
                }
                HawklyxWidgetProvider.notifyDataChanged(context)

                val result = apiClient.fetchLatestPrice(force)
                result.fold(
                    onSuccess = { networkResponse ->
                        val obs = networkResponse.observation
                        val status = networkResponse.status
                        val nextTime = networkResponse.nextRefreshAllowedTimeMs
                        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
                        val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(now)
                        val cooldownSec = if (nextTime > now) ((nextTime - now) / 1000).toInt() else 0

                        if (obs != null) {
                            preferences.saveObservation(obs, status, nextTime)
                            val updatedHistory = preferences.getHistoricalPoints()

                            _uiState.update {
                                PriceUiState(
                                    observation = obs,
                                    status = status,
                                    message = networkResponse.message.ifBlank { "Price updated successfully" },
                                    isRefreshing = false,
                                    nextRefreshAllowedTimeMs = nextTime,
                                    cooldownRemainingSeconds = cooldownSec,
                                    nextScheduledCadenceTimeMs = nextCadence,
                                    cadenceRemainingSeconds = cadenceSec,
                                    history = updatedHistory,
                                    lastSyncAttemptTimeMs = now,
                                    lastSyncSuccess = true
                                )
                            }
                        } else {
                            // Upstream returned response without observation (e.g. provider error or empty data)
                            val lastObs = preferences.getLastObservation()
                            preferences.saveStatusOnly(status, nextTime)
                            val updatedHistory = preferences.getHistoricalPoints()

                            _uiState.update {
                                PriceUiState(
                                    observation = lastObs,
                                    status = if (lastObs != null) DataFreshnessStatus.STALE else status,
                                    message = networkResponse.message.ifBlank { "Could not obtain new price quote. Preserving last valid price." },
                                    isRefreshing = false,
                                    nextRefreshAllowedTimeMs = nextTime,
                                    cooldownRemainingSeconds = cooldownSec,
                                    nextScheduledCadenceTimeMs = nextCadence,
                                    cadenceRemainingSeconds = cadenceSec,
                                    history = updatedHistory,
                                    lastSyncAttemptTimeMs = now,
                                    lastSyncSuccess = false
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        // Network failure, socket timeout, or edge unreachable:
                        // Strictly preserve last-known-good observation and full history
                        val lastObs = preferences.getLastObservation()
                        val updatedHistory = preferences.getHistoricalPoints()
                        val nextAllowedTime = preferences.getNextRefreshAllowedTimeMs()
                        val fallbackStatus = if (lastObs != null) DataFreshnessStatus.PROVIDER_UNAVAILABLE else DataFreshnessStatus.NO_DATA
                        val fallbackMessage = if (lastObs != null) {
                            "Network connection unavailable. Preserving last known price."
                        } else {
                            "Network connection failed. Please check connectivity and try again."
                        }
                        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
                        val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(now)
                        val cooldownSec = if (nextAllowedTime > now) ((nextAllowedTime - now) / 1000).toInt() else 0

                        _uiState.update {
                            PriceUiState(
                                observation = lastObs,
                                status = fallbackStatus,
                                message = fallbackMessage,
                                isRefreshing = false,
                                nextRefreshAllowedTimeMs = nextAllowedTime,
                                cooldownRemainingSeconds = cooldownSec,
                                nextScheduledCadenceTimeMs = nextCadence,
                                cadenceRemainingSeconds = cadenceSec,
                                history = updatedHistory,
                                lastSyncAttemptTimeMs = now,
                                lastSyncSuccess = false
                            )
                        }
                    }
                )

                HawklyxWidgetProvider.notifyDataChanged(context)
                _uiState.value
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HawklyxRepository? = null

        fun getInstance(context: Context): HawklyxRepository {
            return instance ?: synchronized(this) {
                instance ?: HawklyxRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
