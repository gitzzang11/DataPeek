package com.datapeek.data.provider.freet

import com.datapeek.data.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FreetHtmlUsageParserTest {
    @Test
    fun parsesSanitizedUsagePageHtml() {
        val html = """
            <html><head><title>사용량 조회 l MY l 프리티 모바일</title></head>
            <body><main>
              <ul><li>잔여일 : 2일</li></ul>
              <table><tbody><tr>
                <th>MVNO Data 20GB 공제 (LTE)</th>
                <td>20G</td><td>19.02G</td><td>999.81M</td>
              </tr></tbody></table>
            </main></body></html>
        """.trimIndent()

        val snapshot = FreetHtmlUsageParser().parse(html, Instant.parse("2026-08-29T03:05:00Z"))

        assertEquals("20 GB", formatBytes(snapshot.totalBytes))
        assertEquals("19.02 GB", formatBytes(snapshot.usedBytes))
        assertEquals("999.81 MB", formatBytes(snapshot.remainingBytes))
        assertEquals(2, snapshot.daysUntilReset)
        assertEquals(4.9999f, snapshot.remainingPercent!!, 0.2f)
    }

    @Test
    fun recognizesLoginHtml() {
        val result = runCatching {
            FreetHtmlUsageParser().parse("<html><head><title>로그인</title></head></html>")
        }
        assertTrue(result.exceptionOrNull() is com.datapeek.data.model.UsageException)
    }
}
