package com.datapeek.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetUsageEvaluateJavascriptDecoderTest {
    @Test
    fun decodesJsonQuotedCallbackExactlyOnce() {
        val body = """{"data":{"total":"24.2 GB","used":"1.5 GB","remaining":"22.7 GB"}}"""

        val result = decodeEvaluateJavascriptString(JSONObject.quote(body))

        assertEquals(body, result.body)
        assertEquals(EvaluateJavascriptCallbackValueType.STRING, result.diagnostics.callbackValueType)
        assertTrue(result.diagnostics.jsonStringWrapperDetected)
        assertEquals(EvaluateJavascriptBodyRootType.OBJECT, result.diagnostics.decodedRootType)
        assertEquals(body.length, result.diagnostics.decodedBodyLength)
    }

    @Test
    fun decodesEscapedQuotesAndUnicodeWithoutStringReplacement() {
        val body = """{"label":"프리티 \"데이터\"","data":[1,2]}"""

        val result = decodeEvaluateJavascriptString(JSONObject.quote(body))

        assertEquals(body, result.body)
        assertEquals(EvaluateJavascriptBodyRootType.OBJECT, result.diagnostics.decodedRootType)
    }

    @Test
    fun classifiesRawArrayAndHtmlBodiesWithoutTreatingThemAsObjects() {
        val array = decodeEvaluateJavascriptString(JSONObject.quote("[1,2,3]"))
        val html = decodeEvaluateJavascriptString(JSONObject.quote("<!DOCTYPE html><html></html>"))

        assertEquals(EvaluateJavascriptBodyRootType.ARRAY, array.diagnostics.decodedRootType)
        assertEquals(EvaluateJavascriptBodyRootType.HTML, html.diagnostics.decodedRootType)
    }
}
