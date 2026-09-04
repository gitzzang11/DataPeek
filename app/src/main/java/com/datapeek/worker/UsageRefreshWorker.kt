package com.datapeek.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.datapeek.data.repository.UsageCacheStore
import com.datapeek.widget.DataUsageWidget
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val MIN_PERIODIC_MINUTES = 15L

class UsageRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Freet's verified refresh pipeline is hosted by the foreground Activity's WebView.
        // A Worker must not create a headless WebView or copy its cookies into another client.
        val plan = backgroundRefreshPlan()
        if (plan.markRefreshDue) {
            UsageCacheStore.markBackgroundRefreshDue(applicationContext, Instant.now(), plan.result)
        }
        DataUsageWidget().updateAll(applicationContext)
        return Result.success()
    }
}

internal data class BackgroundRefreshPlan(
    val performsUsageApiRefresh: Boolean,
    val markRefreshDue: Boolean,
    val result: String,
)

internal fun backgroundRefreshPlan(): BackgroundRefreshPlan = BackgroundRefreshPlan(
    performsUsageApiRefresh = false,
    markRefreshDue = true,
    result = UsageCacheStore.BACKGROUND_DEFERRED_RESULT,
)

object UsageRefreshScheduler {
    private const val PERIODIC_WORK_NAME = "usage_periodic_refresh"

    suspend fun ensureConfigured(context: Context) {
        val repository = (context.applicationContext as com.datapeek.DataPeekApplication).repository
        schedulePeriodic(
            context = context,
            enabled = repository.autoRefreshEnabled(),
            minutes = repository.autoRefreshMinutes(),
        )
    }

    fun schedulePeriodic(context: Context, minutes: Long) {
        schedulePeriodic(context, enabled = minutes > 0L, minutes = minutes)
    }

    fun schedulePeriodic(context: Context, enabled: Boolean, minutes: Long) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC_WORK_NAME)
        if (!enabled || !isPeriodicRefreshIntervalSupported(minutes)) return
        val request = PeriodicWorkRequestBuilder<UsageRefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

}

internal fun isPeriodicRefreshIntervalSupported(minutes: Long): Boolean = minutes >= MIN_PERIODIC_MINUTES
