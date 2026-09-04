package com.datapeek.ui

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageViewModelMockModeTest {
    @Test
    fun disablingMockModeReturnsToLiveModeAndClearsMockSnapshot() {
        val mockState = HomeUiState(
            snapshot = mockSnapshot(),
            isAuthenticated = true,
            isMockData = true,
        )

        val liveState = mockState.afterMockModeDisabled()

        assertFalse(liveState.isMockData)
        assertNull(liveState.snapshot)
        assertFalse(liveState.isAuthenticated)
    }

    @Test
    fun enablingMockModeKeepsTheSnapshotUntilMockRefreshCompletes() {
        val liveState = HomeUiState(isAuthenticated = false)

        val mockState = liveState.afterMockModeEnabled()

        assertTrue(mockState.isMockData)
        assertNull(mockState.snapshot)
        assertFalse(mockState.isAuthenticated)
    }

    private fun mockSnapshot() = UsageSnapshot(
        carrier = Carrier.FREET_SKT,
        lineName = "개발용 Mock 회선",
        totalBytes = 24_200_000_000L,
        usedBytes = 1_500_000_000L,
        remainingBytes = 22_700_000_000L,
        remainingPercent = 93.8f,
        cycleStart = LocalDate.of(2026, 9, 1),
        cycleEnd = LocalDate.of(2026, 9, 30),
        fetchedAt = Instant.parse("2026-09-02T00:00:00Z"),
        source = UsageSource.MOCK,
    )
}
