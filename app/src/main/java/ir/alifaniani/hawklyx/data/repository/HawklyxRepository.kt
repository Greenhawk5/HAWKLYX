package ir.alifaniani.hawklyx.data.repository

import android.content.Context
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import ir.alifaniani.hawklyx.data.remote.HawklyxApiClient
import ir.alifaniani.hawklyx.widget.HawklyxWidgetProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Single source of truth for Brent Crude price data.
 * Coordinates local caching, network synchronization, cooldown enforcement,
 * and widget notification.
 */
class HawklyxRepository(
    private val context: Context,
    private val preferences: HawklyxPreferences = HawklyxPreferences(context),
    private val apiClient: HawklyxApiClient = HawklyxApiClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<PriceUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): PriceUiState {
        val cached = preferences.getLastObservation()
        val status = preferences.getLastStatus()
        val nextRefresh = preferences.getNextRefreshAllowedTimeMs()
        val now = System.currentTimeMillis()
        val cooldownSec = if (nextRefresh > now) ((nextRefresh - now) / 1000).toInt() else 0

        return if (cached != null) {
            PriceUiState(
                observation = cached,
                status = if (status == DataFreshnessStatus.NO_DATA) DataFreshnessStatus.STALE else status,
                message = "Displaying cached price observation",
                isRefreshing = false,
                nextRefreshAllowedTimeMs = nextRefresh,
                cooldownRemainingSeconds = cooldownSec
            )
        } else {
            PriceUiState(
                observation = null,
                status = DataFreshnessStatus.NO_DATA,
                message = "Awaiting first synchronization",
                isRefreshing = false,
                nextRefreshAllowedTimeMs = nextRefresh,
                cooldownRemainingSeconds = cooldownSec
            )
        }
    }

    /**
     * Synchronizes the latest price observation from the edge service.
     * Enforces the 3-minute cooldown on manual forced refreshes.
     */
    suspend fun refreshPrice(force: Boolean = false): PriceUiState {
        return withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val nextAllowed = preferences.getNextRefreshAllowedTimeMs()

            // Check manual refresh cooldown
            if (force && now < nextAllowed) {
                val remainingSec = ((nextAllowed - now) / 1000).toInt()
                _uiState.update { current ->
                    current.copy(
                        message = "Refresh cooldown active (${remainingSec}s remaining). Showing latest available price.",
                        cooldownRemainingSeconds = remainingSec,
                        isRefreshing = false
                    )
                }
                return@withContext _uiState.value
            }

            // Set refreshing state while preserving the visible price
            _uiState.update { it.copy(isRefreshing = true, message = "Retrieving Brent crude price...") }
            HawklyxWidgetProvider.notifyDataChanged(context)

            val result = apiClient.fetchLatestPrice(force)
            result.fold(
                onSuccess = { networkResponse ->
                    val obs = networkResponse.observation
                    val status = networkResponse.status
                    val nextTime = networkResponse.nextRefreshAllowedTimeMs

                    if (obs != null) {
                        preferences.saveObservation(obs, status, nextTime)
                        _uiState.update {
                            PriceUiState(
                                observation = obs,
                                status = status,
                                message = networkResponse.message.ifBlank { "Price updated successfully" },
                                isRefreshing = false,
                                nextRefreshAllowedTimeMs = nextTime,
                                cooldownRemainingSeconds = 0
                            )
                        }
                    } else {
                        // Response had no observation (e.g. error from provider without cache)
                        val lastObs = preferences.getLastObservation()
                        preferences.saveStatusOnly(status, nextTime)
                        _uiState.update {
                            PriceUiState(
                                observation = lastObs,
                                status = if (lastObs != null) DataFreshnessStatus.STALE else status,
                                message = networkResponse.message.ifBlank { "Could not obtain new price quote" },
                                isRefreshing = false,
                                nextRefreshAllowedTimeMs = nextTime,
                                cooldownRemainingSeconds = 0
                            )
                        }
                    }
                },
                onFailure = { error ->
                    // Network or parsing failure: strictly preserve last-known-good observation
                    val lastObs = preferences.getLastObservation()
                    val fallbackStatus = if (lastObs != null) DataFreshnessStatus.PROVIDER_UNAVAILABLE else DataFreshnessStatus.NO_DATA
                    val message = "Network connection failed. ${if (lastObs != null) "Showing last known price." else "Please try again later."}"

                    _uiState.update {
                        PriceUiState(
                            observation = lastObs,
                            status = fallbackStatus,
                            message = message,
                            isRefreshing = false,
                            nextRefreshAllowedTimeMs = preferences.getNextRefreshAllowedTimeMs(),
                            cooldownRemainingSeconds = 0
                        )
                    }
                }
            )

            // Notify widget to redraw with updated state
            HawklyxWidgetProvider.notifyDataChanged(context)
            _uiState.value
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
