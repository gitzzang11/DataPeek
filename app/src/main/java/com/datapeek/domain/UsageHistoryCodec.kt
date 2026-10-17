package com.datapeek.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** Stores only counters, dates and an opaque line fingerprint, never login/session information. */
object UsageHistoryCodec {
    fun encode(history: List<UsageObservation>): String = JSONArray().apply {
        history.forEach { point ->
            put(JSONObject().apply {
                put("scope", point.scope)
                put("total", point.totalBytes)
                put("remaining", point.remainingBytes)
                put("at", point.fetchedAt.toString())
                put("start", point.cycleStart?.toString())
                put("reset", point.resetDate?.toString())
            })
        }
    }.toString()

    fun decode(raw: String?): List<UsageObservation> = runCatching {
        if (raw == null) return emptyList()
        val array = JSONArray(raw)
        require(array.length() <= 200)
        List(array.length()) { index ->
            val point = array.getJSONObject(index)
            UsageObservation(
                scope = point.getString("scope"),
                totalBytes = if (point.has("total")) point.getLong("total") else null,
                remainingBytes = point.getLong("remaining"),
                fetchedAt = Instant.parse(point.getString("at")),
                cycleStart = if (point.has("start")) LocalDate.parse(point.getString("start")) else null,
                resetDate = if (point.has("reset")) LocalDate.parse(point.getString("reset")) else null,
            ).also {
                require(it.remainingBytes >= 0)
                require(it.totalBytes == null || it.totalBytes >= it.remainingBytes)
            }
        }.also { points -> require(points.zipWithNext().all { (a, b) -> a.fetchedAt < b.fetchedAt }) }
    }.getOrDefault(emptyList())
}
