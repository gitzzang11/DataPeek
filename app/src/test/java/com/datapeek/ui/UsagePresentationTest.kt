package com.datapeek.ui

import com.datapeek.data.model.UsageError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsagePresentationTest {
    @Test
    fun authenticatedStateWithoutErrorIsNormal() {
        assertEquals(
            UsageUiStatus.NORMAL,
            HomeUiState(isAuthenticated = true).usageUiStatus(),
        )
    }

    @Test
    fun unauthenticatedStateRequiresLoginEvenWhenAnErrorIsPresent() {
        assertEquals(
            UsageUiStatus.LOGIN_REQUIRED,
            HomeUiState(
                isAuthenticated = false,
                errorMessage = "프리티 로그인이 만료되었습니다.",
            ).usageUiStatus(),
        )
    }

    @Test
    fun authenticatedStateWithRefreshErrorShowsUpdateFailure() {
        assertEquals(
            UsageUiStatus.UPDATE_FAILED,
            HomeUiState(
                isAuthenticated = true,
                errorMessage = "네트워크에 연결할 수 없습니다.",
            ).usageUiStatus(),
        )
    }

    @Test
    fun onlyAuthenticationErrorsRequireLoginRecovery() {
        assertTrue(UsageError.NotAuthenticated.requiresLogin())
        assertTrue(UsageError.SessionExpired.requiresLogin())
        assertFalse(UsageError.NetworkUnavailable.requiresLogin())
        assertFalse(UsageError.ParseError("invalid").requiresLogin())
    }

    @Test
    fun persistedAuthenticationFailureMessageAlsoRequiresLoginRecovery() {
        assertTrue("프리티 로그인이 만료되었습니다.".requiresLoginMessage())
        assertTrue("세션이 만료되었습니다.".requiresLoginMessage())
        assertFalse("네트워크에 연결할 수 없습니다.".requiresLoginMessage())
    }

    @Test
    fun cachedSnapshotAgeIsHumanReadableForStaleData() {
        val fetchedAt = Instant.parse("2026-09-03T06:00:00Z")
        val now = Instant.parse("2026-09-04T00:00:00Z")

        assertEquals("18시간 전", lastSuccessfulRefreshAgeLabel(fetchedAt, now))
    }

    @Test
    fun cachedSnapshotAgeDoesNotBecomeNegativeWhenTheClockMovesBack() {
        val fetchedAt = Instant.parse("2026-09-04T00:00:00Z")
        val now = Instant.parse("2026-09-03T23:59:00Z")

        assertEquals("방금 전", lastSuccessfulRefreshAgeLabel(fetchedAt, now))
    }
}
