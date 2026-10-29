package com.datapeek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MonthlyForecastTest {
    private fun at(value: String) = Instant.parse(value)
    private fun date(value: String) = LocalDate.parse(value)
    private fun forecast(ledger: UsageLedger, now: String, resetDay: Int = 1) =
        forecastCycle(ledger, resetDay, at(now), ZoneOffset.UTC)

    // 1,000 used per day since 00:00 on Oct 1, read every midnight until Oct 11.
    private val steadyThousand = octoberDaily(*(2..11).map { it to 30_000L - 1_000L * (it - 1) }.toTypedArray())

    @Test fun datePredictedInsideThisMonthIsReportedAsTheExhaustionDay() {
        val result = forecast(steadyThousand, "2026-10-11T00:00:00Z")
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
        assertEquals(1_000.0, result.dailyBytes!!, 0.001)
        assertEquals(date("2026-10-31"), result.exhaustionDate)
        assertEquals(20, result.daysLeft)
        assertEquals(20_000.0 / 21, result.dailyBudgetBytes!!, 0.001)
        assertNull(result.leftoverAtResetBytes)
    }

    @Test fun datePredictedAfterTheResetMeansThisMonthIsSafe() {
        val ledger = octoberDaily(*(2..11).map { it to 30_000L - 500L * (it - 1) }.toTypedArray())
        val result = forecast(ledger, "2026-10-11T00:00:00Z")
        assertEquals(ForecastStatus.SAFE, result.status)
        assertEquals(500.0, result.dailyBytes!!, 0.001)
        assertEquals(date("2026-11-30"), result.exhaustionDate)
        assertEquals(25_000L - 500L * 21, result.leftoverAtResetBytes)
    }

    @Test fun exhaustingExactlyOnTheLastDayCountsAsThisMonth() {
        val result = forecast(steadyThousand, "2026-10-11T00:00:00Z")
        assertTrue(result.exhaustionDate!! <= result.cycle.lastDay)
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
    }

    @Test fun wholeMonthAndLastWeekBothShapeThePaceOnceAWeekOfDataExists() {
        // 1,000/day for 7 days, then 4,000/day for 3 days: month average 1,900, last 7 days about 2,286.
        val ledger = octoberDaily(
            2 to 29_000, 3 to 28_000, 4 to 27_000, 5 to 26_000, 6 to 25_000, 7 to 24_000, 8 to 23_000,
            9 to 19_000, 10 to 15_000, 11 to 11_000,
        )
        val result = forecast(ledger, "2026-10-11T00:00:00Z")
        assertEquals(1_900.0, result.cycleAverageBytes!!, 0.001)
        assertEquals(16_000.0 / 7, result.recentAverageBytes!!, 0.001)
        assertEquals(0.5 * (1_900.0 + 16_000.0 / 7), result.dailyBytes!!, 0.001)
        assertTrue(result.earliestDate!! < result.latestDate!!)
    }

    @Test fun beforeAWeekOfDataOnlyTheWholeMonthSoFarIsUsed() {
        val ledger = octoberDaily(2 to 29_000, 3 to 28_000, 4 to 24_000)
        val result = forecast(ledger, "2026-10-04T00:00:00Z")
        assertEquals(2_000.0, result.dailyBytes!!, 0.001)
    }

    @Test fun previousMonthsNeverInfluenceThePrediction() {
        val ledger = ledgerOf(
            reading("2026-09-29T00:00:00Z", 100),
            reading("2026-09-30T00:00:00Z", 0),
            reading("2026-10-02T00:00:00Z", 29_000),
            reading("2026-10-04T00:00:00Z", 27_000),
        )
        val result = forecast(ledger, "2026-10-04T00:00:00Z")
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
        assertEquals(1_000.0, result.dailyBytes!!, 0.001)
        assertEquals(27_000L, result.remainingBytes)
    }

    @Test fun aNewMonthStartsLearningAgainInsteadOfReusingTheOldOne() {
        val result = forecast(steadyThousand, "2026-11-01T09:00:00Z")
        assertEquals(ForecastStatus.INSUFFICIENT, result.status)
        assertNull(result.remainingBytes)
        assertEquals(date("2026-11-01"), result.cycle.start)
    }

    @Test fun lessThanADayIntoTheMonthIsNotEnoughToPredict() {
        val ledger = ledgerOf(reading("2026-10-01T12:00:00Z", 29_000))
        assertEquals(ForecastStatus.INSUFFICIENT, forecast(ledger, "2026-10-01T12:00:00Z").status)
        assertEquals(ForecastStatus.INSUFFICIENT, forecast(UsageLedger(), "2026-10-01T12:00:00Z").status)
    }

    @Test fun firstReadingLateInTheMonthPredictsFromTheUsageSinceTheMonthStart() {
        val ledger = ledgerOf(reading("2026-10-11T00:00:00Z", 20_000))
        val result = forecast(ledger, "2026-10-11T00:00:00Z")
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
        assertEquals(1_000.0, result.dailyBytes!!, 0.001)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test fun topUpRestartsThePaceFromTheTopUpReading() {
        val ledger = octoberDaily(2 to 29_000, 3 to 28_000, 4 to 29_500, 5 to 28_500, 6 to 27_500)
        val result = forecast(ledger, "2026-10-06T00:00:00Z")
        assertEquals(1_000.0, result.dailyBytes!!, 0.001)
        assertEquals(2.0, result.observedDays, 0.001)
    }

    @Test fun zeroBalanceMeansExhaustedAndNoUsageMeansNothingToWorryAbout() {
        assertEquals(ForecastStatus.EXHAUSTED, forecast(octoberDaily(5 to 0), "2026-10-05T01:00:00Z").status)
        val unused = forecast(octoberDaily(2 to 30_000, 5 to 30_000), "2026-10-05T00:00:00Z")
        assertEquals(ForecastStatus.NO_CONSUMPTION, unused.status)
        assertNull(unused.exhaustionDate)
    }

    @Test fun customResetDayMovesTheMonthBoundary() {
        val ledger = ledgerOf(
            reading("2026-10-16T00:00:00Z", 28_000),
            reading("2026-10-20T00:00:00Z", 20_000),
        )
        val result = forecast(ledger, "2026-10-20T00:00:00Z", resetDay = 15)
        assertEquals(date("2026-10-15"), result.cycle.start)
        assertEquals(date("2026-11-15"), result.cycle.endExclusive)
        assertEquals(2_000.0, result.cycleAverageBytes!!, 0.001) // 10,000 used in 5 days
        assertEquals(10.0, result.remainingBytes!! / result.dailyBytes!!, 0.001)
        assertEquals(date("2026-10-30"), result.exhaustionDate)
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
    }

    @Test fun oldReadingIsStillUsedButFlaggedAsStale() {
        val result = forecast(steadyThousand, "2026-10-14T06:00:00Z")
        assertEquals(3, result.staleDays)
        assertEquals(ForecastStatus.RUNS_OUT, result.status)
    }

    @Test fun confidenceGrowsWithTheDaysOfRecordedUsage() {
        assertEquals(Confidence.LOW, forecast(octoberDaily(2 to 29_000, 3 to 28_000), "2026-10-03T00:00:00Z").confidence)
        assertEquals(Confidence.MEDIUM, forecast(octoberDaily(2 to 29_000, 6 to 25_000), "2026-10-06T00:00:00Z").confidence)
        assertEquals(Confidence.HIGH, forecast(steadyThousand, "2026-10-11T00:00:00Z").confidence)
    }
}
