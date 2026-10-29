package com.datapeek.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** One data allowance period: [start] inclusive until [endExclusive], the next reset date. */
data class UsageCycle(val start: LocalDate, val endExclusive: LocalDate) {
    val lastDay: LocalDate get() = endExclusive.minusDays(1)
    val lengthDays: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}

private fun resetDateIn(month: YearMonth, resetDay: Int): LocalDate =
    month.atDay(resetDay.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))

/**
 * Cycle containing [date] when data resets on [resetDay] of every month.
 * Days past the end of a short month reset on that month's last day, so 31 means "month end".
 */
fun cycleFor(date: LocalDate, resetDay: Int): UsageCycle {
    val month = YearMonth.from(date)
    val thisMonthReset = resetDateIn(month, resetDay)
    val start = if (date >= thisMonthReset) thisMonthReset else resetDateIn(month.minusMonths(1), resetDay)
    return UsageCycle(start, resetDateIn(YearMonth.from(start).plusMonths(1), resetDay))
}

fun UsageCycle.previous(resetDay: Int): UsageCycle = cycleFor(start.minusDays(1), resetDay)

fun UsageCycle.next(resetDay: Int): UsageCycle = cycleFor(endExclusive, resetDay)

/** A manual reset day (1..31) wins; otherwise a carrier-provided reset date, otherwise the calendar month. */
fun resolveResetDay(setting: Int, carrierResetDate: LocalDate?): Int = when {
    setting in 1..31 -> setting
    carrierResetDate != null -> carrierResetDate.dayOfMonth
    else -> 1
}
