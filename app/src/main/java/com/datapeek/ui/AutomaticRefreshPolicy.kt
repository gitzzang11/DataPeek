package com.datapeek.ui

import java.time.Duration
import java.time.Instant

internal enum class ForegroundRefreshDecision {
    REFRESH_NO_CACHE,
    REFRESH_DUE,
    REFRESH_STALE,
    RECHECK_SESSION,
    SKIP_RECENT,
    SKIP_SESSION_EXPIRED,
    SKIP_UNAUTHENTICATED,
}

internal fun decideForegroundRefresh(
    hasCachedSnapshot: Boolean,
    locallyAuthenticated: Boolean,
    sessionExpired: Boolean,
    refreshDue: Boolean,
    lastSuccessfulRefresh: Instant?,
    now: Instant,
    intervalMinutes: Long,
): ForegroundRefreshDecision {
    if (sessionExpired) return ForegroundRefreshDecision.SKIP_SESSION_EXPIRED
    if (!hasCachedSnapshot && !locallyAuthenticated) return ForegroundRefreshDecision.SKIP_UNAUTHENTICATED
    if (!hasCachedSnapshot) return ForegroundRefreshDecision.REFRESH_NO_CACHE
    if (!locallyAuthenticated) return ForegroundRefreshDecision.RECHECK_SESSION
    if (refreshDue) return ForegroundRefreshDecision.REFRESH_DUE

    val interval = Duration.ofMinutes(intervalMinutes.coerceAtLeast(1L))
    val age = lastSuccessfulRefresh?.let { Duration.between(it, now) }
    return if (age == null || age >= interval) {
        ForegroundRefreshDecision.REFRESH_STALE
    } else {
        ForegroundRefreshDecision.SKIP_RECENT
    }
}
