package com.datapeek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageCycleTest {
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun calendarMonthCycleRunsFromFirstToNextFirst() {
        val cycle = cycleFor(date("2026-10-29"), 1)
        assertEquals(date("2026-10-01"), cycle.start)
        assertEquals(date("2026-11-01"), cycle.endExclusive)
        assertEquals(date("2026-10-31"), cycle.lastDay)
        assertEquals(31, cycle.lengthDays)
    }

    @Test fun customResetDaySplitsMonthsAtThatDay() {
        assertEquals(UsageCycle(date("2026-09-15"), date("2026-10-15")), cycleFor(date("2026-10-14"), 15))
        assertEquals(UsageCycle(date("2026-10-15"), date("2026-11-15")), cycleFor(date("2026-10-15"), 15))
        assertEquals(UsageCycle(date("2026-12-15"), date("2027-01-15")), cycleFor(date("2027-01-01"), 15))
    }

    @Test fun resetDayBeyondMonthLengthFallsBackToLastDayOfMonth() {
        assertEquals(UsageCycle(date("2027-02-28"), date("2027-03-31")), cycleFor(date("2027-03-15"), 31))
        assertEquals(UsageCycle(date("2027-02-28"), date("2027-03-31")), cycleFor(date("2027-02-28"), 31))
        assertEquals(UsageCycle(date("2026-04-30"), date("2026-05-31")), cycleFor(date("2026-05-10"), 31))
    }

    @Test fun neighbouringCyclesTouchWithoutGapsOrOverlap() {
        val cycle = cycleFor(date("2026-10-20"), 1)
        assertEquals(cycle.start, cycle.previous(1).endExclusive)
        assertEquals(cycle.endExclusive, cycle.next(1).start)
        assertTrue(date("2026-10-01") in cycle)
        assertFalse(date("2026-11-01") in cycle)
    }

    @Test fun manualResetDayBeatsCarrierInfoWhichBeatsTheCalendarMonth() {
        assertEquals(20, resolveResetDay(20, date("2026-11-05")))
        assertEquals(5, resolveResetDay(0, date("2026-11-05")))
        assertEquals(1, resolveResetDay(0, null))
    }
}
