package com.datapeek.data.provider.freet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetUsageJsonStructureTest {
    @Test
    fun reportsJsonStructureAndUsageCandidatesWithoutValues() {
        val payload = javaClass.getResource("/freet_usage_api_real_shape.json")!!.readText()

        val result = inspectFreetUsageJsonStructure(payload)

        assertEquals(UsageJsonNodeType.OBJECT, result.rootType)
        assertEquals(UsageParseStatus.SUCCESS, result.jsonSyntax)
        assertTrue(result.nodes.any { it.key == "data" && it.type == UsageJsonNodeType.OBJECT })
        assertTrue(result.nodes.any { it.key == "DATA" && it.type == UsageJsonNodeType.ARRAY })
        assertTrue(result.nodes.any { it.path == "\$.data.DATA[0].totalText" && it.isUsageCandidate })
        assertTrue(result.nodes.any { it.path == "\$.data.DATA[0].remainRate" && it.isUsageCandidate })
        assertTrue(result.valuesRedacted)
    }

    @Test
    fun invalidJsonReportsSyntaxFailureWithoutExposingInput() {
        val result = inspectFreetUsageJsonStructure("not-json")

        assertEquals(UsageJsonNodeType.INVALID, result.rootType)
        assertEquals(UsageParseStatus.FAIL, result.jsonSyntax)
        assertTrue(result.nodes.isEmpty())
    }
}
