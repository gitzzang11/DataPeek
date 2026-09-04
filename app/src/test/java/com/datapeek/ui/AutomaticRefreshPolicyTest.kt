package com.datapeek.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticRefreshPolicyTest {
    private val now = Instant.parse("2026-09-04T10:00:00Z")
    private val oneHour = 60L

    @Test
    fun recentCachedSnapshotSkipsForegroundRefresh() {
        assertEquals(
            ForegroundRefreshDecision.SKIP_RECENT,
            decideForegroundRefresh(
                hasCachedSnapshot = true,
                locallyAuthenticated = true,
                sessionExpired = false,
                refreshDue = false,
                lastSuccessfulRefresh = now.minusSeconds(10 * 60),
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }

    @Test
    fun staleCachedSnapshotStartsForegroundRefresh() {
        assertEquals(
            ForegroundRefreshDecision.REFRESH_STALE,
            decideForegroundRefresh(
                hasCachedSnapshot = true,
                locallyAuthenticated = true,
                sessionExpired = false,
                refreshDue = false,
                lastSuccessfulRefresh = now.minusSeconds(61 * 60),
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }

    @Test
    fun backgroundDueRefreshWinsEvenWhenCacheIsRecent() {
        assertEquals(
            ForegroundRefreshDecision.REFRESH_DUE,
            decideForegroundRefresh(
                hasCachedSnapshot = true,
                locallyAuthenticated = true,
                sessionExpired = false,
                refreshDue = true,
                lastSuccessfulRefresh = now.minusSeconds(10 * 60),
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }

    @Test
    fun noCacheWithLocalSessionStartsProbe() {
        assertEquals(
            ForegroundRefreshDecision.REFRESH_NO_CACHE,
            decideForegroundRefresh(
                hasCachedSnapshot = false,
                locallyAuthenticated = true,
                sessionExpired = false,
                refreshDue = false,
                lastSuccessfulRefresh = null,
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }

    @Test
    fun cachedSnapshotWithoutLocalSessionStillProbesForExpiry() {
        assertEquals(
            ForegroundRefreshDecision.RECHECK_SESSION,
            decideForegroundRefresh(
                hasCachedSnapshot = true,
                locallyAuthenticated = false,
                sessionExpired = false,
                refreshDue = false,
                lastSuccessfulRefresh = now.minusSeconds(10 * 60),
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }

    @Test
    fun sessionExpiredDoesNotStartAutomaticRefresh() {
        assertEquals(
            ForegroundRefreshDecision.SKIP_SESSION_EXPIRED,
            decideForegroundRefresh(
                hasCachedSnapshot = true,
                locallyAuthenticated = false,
                sessionExpired = true,
                refreshDue = true,
                lastSuccessfulRefresh = now.minusSeconds(2 * 60 * 60),
                now = now,
                intervalMinutes = oneHour,
            ),
        )
    }
}
