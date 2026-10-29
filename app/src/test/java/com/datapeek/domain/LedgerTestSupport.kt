package com.datapeek.domain

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import java.time.Instant
import java.time.ZoneOffset

internal fun reading(
    at: String,
    remaining: Long,
    total: Long? = 30_000L,
    source: UsageSource = UsageSource.CARRIER_SERVER,
    line: String = "line",
) = UsageSnapshot(
    carrier = Carrier.FREET_SKT,
    lineId = line,
    totalBytes = total,
    remainingBytes = remaining,
    fetchedAt = Instant.parse(at),
    source = source,
)

internal fun ledgerOf(vararg readings: UsageSnapshot): UsageLedger =
    readings.fold(UsageLedger()) { ledger, snapshot -> recordUsage(ledger, snapshot, ZoneOffset.UTC) }

/** Daily readings at 00:00 UTC of October [days] with the given remaining balances. */
internal fun octoberDaily(vararg remainingByDay: Pair<Int, Long>, total: Long = 30_000L): UsageLedger =
    ledgerOf(*remainingByDay.map { (day, remaining) ->
        reading("2026-10-%02dT00:00:00Z".format(day), remaining, total)
    }.toTypedArray())
