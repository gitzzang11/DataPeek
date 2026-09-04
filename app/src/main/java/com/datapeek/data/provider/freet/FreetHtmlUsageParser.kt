package com.datapeek.data.provider.freet

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.calculateRemainingPercent
import com.datapeek.data.util.parseDataSize
import org.jsoup.Jsoup
import java.time.Instant

class FreetHtmlUsageParser {
    fun parse(payload: String, fetchedAt: Instant = Instant.now()): UsageSnapshot {
        val document = Jsoup.parse(payload)
        if (document.title().contains("로그인") || document.select("h2").any { it.text().contains("로그인/회원가입") }) {
            throw UsageException(UsageError.SessionExpired)
        }

        val dataRow = document.select("table tr").firstOrNull { row ->
            row.select("th,td").firstOrNull()?.text()?.contains("Data", ignoreCase = true) == true
        } ?: throw UsageException(UsageError.ParseError("데이터 사용량 행이 없습니다."))

        val cells = dataRow.select("th,td").map { it.text().trim() }
        if (cells.size < 4) throw UsageException(UsageError.ParseError("데이터 사용량 열이 부족합니다."))
        val total = parseDataSize(cells[1])
        val used = parseDataSize(cells[2])
        val remaining = parseDataSize(cells[3])
        if (total == null || used == null || remaining == null) {
            throw UsageException(UsageError.ParseError("데이터 사용량 숫자를 해석하지 못했습니다."))
        }

        val resetDays = RESET_DAYS.find(document.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
        return UsageSnapshot(
            carrier = Carrier.FREET_SKT,
            lineName = document.select("aside li").firstOrNull()?.text()?.trim(),
            totalBytes = total,
            usedBytes = used,
            remainingBytes = remaining,
            remainingPercent = calculateRemainingPercent(remaining, total),
            daysUntilReset = resetDays,
            fetchedAt = fetchedAt,
            source = UsageSource.WEB_PAGE,
        )
    }

    companion object {
        private val RESET_DAYS = Regex("잔여일\\s*[:：]?\\s*(\\d+)\\s*일")
    }
}
