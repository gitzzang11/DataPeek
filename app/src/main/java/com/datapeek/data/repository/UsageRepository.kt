package com.datapeek.data.repository

import android.content.Context
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.provider.CarrierProvider
import com.datapeek.data.provider.MockProvider
import com.datapeek.widget.DataUsageWidget
import androidx.glance.appwidget.updateAll
import com.datapeek.worker.UsageRefreshScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class UsageRepository(
    private val context: Context,
    private val providers: Map<String, CarrierProvider>,
) {
    private val refreshMutex = Mutex()
    private var overrideProvider: CarrierProvider? = null
    private val _activeCarrier = MutableStateFlow(Carrier.FREET_SKT)
    val activeCarrier = _activeCarrier.asStateFlow()

    val snapshot: Flow<UsageSnapshot?> = UsageCacheStore.snapshotFlow(context)
    val trackedUsage = UsageCacheStore.trackedUsageFlow(context)
    val resetDay: Flow<Int> = UsageCacheStore.resetDayFlow(context)
    val lastError: Flow<String?> = UsageCacheStore.errorFlow(context)
    val developerModeEnabled: Flow<Boolean> = UsageCacheStore.developerModeEnabledFlow(context)
    val autoRefreshEnabled: Flow<Boolean> = UsageCacheStore.autoRefreshEnabledFlow(context)
    val refreshDue: Flow<Boolean> = UsageCacheStore.refreshDueFlow(context)
    val lastBackgroundAttempt: Flow<Instant?> = UsageCacheStore.lastBackgroundAttemptFlow(context)
    val lastBackgroundResult: Flow<String?> = UsageCacheStore.lastBackgroundResultFlow(context)
    val lastRefreshTrigger: Flow<String?> = UsageCacheStore.lastRefreshTriggerFlow(context)

    suspend fun refresh(): Result<UsageSnapshot> = refreshMutex.withLock {
        val provider = overrideProvider ?: providers[_activeCarrier.value.name]
            ?: return@withLock Result.failure(UsageException(UsageError.UnsupportedResponse))
        val result = provider.fetchUsage()
        result.fold(
            onSuccess = { snapshot ->
                saveSnapshot(snapshot)
                Result.success(snapshot)
            },
            onFailure = { throwable ->
                val error = (throwable as? UsageException)?.error ?: UsageError.NetworkUnavailable
                UsageCacheStore.saveError(context, errorMessage(error))
                Result.failure(throwable)
            },
        )
    }

    suspend fun isAuthenticated(): Boolean =
        (overrideProvider ?: providers[_activeCarrier.value.name])?.isAuthenticated() == true

    suspend fun markAuthenticated() {
        (overrideProvider ?: providers[_activeCarrier.value.name])?.markAuthenticated()
    }

    suspend fun saveSnapshot(snapshot: UsageSnapshot) {
        UsageCacheStore.saveSnapshot(context, snapshot)
    }

    suspend fun recordError(error: UsageError) {
        UsageCacheStore.saveError(context, errorMessage(error))
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        UsageCacheStore.setDeveloperModeEnabled(context, enabled)
    }

    suspend fun clearCachedSnapshot() {
        UsageCacheStore.clearSnapshot(context)
    }

    suspend fun requestWidgetUpdate(): Boolean = runCatching {
        DataUsageWidget().updateAll(context)
    }.isSuccess

    suspend fun logout() {
        (overrideProvider ?: providers[_activeCarrier.value.name])?.logout()
        overrideProvider = null
        UsageCacheStore.clearSnapshot(context)
    }

    fun useMockProvider() {
        overrideProvider = MockProvider()
    }

    suspend fun clearMockProvider() {
        overrideProvider = null
        // Test data never reaches the usage history, so turning it off must not erase the real history.
        UsageCacheStore.clearSnapshot(context, keepHistory = true)
    }

    suspend fun setResetDay(day: Int) {
        UsageCacheStore.setResetDay(context, day)
    }

    suspend fun clearUsageHistory() {
        UsageCacheStore.clearUsageHistory(context)
    }

    suspend fun setAutoRefreshMinutes(minutes: Long) {
        UsageCacheStore.setAutoRefreshMinutes(context, minutes)
        UsageRefreshScheduler.ensureConfigured(context)
    }

    suspend fun autoRefreshMinutes() = UsageCacheStore.readAutoRefreshMinutes(context)

    suspend fun autoRefreshEnabled() = UsageCacheStore.readAutoRefreshEnabled(context)

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        UsageCacheStore.setAutoRefreshEnabled(context, enabled)
        UsageRefreshScheduler.ensureConfigured(context)
    }

    suspend fun isRefreshDue() = UsageCacheStore.readRefreshDue(context)

    suspend fun clearRefreshDue() {
        UsageCacheStore.clearRefreshDue(context)
    }

    suspend fun recordRefreshTrigger(trigger: String) {
        UsageCacheStore.recordRefreshTrigger(context, trigger)
    }

    private fun errorMessage(error: UsageError) = when (error) {
        UsageError.NotAuthenticated -> "로그인이 필요합니다."
        UsageError.SessionExpired -> "로그인이 만료되었습니다."
        UsageError.NetworkUnavailable -> "네트워크에 연결할 수 없습니다."
        is UsageError.HttpError -> "통신사 서버 오류(${error.code})"
        is UsageError.ParseError -> "응답을 해석하지 못했습니다."
        UsageError.ApiTimeout -> "사용량 확인 시간이 초과되었습니다."
        UsageError.ApiBodyReadFailed -> "사용량 응답을 읽지 못했습니다."
        UsageError.ApiRequestFailed -> "사용량 서버에 연결하지 못했습니다."
        UsageError.UnsupportedResponse -> "인증된 사용량 endpoint가 아직 연결되지 않았습니다."
        UsageError.RateLimited -> "잠시 후 다시 시도해 주세요."
    }
}
