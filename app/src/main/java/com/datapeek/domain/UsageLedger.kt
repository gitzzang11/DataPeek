package com.datapeek.domain

import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The last successful carrier reading of one local calendar day. */
data class LedgerDay(
    val date: LocalDate,
    val remainingBytes: Long,
    val totalBytes: Long?,
    val fetchedAt: Instant,
    /** Reset date reported by the carrier with this reading, when it reports one. */
    val resetHint: LocalDate? = null,
    /** True when the balance went up between two readings of the same day (data top-up). */
    val topUpSeen: Boolean = false,
)

/** Per-day usage history of a single line. Holds counters and dates only, never account information. */
data class UsageLedger(val scope: String? = null, val days: List<LedgerDay> = emptyList()) {
    val latest: LedgerDay? get() = days.lastOrNull()
    val carrierResetHint: LocalDate? get() = days.lastOrNull { it.resetHint != null }?.resetHint
}

const val LEDGER_MAX_DAYS = 400

private fun lineScope(snapshot: UsageSnapshot): String {
    val identity = listOf(snapshot.carrier.name, snapshot.lineId, snapshot.phoneNumberMasked, snapshot.lineName)
        .joinToString("\u0000") { it.orEmpty() }
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Adds a successful carrier reading. Test data, failed readings and out-of-order readings are ignored. */
fun recordUsage(
    ledger: UsageLedger,
    snapshot: UsageSnapshot,
    zone: ZoneId = ZoneId.systemDefault(),
): UsageLedger {
    if (snapshot.source !in setOf(UsageSource.CARRIER_SERVER, UsageSource.WEB_PAGE)) return ledger
    val remaining = snapshot.remainingBytes?.takeIf { it >= 0 } ?: return ledger
    if (snapshot.totalBytes != null && (snapshot.totalBytes < 0 || remaining > snapshot.totalBytes)) return ledger
    val scope = lineScope(snapshot)
    val base = if (ledger.scope != null && ledger.scope != scope) UsageLedger() else ledger
    val date = snapshot.fetchedAt.atZone(zone).toLocalDate()
    val last = base.latest
    if (last != null && snapshot.fetchedAt <= last.fetchedAt) return ledger
    val fetchDate = snapshot.fetchedAt.atZone(zone).toLocalDate()
    val hint = snapshot.cycleEnd ?: snapshot.daysUntilReset?.takeIf { it in 0..366 }?.let { fetchDate.plusDays(it.toLong()) }
    val sameDay = last?.takeIf { it.date == date }
    val day = LedgerDay(
        date = date,
        remainingBytes = remaining,
        totalBytes = snapshot.totalBytes,
        fetchedAt = snapshot.fetchedAt,
        resetHint = hint,
        topUpSeen = sameDay != null && (sameDay.topUpSeen || remaining > sameDay.remainingBytes),
    )
    val days = (if (sameDay != null) base.days.dropLast(1) else base.days) + day
    return UsageLedger(scope, days.takeLast(LEDGER_MAX_DAYS))
}

object UsageLedgerCodec {
    fun encode(ledger: UsageLedger): String = JSONObject().apply {
        put("scope", ledger.scope)
        put("days", JSONArray().apply {
            ledger.days.forEach { day ->
                put(JSONObject().apply {
                    put("d", day.date.toString())
                    put("r", day.remainingBytes)
                    put("t", day.totalBytes)
                    put("at", day.fetchedAt.toString())
                    put("h", day.resetHint?.toString())
                    if (day.topUpSeen) put("up", true)
                })
            }
        })
    }.toString()

    fun decode(raw: String?): UsageLedger? = runCatching {
        if (raw == null) return null
        val root = JSONObject(raw)
        val array = root.getJSONArray("days")
        require(array.length() <= LEDGER_MAX_DAYS)
        val days = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            LedgerDay(
                date = LocalDate.parse(item.getString("d")),
                remainingBytes = item.getLong("r"),
                totalBytes = if (item.has("t")) item.getLong("t") else null,
                fetchedAt = Instant.parse(item.getString("at")),
                resetHint = if (item.has("h")) LocalDate.parse(item.getString("h")) else null,
                topUpSeen = item.optBoolean("up", false),
            ).also {
                require(it.remainingBytes >= 0)
                require(it.totalBytes == null || it.totalBytes >= it.remainingBytes)
            }
        }
        require(days.zipWithNext().all { (a, b) -> a.date < b.date && a.fetchedAt < b.fetchedAt })
        UsageLedger(if (root.has("scope")) root.getString("scope") else null, days)
    }.getOrNull()

    /** Converts the 1.1 reading list (7-day window) into per-day entries so no recorded history is lost. */
    fun fromLegacy(history: List<UsageObservation>, zone: ZoneId = ZoneId.systemDefault()): UsageLedger {
        val days = history.groupBy { it.fetchedAt.atZone(zone).toLocalDate() }.toSortedMap().map { (date, readings) ->
            val last = readings.maxBy { it.fetchedAt }
            LedgerDay(
                date = date,
                remainingBytes = last.remainingBytes,
                totalBytes = last.totalBytes,
                fetchedAt = last.fetchedAt,
                resetHint = last.resetDate,
                topUpSeen = readings.sortedBy { it.fetchedAt }.zipWithNext().any { (a, b) -> b.remainingBytes > a.remainingBytes },
            )
        }
        return UsageLedger(history.lastOrNull()?.scope, days.takeLast(LEDGER_MAX_DAYS))
    }
}
