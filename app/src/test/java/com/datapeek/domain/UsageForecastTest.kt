package com.datapeek.domain

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UsageForecastTest {
    private val start = Instant.parse("2026-10-10T00:00:00Z")
    private fun snapshot(days: Long, remaining: Long, total: Long = 20_000L) = UsageSnapshot(
        carrier = Carrier.FREET_SKT,
        lineId = "test-line",
        totalBytes = total,
        remainingBytes = remaining,
        fetchedAt = start.plusSeconds(days * 86_400),
        cycleEnd = LocalDate.parse("2026-10-31"),
        source = UsageSource.CARRIER_SERVER,
    )

    private fun history(vararg snapshots: UsageSnapshot): List<UsageObservation> =
        snapshots.fold(emptyList()) { history, snapshot -> recordUsage(history, snapshot, ZoneOffset.UTC) }

    @Test fun predictsFromObservedConsumptionAndKeepsTheDateAnchoredToLastReading() {
        val latest = snapshot(2, 8_000)
        val forecast = forecastUsage(history(snapshot(0, 10_000), latest), latest.fetchedAt, ZoneOffset.UTC)
        assertEquals(ForecastStatus.PREDICTED, forecast.status)
        assertEquals(1_000.0, forecast.dailyBytes!!, 0.001)
        assertEquals(latest.fetchedAt.plusSeconds(8 * 86_400), forecast.exhaustedAt)
        assertEquals(true, forecast.runsOutBeforeReset)
        assertEquals(8_000.0 / 19, forecast.dailyBudgetBytes!!, 0.001)
    }

    @Test fun oneReadingOrLessThanOneDayNeedsMoreHistory() {
        val first = snapshot(0, 10_000)
        assertEquals(ForecastStatus.LEARNING, forecastUsage(history(first), first.fetchedAt).status)
        val second = first.copy(remainingBytes = 9_000, fetchedAt = start.plusSeconds(3_600))
        assertEquals(ForecastStatus.LEARNING, forecastUsage(history(first, second), second.fetchedAt).status)
    }

    @Test fun unchangedBalanceDoesNotInventAnExhaustionDate() {
        val latest = snapshot(2, 10_000)
        val result = forecastUsage(history(snapshot(0, 10_000), latest), latest.fetchedAt)
        assertEquals(ForecastStatus.NO_CONSUMPTION, result.status)
        assertNull(result.exhaustedAt)
    }

    @Test fun zeroBalanceIsAlreadyExhaustedEvenWithOneReading() {
        val latest = snapshot(0, 0)
        assertEquals(ForecastStatus.EXHAUSTED, forecastUsage(history(latest), latest.fetchedAt).status)
    }

    @Test fun topUpOrPlanChangeStartsANewHistory() {
        assertEquals(1, history(snapshot(0, 10_000), snapshot(1, 11_000)).size)
        assertEquals(1, history(snapshot(0, 10_000), snapshot(1, 9_000, total = 30_000)).size)
    }

    @Test fun newCycleAndDifferentLineNeverShareConsumption() {
        val first = snapshot(0, 10_000)
        assertEquals(1, history(first, snapshot(1, 9_000).copy(lineId = "other")).size)
        assertEquals(1, history(first, snapshot(1, 9_000).copy(cycleEnd = LocalDate.parse("2026-11-30"))).size)
    }

    @Test fun failedOrMockReadingsDoNotPolluteRealHistory() {
        val first = snapshot(0, 10_000)
        val original = history(first)
        assertEquals(original, recordUsage(original, snapshot(1, 9_000).copy(remainingBytes = null)))
        assertEquals(original, recordUsage(original, snapshot(1, 9_000).copy(source = UsageSource.MOCK)))
        assertEquals(original, recordUsage(original, snapshot(1, -1)))
    }

    @Test fun duplicatesAndOlderResponsesDoNotAddObservations() {
        val first = snapshot(0, 10_000)
        val latest = snapshot(2, 8_000)
        val original = history(first, latest)
        assertEquals(original, recordUsage(original, latest))
        assertEquals(original, recordUsage(original, first))
    }

    @Test fun frequentRefreshesPreserveTheFirstBaselineAndBoundStorage() {
        val first = snapshot(0, 10_000)
        var observations = history(first)
        repeat(2_000) { index ->
            observations = recordUsage(observations, first.copy(
                fetchedAt = start.plusSeconds((index + 1) * 60L),
                remainingBytes = 10_000L - index,
            ))
        }
        assertEquals(start, observations.first().fetchedAt)
        assertTrue(observations.size < 40)
    }

    @Test fun staleOrFutureReadingsDoNotProduceCurrentPredictions() {
        val latest = snapshot(2, 8_000)
        val observations = history(snapshot(0, 10_000), latest)
        assertEquals(ForecastStatus.STALE, forecastUsage(observations, start.plusSeconds(4 * 86_400)).status)
        assertEquals(ForecastStatus.STALE, forecastUsage(observations, start).status)
    }

    @Test fun forecastsBeyondResetAreNotReportedAsRunningOutThisCycle() {
        val latest = snapshot(2, 9_800)
        val result = forecastUsage(history(snapshot(0, 10_000), latest), latest.fetchedAt)
        assertEquals(false, result.runsOutBeforeReset)
    }

    @Test fun missingResetDateStillAllowsAnExhaustionEstimate() {
        val first = snapshot(0, 10_000).copy(cycleEnd = null)
        val latest = snapshot(2, 8_000).copy(cycleEnd = null)
        val result = forecastUsage(history(first, latest), latest.fetchedAt)
        assertNotNull(result.exhaustedAt)
        assertNull(result.runsOutBeforeReset)
        assertNull(result.dailyBudgetBytes)
    }

    @Test fun resetCountdownIsConvertedToAFixedDateAndSurvivesSerialization() {
        val first = snapshot(0, 10_000).copy(cycleEnd = null, daysUntilReset = 5)
        val latest = snapshot(2, 8_000).copy(cycleEnd = null, daysUntilReset = 3)
        val observations = history(first, latest)
        assertEquals(2, observations.size)
        assertEquals(LocalDate.parse("2026-10-15"), observations.last().resetDate)
        assertEquals(observations, UsageHistoryCodec.decode(UsageHistoryCodec.encode(observations)))
        assertFalse(UsageHistoryCodec.encode(observations).contains("test-line"))
        assertTrue(UsageHistoryCodec.decode("invalid").isEmpty())
    }

    @Test fun emptyHistoryAndExpiredCycleDoNotPredict() {
        assertEquals(ForecastStatus.LEARNING, forecastUsage(emptyList(), start).status)
        val latest = snapshot(2, 8_000).copy(cycleEnd = LocalDate.parse("2026-10-11"))
        assertEquals(ForecastStatus.STALE, forecastUsage(history(latest), latest.fetchedAt).status)
    }

    @Test fun recentWeekExcludesOlderConsumptionPatterns() {
        val points = (0L..10L).map { day -> snapshot(day, if (day < 3) 20_000 - day * 3_000 else 14_000 - day * 100) }
        val observations = history(*points.toTypedArray())
        val result = forecastUsage(observations, points.last().fetchedAt)
        assertEquals(100.0, result.dailyBytes!!, 0.001)
        assertEquals(7.0, result.observedDays, 0.001)
    }

    @Test fun fallbackToOfficialWebPageCanContinueTheSameHistory() {
        val latest = snapshot(2, 8_000).copy(source = UsageSource.WEB_PAGE)
        assertEquals(ForecastStatus.PREDICTED, forecastUsage(history(snapshot(0, 10_000), latest), latest.fetchedAt).status)
    }

    @Test fun aLongGapStartsLearningAgainRatherThanInterpolatingAcrossResets() {
        assertEquals(1, history(snapshot(0, 10_000), snapshot(10, 8_000)).size)
    }

    @Test fun missingAllowanceAndCycleDatesRoundTripWithoutInventingValues() {
        val observations = history(snapshot(0, 10_000).copy(totalBytes = null, cycleEnd = null))
        val decoded = UsageHistoryCodec.decode(UsageHistoryCodec.encode(observations))
        assertEquals(observations, decoded)
        assertNull(decoded.single().totalBytes)
        assertNull(decoded.single().resetDate)
    }

    @Test fun invalidSerializedCountersCannotProducePredictions() {
        val observations = history(snapshot(0, 10_000))
        val encoded = UsageHistoryCodec.encode(observations)
        assertTrue(UsageHistoryCodec.decode(encoded.replace("10000", "-1")).isEmpty())
    }

    @Test fun negligibleConsumptionDoesNotInventACappedExhaustionDate() {
        val first = snapshot(0, Long.MAX_VALUE).copy(totalBytes = Long.MAX_VALUE, cycleEnd = null)
        val latest = first.copy(remainingBytes = Long.MAX_VALUE - 1, fetchedAt = start.plusSeconds(86_400))
        val result = forecastUsage(history(first, latest), latest.fetchedAt)
        assertNull(result.exhaustedAt)
        assertNotNull(result.dailyBytes)
    }
}
