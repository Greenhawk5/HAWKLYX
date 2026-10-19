package ir.alifaniani.hawklyx.data.repository

import android.content.Context
import ir.alifaniani.hawklyx.data.local.HawklyxPreferences
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.HistoricalObservationPoint
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryResponseModel
import ir.alifaniani.hawklyx.data.model.HistoryUiState
import ir.alifaniani.hawklyx.data.model.PriceUiState
import ir.alifaniani.hawklyx.data.remote.HawklyxApiClient
import ir.alifaniani.hawklyx.data.schedule.UtcRefreshSchedule
import ir.alifaniani.hawklyx.widget.HawklyxWidgetCoordinator
import ir.alifaniani.hawklyx.domain.BrentMarketCalendar
import ir.alifaniani.hawklyx.domain.HistoricalPoint
import ir.alifaniani.hawklyx.domain.PriceChange24h
import ir.alifaniani.hawklyx.domain.PriceChangeCalculator
import ir.alifaniani.hawklyx.domain.SessionPriceCalculator
import ir.alifaniani.hawklyx.domain.SessionPriceState
import ir.alifaniani.hawklyx.data.model.MarketStateModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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

    private val _historyUiState = MutableStateFlow(HistoryUiState())
    val historyUiState: StateFlow<HistoryUiState> = _historyUiState.asStateFlow()

    private data class HistoryCacheEntry(
        val response: HistoryResponseModel,
        val cachedAtMs: Long
    )
    private val historyCache = ConcurrentHashMap<HistoryRange, HistoryCacheEntry>()
    private val historyMutex = Mutex()
    @Volatile
    private var activeHistoryRange: HistoryRange = HistoryRange.ONE_MONTH

    private fun computePriceChange(
        observation: BrentPriceObservation?,
        localHistory: List<HistoricalObservationPoint>,
        fallbackNowMs: Long
    ): PriceChange24h {
        if (observation == null || observation.price <= 0.0) {
            return PriceChange24h.unavailable()
        }
        val currentTimestampMs = if (observation.asOf.isNotBlank()) {
            try { Instant.parse(observation.asOf).toEpochMilli() } catch (_: Exception) { fallbackNowMs }
        } else {
            fallbackNowMs
        }

        val allPoints = mutableListOf<HistoricalPoint>()
        preferences.getCachedHistoryResponse(HistoryRange.ONE_WEEK)?.points?.let {
            allPoints.addAll(it)
        }
        allPoints.addAll(localHistory)

        return PriceChangeCalculator.calculate24hChange(
            currentPrice = observation.price,
            currentTimestampMs = currentTimestampMs,
            points = allPoints
        )
    }

    private fun computeSessionPriceState(
        observation: BrentPriceObservation?,
        localHistory: List<HistoricalObservationPoint>,
        status: DataFreshnessStatus,
        marketState: MarketStateModel?,
        fallbackNowMs: Long
    ): SessionPriceState {
        if (observation == null || observation.price <= 0.0) {
            return SessionPriceState.unavailable()
        }
        val currentTimestampMs = if (observation.asOf.isNotBlank()) {
            try { Instant.parse(observation.asOf).toEpochMilli() } catch (_: Exception) { fallbackNowMs }
        } else {
            fallbackNowMs
        }

        val sessionInfo = BrentMarketCalendar.getSessionInfo(currentTimestampMs)
        val isMarketOpen = (marketState?.isOpen ?: (status != DataFreshnessStatus.MARKET_CLOSED)) && sessionInfo.isMarketOpen

        if (!isMarketOpen || status == DataFreshnessStatus.MARKET_CLOSED) {
            return SessionPriceCalculator.calculateSessionPrice(
                currentPrice = observation.price,
                referencePrice = preferences.getSessionReferencePrice(),
                referenceTimestampMs = preferences.getSessionReferenceTimestampMs(),
                sessionId = sessionInfo.sessionId,
                isMarketOpen = false,
                isStale = false
            )
        }

        val isStale = observation.isStale || status == DataFreshnessStatus.STALE ||
                status == DataFreshnessStatus.PROVIDER_UNAVAILABLE || status == DataFreshnessStatus.INVALID_RESPONSE

        val savedSessionId = preferences.getSessionId()
        var refPrice = preferences.getSessionReferencePrice()
        var refTimestampMs = preferences.getSessionReferenceTimestampMs()

        if (savedSessionId != sessionInfo.sessionId || refPrice <= 0.0) {
            val allPoints = mutableListOf<HistoricalPoint>()
            preferences.getCachedHistoryResponse(HistoryRange.ONE_WEEK)?.points?.let {
                allPoints.addAll(it)
            }
            allPoints.addAll(localHistory)

            val baselinePoint = SessionPriceCalculator.findSessionReferencePoint(
                sessionStartEpochMs = sessionInfo.sessionStartEpochMs,
                points = allPoints
            )

            if (baselinePoint != null) {
                refPrice = baselinePoint.price
                refTimestampMs = baselinePoint.timestampMs
                preferences.saveSessionReference(sessionInfo.sessionId, refPrice, refTimestampMs)
            } else if (savedSessionId != sessionInfo.sessionId && observation.price > 0.0) {
                refPrice = observation.price
                refTimestampMs = currentTimestampMs
                preferences.saveSessionReference(sessionInfo.sessionId, refPrice, refTimestampMs)
            }
        }

        return SessionPriceCalculator.calculateSessionPrice(
            currentPrice = observation.price,
            referencePrice = refPrice,
            referenceTimestampMs = refTimestampMs,
            sessionId = sessionInfo.sessionId,
            isMarketOpen = true,
            isStale = isStale
        )
    }

    private fun loadInitialState(): PriceUiState {
        val cached = preferences.getLastObservation()
        val status = preferences.getLastStatus()
        val nextRefresh = preferences.getNextRefreshAllowedTimeMs()
        val history = preferences.getHistoricalPoints()
        val now = System.currentTimeMillis()

        val cooldownSec = if (nextRefresh > now) ((nextRefresh - now) / 1000).toInt() else 0
        val nextCadenceMs = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
        val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(now)
        val priceChange = computePriceChange(cached, history, now)
        val sessionPrice = computeSessionPriceState(cached, history, status, null, now)

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
                lastSyncSuccess = true,
                priceChange24h = priceChange,
                sessionPriceState = sessionPrice
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
                lastSyncSuccess = false,
                priceChange24h = PriceChange24h.unavailable(),
                sessionPriceState = SessionPriceState.unavailable()
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
     * Automatic synchronization on application launch or background schedule.
     * Uses non-forced mode (force = false) to leverage cached quotes if fresh.
     * Does NOT mark isRefreshing = true, does NOT trigger manual refresh feedback,
     * and does NOT consume user refresh quota/cooldown.
     */
    suspend fun synchronizeOnLaunch(): PriceUiState {
        return refreshPrice(force = false)
    }

    /**
     * Synchronizes the latest price observation from the edge service.
     * Enforces the 3-minute cooldown on manual forced refreshes and deduplicates concurrent calls.
     *
     * Invariants:
     * - Only user-initiated manual refreshes (force = true) set isRefreshing = true.
     * - Automatic/background sync (force = false) performs silent updates without manual UI lock.
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

                // Reflect refreshing state ONLY for manual user-initiated refresh
                if (force) {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = true,
                            message = "Retrieving Brent crude price from edge service..."
                        )
                    }
                    HawklyxWidgetCoordinator.updateRefreshingState(context, isRefreshing = true)
                }

                val result = apiClient.fetchLatestPrice(force)
                result.fold(
                    onSuccess = { networkResponse ->
                        val obs = networkResponse.observation
                        val status = networkResponse.status
                        val nextTime = networkResponse.nextRefreshAllowedTimeMs
                        val nextCadence = UtcRefreshSchedule.calculateNextCadenceEpochMs(now)
                        val cadenceSec = UtcRefreshSchedule.getRemainingSeconds(now)
                        val cooldownSec = if (nextTime > now) ((nextTime - now) / 1000).toInt() else 0

                        val isMarketOpen = networkResponse.marketState?.isOpen ?: (status != DataFreshnessStatus.MARKET_CLOSED)

                        if (obs != null) {
                            preferences.saveObservation(obs, status, nextTime, isMarketOpen)
                            val updatedHistory = preferences.getHistoricalPoints()
                            val priceChange = computePriceChange(obs, updatedHistory, now)
                            val sessionPrice = computeSessionPriceState(obs, updatedHistory, status, networkResponse.marketState, now)

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
                                    lastSyncSuccess = true,
                                    marketState = networkResponse.marketState,
                                    priceChange24h = priceChange,
                                    sessionPriceState = sessionPrice
                                )
                            }
                        } else {
                            // Upstream returned response without observation (e.g. provider error or empty data)
                            val lastObs = preferences.getLastObservation()
                            preferences.saveStatusOnly(status, nextTime, isMarketOpen)
                            val updatedHistory = preferences.getHistoricalPoints()
                            val priceChange = computePriceChange(lastObs, updatedHistory, now)
                            val sessionPrice = computeSessionPriceState(lastObs, updatedHistory, if (lastObs != null) DataFreshnessStatus.STALE else status, networkResponse.marketState, now)

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
                                    lastSyncSuccess = false,
                                    marketState = networkResponse.marketState,
                                    priceChange24h = priceChange,
                                    sessionPriceState = sessionPrice
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
                        val priceChange = computePriceChange(lastObs, updatedHistory, now)
                        val sessionPrice = computeSessionPriceState(lastObs, updatedHistory, fallbackStatus, _uiState.value.marketState, now)

                        _uiState.update { current ->
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
                                lastSyncSuccess = false,
                                marketState = current.marketState,
                                priceChange24h = priceChange,
                                sessionPriceState = sessionPrice
                            )
                        }
                    }
                )

                if (HawklyxWidgetCoordinator.hasIntelligenceWidgets(context)) {
                    loadHistory(HistoryRange.ONE_WEEK, forceRefresh = force)
                }
                HawklyxWidgetCoordinator.notifyAllWidgets(context)
                _uiState.value
            }
        }
    }

    /**
     * Loads historical Brent crude prices for the specified range.
     * Implements in-memory TTL caching (5 minutes), prevents duplicate in-flight requests,
     * and handles rapid range switching safely by only applying the latest requested range.
     * Strictly preserves cached data on network failures, marking it as stale.
     */
    suspend fun loadHistory(range: HistoryRange, forceRefresh: Boolean = false): HistoryUiState {
        return withContext(ioDispatcher) {
            activeHistoryRange = range
            val now = System.currentTimeMillis()
            val cached = historyCache[range]
            val isCacheFresh = cached != null && (now - cached.cachedAtMs < HISTORY_CACHE_TTL_MS)

            // Return cached response immediately if fresh and not forced
            if (isCacheFresh && !forceRefresh) {
                _historyUiState.update {
                    it.copy(
                        selectedRange = range,
                        isLoading = false,
                        isStale = false,
                        data = cached!!.response,
                        errorMessage = null
                    )
                }
                return@withContext _historyUiState.value
            }

            // Immediately show loading state while preserving previous data if available
            _historyUiState.update { current ->
                val existingData = cached?.response ?: if (current.selectedRange == range) current.data else null
                current.copy(
                    selectedRange = range,
                    isLoading = true,
                    isStale = false,
                    data = existingData,
                    errorMessage = null
                )
            }

            historyMutex.withLock {
                // Ensure active range didn't change while waiting for lock
                if (activeHistoryRange != range) {
                    return@withLock _historyUiState.value
                }

                val result = apiClient.fetchHistory(range = range)
                result.fold(
                    onSuccess = { response ->
                        historyCache[range] = HistoryCacheEntry(response, System.currentTimeMillis())
                        if (range == HistoryRange.ONE_WEEK) {
                            preferences.saveCachedHistoryResponse(response)
                            _uiState.update { current ->
                                current.copy(
                                    priceChange24h = computePriceChange(
                                        current.observation,
                                        current.history,
                                        System.currentTimeMillis()
                                    ),
                                    sessionPriceState = computeSessionPriceState(
                                        current.observation,
                                        current.history,
                                        current.status,
                                        current.marketState,
                                        System.currentTimeMillis()
                                    )
                                )
                            }
                            HawklyxWidgetCoordinator.notifyAllWidgets(context)
                        }
                        if (activeHistoryRange == range) {
                            _historyUiState.update {
                                it.copy(
                                    selectedRange = range,
                                    isLoading = false,
                                    isStale = false,
                                    data = response,
                                    errorMessage = null
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        val persistentCached = if (range == HistoryRange.ONE_WEEK) preferences.getCachedHistoryResponse(range) else null
                        if (activeHistoryRange == range) {
                            _historyUiState.update { current ->
                                val fallback = cached?.response ?: persistentCached ?: current.data
                                current.copy(
                                    selectedRange = range,
                                    isLoading = false,
                                    isStale = fallback != null,
                                    data = fallback,
                                    errorMessage = if (fallback != null) {
                                        "Displaying cached history (offline: ${error.message})"
                                    } else {
                                        "Could not load market history: ${error.message}"
                                    }
                                )
                            }
                        }
                    }
                )
                _historyUiState.value
            }
        }
    }

    companion object {
        const val HISTORY_CACHE_TTL_MS = 300_000L // 5 minutes in-memory TTL
        @Volatile
        private var instance: HawklyxRepository? = null

        fun getInstance(context: Context): HawklyxRepository {
            return instance ?: synchronized(this) {
                instance ?: HawklyxRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
