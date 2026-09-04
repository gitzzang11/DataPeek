package com.datapeek.worker

import com.datapeek.data.repository.UsageCacheStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticRefreshWorkerTest {
    @Test
    fun backgroundWorkerDefersTheWebViewRefreshToAUsableForegroundHost() {
        val plan = backgroundRefreshPlan()

        assertFalse(plan.performsUsageApiRefresh)
        assertTrue(plan.markRefreshDue)
        assertEquals(UsageCacheStore.BACKGROUND_DEFERRED_RESULT, plan.result)
    }

    @Test
    fun periodicWorkUsesThePlatformMinimumInterval() {
        assertFalse(isPeriodicRefreshIntervalSupported(14L))
        assertTrue(isPeriodicRefreshIntervalSupported(15L))
        assertTrue(isPeriodicRefreshIntervalSupported(60L))
    }
}
