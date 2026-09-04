package com.datapeek.ui

import com.datapeek.data.model.UsageError
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusTest {
    @Test
    fun onlyConfirmedAuthenticationFailuresRequireLogin() {
        assertEquals(SessionStatus.SESSION_EXPIRED, UsageError.NotAuthenticated.toSessionStatus())
        assertEquals(SessionStatus.SESSION_EXPIRED, UsageError.SessionExpired.toSessionStatus())
        assertEquals(SessionStatus.SESSION_EXPIRED, "프리티 로그인이 만료되었습니다.".toSessionStatusFromMessage())
    }

    @Test
    fun transientFailuresAreNotClassifiedAsSessionExpiry() {
        assertEquals(SessionStatus.NETWORK_ERROR, UsageError.NetworkUnavailable.toSessionStatus())
        assertEquals(SessionStatus.NETWORK_ERROR, UsageError.ApiTimeout.toSessionStatus())
        assertEquals(SessionStatus.NETWORK_ERROR, UsageError.ApiBodyReadFailed.toSessionStatus())
        assertEquals(SessionStatus.NETWORK_ERROR, UsageError.ApiRequestFailed.toSessionStatus())
        assertEquals(SessionStatus.NETWORK_ERROR, "네트워크에 연결할 수 없습니다.".toSessionStatusFromMessage())
    }

    @Test
    fun parserAndServerFailuresDoNotBecomeSessionExpiry() {
        assertEquals(SessionStatus.SERVER_ERROR, UsageError.ParseError("invalid").toSessionStatus())
        assertEquals(SessionStatus.SERVER_ERROR, UsageError.HttpError(500).toSessionStatus())
        assertEquals(SessionStatus.SERVER_ERROR, UsageError.UnsupportedResponse.toSessionStatus())
        assertEquals(SessionStatus.SERVER_ERROR, "응답을 해석하지 못했습니다.".toSessionStatusFromMessage())
    }

    @Test
    fun cachedSnapshotIsProbedEvenWhenLocalSessionMarkerIsMissing() {
        assertEquals(true, shouldProbeSession(hasCachedSnapshot = true, locallyAuthenticated = false))
    }

    @Test
    fun allStartupCacheAndMarkerCombinationsHaveAnExplicitInitialState() {
        assertEquals(SessionStatus.CHECKING, initialSessionStatus(true, true))
        assertEquals(SessionStatus.CHECKING, initialSessionStatus(true, false))
        assertEquals(SessionStatus.CHECKING, initialSessionStatus(false, true))
        assertEquals(SessionStatus.UNKNOWN, initialSessionStatus(false, false))
    }

    @Test
    fun emptyCacheWithoutLocalSessionMarkerDoesNotStartAnUnauthenticatedProbe() {
        assertEquals(false, shouldProbeSession(hasCachedSnapshot = false, locallyAuthenticated = false))
    }

    @Test
    fun foregroundRefreshIsSkippedOnlyInsideTheMinimumInterval() {
        assertEquals(false, shouldStartForegroundRefresh(1_000L, 20_000L, 30_000L))
        assertEquals(true, shouldStartForegroundRefresh(1_000L, 31_000L, 30_000L))
        assertEquals(true, shouldStartForegroundRefresh(Long.MIN_VALUE, 20_000L, 30_000L))
    }

    @Test
    fun cacheRemainsVisibleWhileTransientRefreshFails() {
        val cached = testSnapshot()
        val state = HomeUiState(
            snapshot = cached,
            isAuthenticated = true,
            sessionStatus = SessionStatus.NETWORK_ERROR,
            errorMessage = "네트워크에 연결할 수 없습니다.",
        )

        assertEquals(cached, state.snapshot)
        assertEquals(UsageUiStatus.UPDATE_FAILED, state.usageUiStatus())
    }

    @Test
    fun cacheRemainsVisibleWhenSessionExpires() {
        val cached = testSnapshot()
        val state = HomeUiState(
            snapshot = cached,
            isAuthenticated = false,
            sessionStatus = SessionStatus.SESSION_EXPIRED,
            errorMessage = "로그인이 만료되었습니다.",
        )

        assertEquals(cached, state.snapshot)
        assertEquals(UsageUiStatus.LOGIN_REQUIRED, state.usageUiStatus())
    }

    private fun testSnapshot() = com.datapeek.data.model.UsageSnapshot(
        carrier = com.datapeek.data.model.Carrier.FREET_SKT,
        totalBytes = 20L,
        usedBytes = 1L,
        remainingBytes = 19L,
        fetchedAt = java.time.Instant.parse("2026-09-02T00:00:00Z"),
        source = com.datapeek.data.model.UsageSource.CARRIER_SERVER,
    )
}
