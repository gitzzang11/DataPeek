package com.datapeek.ui

import androidx.annotation.VisibleForTesting
import android.os.SystemClock
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.repository.UsageRepository
import com.datapeek.data.session.WebViewCookieBridge
import com.datapeek.data.util.daysUntilReset
import com.datapeek.domain.UsageObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

private const val FOREGROUND_REFRESH_MIN_INTERVAL_MILLIS = 30_000L

data class HomeUiState(
    val carrier: Carrier = Carrier.FREET_SKT,
    val snapshot: UsageSnapshot? = null,
    val isAuthenticated: Boolean = false,
    val sessionStatus: SessionStatus = SessionStatus.UNKNOWN,
    val isMockData: Boolean = false,
    val developerModeEnabled: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshMinutes: Long = 60L,
    val refreshDue: Boolean = false,
    val lastBackgroundAttempt: Instant? = null,
    val lastBackgroundResult: String? = null,
    val lastRefreshTrigger: String? = null,
    val usageHistory: List<UsageObservation> = emptyList(),
)

data class SnapshotDeliveryStatus(
    val repositoryCacheUpdated: Boolean,
    val homeUiUpdated: Boolean,
    val widgetUpdateRequested: Boolean,
)

internal class UsageViewModel(
    private val repository: UsageRepository,
    private val scope: CoroutineScope,
    private val freetUsageRefreshCoordinator: FreetUsageRefreshCoordinator? = null,
) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var lastForegroundRefreshStartedAt = Long.MIN_VALUE

    init {
        scope.launch {
            repository.developerModeEnabled.collect { enabled ->
                _state.update { it.copy(developerModeEnabled = enabled) }
            }
        }
        scope.launch {
            repository.trackedUsage.collect { (snapshot, history) ->
                _state.update {
                    it.copy(snapshot = snapshot, carrier = snapshot?.carrier ?: it.carrier, usageHistory = history)
                }
            }
        }
        scope.launch {
            repository.lastError.collect { message ->
                _state.update { current ->
                    if (current.isRefreshing) {
                        current
                    } else {
                        val status = message.toSessionStatusFromMessage()
                        current.copy(
                            errorMessage = message,
                            sessionStatus = status ?: current.sessionStatus,
                            isAuthenticated = if (status == SessionStatus.SESSION_EXPIRED) {
                                false
                            } else {
                                current.isAuthenticated
                            },
                        )
                    }
                }
            }
        }
        scope.launch {
            repository.autoRefreshEnabled.collect { enabled ->
                _state.update { it.copy(autoRefreshEnabled = enabled) }
            }
        }
        scope.launch {
            repository.autoRefreshMinutes().let { minutes ->
                _state.update { it.copy(autoRefreshMinutes = minutes) }
            }
        }
        scope.launch {
            repository.refreshDue.collect { due ->
                _state.update { it.copy(refreshDue = due) }
            }
        }
        scope.launch {
            repository.lastBackgroundAttempt.collect { attemptedAt ->
                _state.update { it.copy(lastBackgroundAttempt = attemptedAt) }
            }
        }
        scope.launch {
            repository.lastBackgroundResult.collect { result ->
                _state.update { it.copy(lastBackgroundResult = result) }
            }
        }
        scope.launch {
            repository.lastRefreshTrigger.collect { trigger ->
                _state.update { it.copy(lastRefreshTrigger = trigger) }
            }
        }
        scope.launch {
            val authenticated = repository.isAuthenticated()
            val cachedSnapshot = repository.snapshot.first()
            val refreshDue = repository.isRefreshDue()
            val intervalMinutes = repository.autoRefreshMinutes()
            val decision = decideForegroundRefresh(
                hasCachedSnapshot = cachedSnapshot != null,
                locallyAuthenticated = authenticated,
                sessionExpired = false,
                refreshDue = refreshDue,
                lastSuccessfulRefresh = cachedSnapshot?.fetchedAt,
                now = Instant.now(),
                intervalMinutes = intervalMinutes,
            )
            _state.update {
                it.copy(
                    snapshot = it.snapshot ?: cachedSnapshot,
                    carrier = cachedSnapshot?.carrier ?: it.carrier,
                    isAuthenticated = authenticated,
                    sessionStatus = if (decision == ForegroundRefreshDecision.SKIP_RECENT) {
                        SessionStatus.AUTHENTICATED
                    } else {
                        initialSessionStatus(
                            hasCachedSnapshot = cachedSnapshot != null,
                            locallyAuthenticated = authenticated,
                        )
                    },
                    errorMessage = if (decision != ForegroundRefreshDecision.SKIP_RECENT) null else it.errorMessage,
                )
            }
            when (decision) {
                ForegroundRefreshDecision.REFRESH_NO_CACHE,
                ForegroundRefreshDecision.REFRESH_DUE,
                ForegroundRefreshDecision.REFRESH_STALE,
                -> refresh(FreetUsageRefreshOrigin.SESSION_RECHECK)

                ForegroundRefreshDecision.RECHECK_SESSION -> refresh(FreetUsageRefreshOrigin.SESSION_RECHECK)
                ForegroundRefreshDecision.SKIP_RECENT,
                ForegroundRefreshDecision.SKIP_SESSION_EXPIRED,
                ForegroundRefreshDecision.SKIP_UNAUTHENTICATED,
                -> Unit
            }
        }
    }

    fun refresh() = refresh(FreetUsageRefreshOrigin.MANUAL)

    private fun refresh(origin: FreetUsageRefreshOrigin) {
        if (_state.value.isRefreshing) return
        if (
            origin in setOf(FreetUsageRefreshOrigin.FOREGROUND, FreetUsageRefreshOrigin.SESSION_RECHECK) &&
            !shouldStartForegroundRefresh(
                lastStartedAt = lastForegroundRefreshStartedAt,
                now = SystemClock.elapsedRealtime(),
                minimumIntervalMillis = FOREGROUND_REFRESH_MIN_INTERVAL_MILLIS,
            )
        ) return
        if (origin in setOf(FreetUsageRefreshOrigin.FOREGROUND, FreetUsageRefreshOrigin.SESSION_RECHECK)) {
            lastForegroundRefreshStartedAt = SystemClock.elapsedRealtime()
        }
        _state.update {
            it.copy(
                isRefreshing = true,
                sessionStatus = SessionStatus.CHECKING,
                errorMessage = null,
            )
        }
        scope.launch { repository.recordRefreshTrigger(origin.name) }
        if (freetUsageRefreshCoordinator != null && !_state.value.isMockData) {
            if (freetUsageRefreshCoordinator.request(origin) == null) {
                if (freetUsageRefreshCoordinator.pendingRequest.value == null) {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
            return
        }
        scope.launch {
            repository.refresh().fold(
                onSuccess = { snapshot ->
                    repository.requestWidgetUpdate()
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            carrier = snapshot.carrier,
                            isAuthenticated = true,
                            sessionStatus = SessionStatus.AUTHENTICATED,
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    val usageError = (error as? UsageException)?.error
                    val requiresLogin = usageError?.requiresLogin() == true
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            sessionStatus = usageError?.toSessionStatus() ?: SessionStatus.SERVER_ERROR,
                            isAuthenticated = if (requiresLogin) false else it.isAuthenticated,
                            errorMessage = error.toDisplayMessage(),
                        )
                    }
                },
            )
        }
    }

    fun refreshIfAuthenticated() {
        scope.launch {
            val current = _state.value
            if (current.sessionStatus == SessionStatus.SESSION_EXPIRED) return@launch
            val snapshot = current.snapshot ?: repository.snapshot.first()
            val locallyAuthenticated = repository.isAuthenticated()
            val decision = decideForegroundRefresh(
                hasCachedSnapshot = snapshot != null,
                locallyAuthenticated = locallyAuthenticated,
                sessionExpired = false,
                refreshDue = repository.isRefreshDue(),
                lastSuccessfulRefresh = snapshot?.fetchedAt,
                now = Instant.now(),
                intervalMinutes = repository.autoRefreshMinutes(),
            )
            when (decision) {
                ForegroundRefreshDecision.REFRESH_NO_CACHE,
                ForegroundRefreshDecision.REFRESH_DUE,
                ForegroundRefreshDecision.REFRESH_STALE,
                -> refresh(FreetUsageRefreshOrigin.FOREGROUND)

                ForegroundRefreshDecision.RECHECK_SESSION -> refresh(FreetUsageRefreshOrigin.SESSION_RECHECK)
                ForegroundRefreshDecision.SKIP_RECENT,
                ForegroundRefreshDecision.SKIP_SESSION_EXPIRED,
                ForegroundRefreshDecision.SKIP_UNAUTHENTICATED,
                -> Unit
            }
        }
    }

    fun refreshFromWidget() = refresh(FreetUsageRefreshOrigin.WIDGET)

    fun useMockData() {
        repository.useMockProvider()
        _state.update { it.afterMockModeEnabled() }
        refresh()
    }

    fun clearMockData() {
        scope.launch {
            repository.clearMockProvider()
            _state.update {
                it.afterMockModeDisabled().copy(isAuthenticated = repository.isAuthenticated())
            }
            refresh()
        }
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        val wasMockData = _state.value.isMockData
        _state.update {
            it.copy(
                developerModeEnabled = enabled,
                isMockData = if (enabled) it.isMockData else false,
                snapshot = if (!enabled && wasMockData) null else it.snapshot,
                isAuthenticated = if (!enabled && wasMockData) false else it.isAuthenticated,
            )
        }
        scope.launch {
            runCatching {
                if (!enabled && wasMockData) repository.clearMockProvider()
                repository.setDeveloperModeEnabled(enabled)
            }.onFailure {
                _state.update { current -> current.copy(developerModeEnabled = !enabled) }
            }
        }
    }

    fun clearCachedSnapshot() {
        scope.launch {
            repository.clearCachedSnapshot()
            _state.update { it.copy(snapshot = null, errorMessage = null) }
        }
    }

    fun loginCompleted() {
        scope.launch {
            repository.markAuthenticated()
            _state.update { it.copy(isAuthenticated = true, errorMessage = null) }
            _state.update { it.copy(sessionStatus = SessionStatus.CHECKING) }
            refresh(FreetUsageRefreshOrigin.LOGIN)
        }
    }

    fun handleFreetApiResult(
        requestId: Long,
        probe: FreetUsageApiProbeResult,
        onDeliveryStatus: (SnapshotDeliveryStatus) -> Unit = {},
    ) {
        val coordinator = freetUsageRefreshCoordinator ?: return
        if (coordinator.pendingRequest.value?.id != requestId) return
        scope.launch {
            var repositoryUpdated = false
            var finalFailure = probe.toRefreshFailureReason()
            try {
                if (probe.isSuccessfulUsage()) {
                    val snapshot = probe.snapshot ?: return@launch
                    repositoryUpdated = deliverSnapshot(snapshot, onDeliveryStatus)
                    finalFailure = if (repositoryUpdated) {
                        FreetUsageRefreshFailureReason.NONE
                    } else {
                        FreetUsageRefreshFailureReason.API_REQUEST_FAILED
                    }
                } else {
                    val error = probe.toUsageErrorOrNull() ?: UsageError.ApiRequestFailed
                    repository.recordError(error)
                    repository.requestWidgetUpdate()
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            sessionStatus = error.toSessionStatus(),
                            isAuthenticated = if (error.requiresLogin()) false else it.isAuthenticated,
                            errorMessage = error.toDisplayMessage(),
                        )
                    }
                }
            } catch (error: Throwable) {
                repositoryUpdated = false
                finalFailure = FreetUsageRefreshFailureReason.API_REQUEST_FAILED
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        sessionStatus = SessionStatus.SERVER_ERROR,
                        errorMessage = error.toDisplayMessage(),
                    )
                }
            } finally {
                coordinator.onRepositoryUpdated(requestId, repositoryUpdated)
                coordinator.onFinalRefreshResult(
                    requestId = requestId,
                    succeeded = repositoryUpdated,
                    failure = finalFailure,
                )
                coordinator.complete(requestId)
            }
        }
    }

    fun loginCompletedWithSnapshot(
        snapshot: UsageSnapshot,
        onDeliveryStatus: (SnapshotDeliveryStatus) -> Unit = {},
    ) {
        scope.launch { deliverSnapshot(snapshot, onDeliveryStatus) }
    }

    fun logout() {
        scope.launch {
            val developerModeEnabled = _state.value.developerModeEnabled
            freetUsageRefreshCoordinator?.pendingRequest?.value?.let { request ->
                freetUsageRefreshCoordinator.cancel(request.id)
            }
            repository.logout()
            WebViewCookieBridge.clearAllCookies()
            repository.requestWidgetUpdate()
            _state.value = HomeUiState(
                developerModeEnabled = developerModeEnabled,
                sessionStatus = SessionStatus.UNKNOWN,
                autoRefreshEnabled = _state.value.autoRefreshEnabled,
                autoRefreshMinutes = _state.value.autoRefreshMinutes,
            )
        }
    }

    fun setRefreshMinutes(minutes: Long) {
        scope.launch {
            repository.setAutoRefreshMinutes(minutes)
            if (minutes <= 0L) {
                repository.setAutoRefreshEnabled(false)
            }
            _state.update {
                it.copy(
                    autoRefreshMinutes = minutes.coerceAtLeast(0L),
                    autoRefreshEnabled = if (minutes <= 0L) false else it.autoRefreshEnabled,
                )
            }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        scope.launch {
            if (enabled && repository.autoRefreshMinutes() <= 0L) {
                repository.setAutoRefreshMinutes(60L)
                _state.update { it.copy(autoRefreshMinutes = 60L) }
            }
            repository.setAutoRefreshEnabled(enabled)
            _state.update { it.copy(autoRefreshEnabled = enabled) }
        }
    }

    @VisibleForTesting
    fun applySnapshot(snapshot: UsageSnapshot) {
        _state.update { it.copy(snapshot = snapshot, isAuthenticated = true) }
    }

    private fun Throwable.toDisplayMessage() = when (this) {
        is UsageException -> error.toDisplayMessage()
        else -> "새로고침에 실패했습니다. 기존 데이터를 유지합니다."
    }

    private fun UsageError.toDisplayMessage() = when (this) {
        UsageError.NotAuthenticated -> "로그인이 필요합니다."
        UsageError.SessionExpired -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
        UsageError.NetworkUnavailable -> "네트워크에 연결할 수 없습니다."
        is UsageError.HttpError -> "통신사 서버 오류(${code})"
        is UsageError.ParseError -> "응답을 해석하지 못했습니다."
        UsageError.ApiTimeout -> "사용량 확인 시간이 초과되었습니다."
        UsageError.ApiBodyReadFailed -> "사용량 응답을 읽지 못했습니다."
        UsageError.ApiRequestFailed -> "사용량 서버에 연결하지 못했습니다."
        UsageError.UnsupportedResponse -> "인증 후 사용량 요청은 Network trace 연결이 필요합니다."
        UsageError.RateLimited -> "잠시 후 다시 시도해 주세요."
    }

    private suspend fun deliverSnapshot(
        snapshot: UsageSnapshot,
        onDeliveryStatus: (SnapshotDeliveryStatus) -> Unit,
    ): Boolean {
        val cacheUpdated = runCatching {
            repository.markAuthenticated()
            repository.saveSnapshot(snapshot)
        }.isSuccess
        if (!cacheUpdated) {
            _state.update {
                it.copy(
                    isRefreshing = false,
                    sessionStatus = SessionStatus.SERVER_ERROR,
                    errorMessage = "사용량을 저장하지 못했습니다.",
                )
            }
            onDeliveryStatus(
                SnapshotDeliveryStatus(
                    repositoryCacheUpdated = false,
                    homeUiUpdated = false,
                    widgetUpdateRequested = false,
                ),
            )
            return false
        }
        _state.update {
            it.copy(
                snapshot = snapshot,
                carrier = snapshot.carrier,
                isAuthenticated = true,
                sessionStatus = SessionStatus.AUTHENTICATED,
                isRefreshing = false,
                errorMessage = null,
            )
        }
        val widgetUpdated = repository.requestWidgetUpdate()
        onDeliveryStatus(
            SnapshotDeliveryStatus(
                repositoryCacheUpdated = true,
                homeUiUpdated = true,
                widgetUpdateRequested = widgetUpdated,
            ),
        )
        return true
    }
}

internal fun HomeUiState.afterMockModeEnabled() = copy(isMockData = true)

    internal fun HomeUiState.afterMockModeDisabled() = copy(
    snapshot = null,
    isAuthenticated = false,
    sessionStatus = SessionStatus.UNKNOWN,
    isMockData = false,
    errorMessage = null,
)
