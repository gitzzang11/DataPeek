package com.datapeek.data.provider.freet

import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.DataUnit
import com.datapeek.data.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FreetUsageParserTest {
    private val parser = FreetUsageParser()

    @Test
    fun parsesConfirmedFreetRawKibFixtureAndUsesRawValuesAsSourceOfTruth() {
        val payload = javaClass.getResource("/freet_usage_api_real_shape.json")!!.readText()

        val result = parser.parseDetailed(payload, Instant.parse("2026-08-29T12:00:00Z"))
        val snapshot = result.snapshot!!

        assertEquals(20L * 1024 * 1024 * 1024, snapshot.totalBytes)
        assertEquals(315733L * 1024, snapshot.usedBytes)
        assertEquals(20655787L * 1024, snapshot.remainingBytes)
        assertEquals(snapshot.totalBytes, snapshot.usedBytes!! + snapshot.remainingBytes!!)
        assertEquals("20 GB", formatBytes(snapshot.totalBytes))
        assertEquals("308.33 MB", formatBytes(snapshot.usedBytes))
        assertEquals("19.7 GB", formatBytes(snapshot.remainingBytes))
        assertEquals(UsageSource.CARRIER_SERVER, snapshot.source)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.usageValuesValid)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.snapshotCreated)
        assertEquals("DATA_BASIC_1", result.diagnostics.dataEntry?.dataType)
        assertEquals(DataUnit.KIB, result.diagnostics.dataEntry?.totalUnit)
        assertEquals(DataUnit.KIB, result.diagnostics.dataEntry?.usedUnit)
        assertEquals(DataUnit.KIB, result.diagnostics.dataEntry?.remainingUnit)
    }

    @Test
    fun rawKibFieldsTakePriorityOverOfficialTextFields() {
        val snapshot = parser.parse(
            """{"data":{"DATA":[
                {"total":"20971520","totalText":"1G","use":"315733","useText":"1M","remain":"20655787","remainText":"2M","dataType":"DATA_BASIC_1"}
            ]}}""".replace("\n", ""),
        )

        assertEquals(20971520L * 1024, snapshot.totalBytes)
        assertEquals(315733L * 1024, snapshot.usedBytes)
        assertEquals(20655787L * 1024, snapshot.remainingBytes)
    }

    @Test
    fun fallsBackToCompoundTextWhenRawKibFieldIsMissingOrInvalid() {
        val snapshot = parser.parse(
            """{"data":{"DATA":[
                {"total":"not-numeric","totalText":"20G","use":null,"useText":"308M 341K","remain":"bad","remainText":"19G 715M","dataType":"DATA_BASIC_1"}
            ]}}""".replace("\n", ""),
        )

        assertEquals(20L * 1024 * 1024 * 1024, snapshot.totalBytes)
        assertEquals(308L * 1024 * 1024 + 341L * 1024, snapshot.usedBytes)
        assertEquals(19L * 1024 * 1024 * 1024 + 715L * 1024 * 1024, snapshot.remainingBytes)
    }

    @Test
    fun dataTypeIsNotUsedAsAUnitFallback() {
        val result = parser.parseDetailed(
            """{"data":{"DATA":[{"totalText":"20","useText":"0.3","remainText":"19.7","dataType":"DATA_BASIC_1"}]}}""",
        )

        assertNull(result.snapshot)
        assertEquals(UsageParseStatus.FAIL, result.diagnostics.usageValuesValid)
    }

    @Test
    fun derivesRemainingPercentFromRawBytesInsteadOfRemainRate() {
        val result = parser.parseDetailed(
            """{"data":{"DATA":[{"total":"20971520","use":"315733","remain":"20655787","remainRate":"1","dataType":"DATA_BASIC_1"}]}}""",
        )

        assertEquals(98.5f, result.snapshot?.remainingPercent ?: 0f, 0.1f)
    }

    @Test
    fun textOnlyFreetValuesSupportCompoundUnits() {
        val snapshot = parser.parse(
            """{"data":{"DATA":[{"totalText":"20G","useText":"308M 341K","remainText":"19G 715M"}]}}""",
        )

        assertEquals(20L * 1024 * 1024 * 1024, snapshot.totalBytes)
        assertEquals(308L * 1024 * 1024 + 341L * 1024, snapshot.usedBytes)
        assertEquals(19L * 1024 * 1024 * 1024 + 715L * 1024 * 1024, snapshot.remainingBytes)
    }

    @Test
    fun exposesOnlyConfirmedDataEntryFieldsAndParsedSourceUnitsInDiagnostics() {
        val result = parser.parseDetailed(
            """{"data":{"DATA":[{"total":"20971520","totalText":"20G","use":"315733","useText":"308M 341K","remain":"20655787","remainText":"19G 715M","remainRate":"1","dataType":"DATA_BASIC_1"}]}}""",
        )
        val data = result.diagnostics.dataEntry!!

        assertEquals("20971520", data.total)
        assertEquals("20G", data.totalText)
        assertEquals("315733", data.use)
        assertEquals("308M 341K", data.useText)
        assertEquals("20655787", data.remain)
        assertEquals("19G 715M", data.remainText)
        assertEquals("1", data.remainRate)
        assertEquals("DATA_BASIC_1", data.dataType)
        assertEquals(DataUnit.KIB, data.totalUnit)
        assertEquals(DataUnit.KIB, data.usedUnit)
        assertEquals(DataUnit.KIB, data.remainingUnit)
    }

    @Test
    fun rejectsMissingEmptyNullAndNonDataArrays() {
        val missing = parser.parseDetailed("""{"data":{}}""")
        val empty = parser.parseDetailed("""{"data":{"DATA":[]}}""")
        val nullEntry = parser.parseDetailed("""{"data":{"DATA":[null]}}""")
        val voiceOnly = parser.parseDetailed("""{"data":{"VOICE":[{"total":"20G"}]}}""")
        val smsOnly = parser.parseDetailed("""{"data":{"SMS":[{"total":"20G"}]}}""")

        listOf(missing, empty, nullEntry, voiceOnly, smsOnly).forEach {
            assertNull(it.snapshot)
            assertEquals(UsageParseStatus.FAIL, it.diagnostics.schemaMatched)
        }
    }

    @Test
    fun rejectsUnusableCoreValuesAndOverflow() {
        val zero = runCatching {
            parser.parse("""{"data":{"DATA":[{"total":"0","use":"0","remain":"0"}]}}""")
        }.exceptionOrNull()
        val negative = runCatching {
            parser.parse("""{"data":{"DATA":[{"total":"20G","use":"-1G","remain":"19G"}]}}""")
        }.exceptionOrNull()
        val impossible = runCatching {
            parser.parse("""{"data":{"DATA":[{"total":"20","use":"1","remain":"21"}]}}""")
        }.exceptionOrNull()
        val overflow = runCatching {
            parser.parse("""{"data":{"DATA":[{"total":"999999999999999999999999999999","use":"1","remain":"1"}]}}""")
        }.exceptionOrNull()

        assertTrue(zero is UsageException)
        assertTrue(negative is UsageException)
        assertTrue(impossible is UsageException)
        assertTrue(overflow is UsageException)
    }

    @Test
    fun recognizesLoginHtml() {
        val error = runCatching { parser.parse("<html><body>로그인</body></html>") }.exceptionOrNull()
        assertTrue(error is UsageException)
        assertEquals(UsageError.SessionExpired, (error as UsageException).error)
    }

    @Test
    fun parseDiagnosticsReachSnapshotCreatedOnlyForAUsableSnapshot() {
        val result = parser.parseDetailed(
            """{"data":{"DATA":[{"total":"20","use":"1","remain":"19","remainRate":"1"}]}}""",
        )

        assertNotNull(result.snapshot)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.jsonSyntax)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.schemaMatched)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.usageFieldsFound)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.usageValuesValid)
        assertEquals(UsageParseStatus.SUCCESS, result.diagnostics.snapshotCreated)
        assertEquals(95f, result.snapshot?.remainingPercent)
    }
}
