package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiReceiptRecognizerTest {
    @Test fun `maps a well-formed products JSON to parsed lines`() {
        val json = """{"products":[{"name":"TOMATE RAMA","price":1.5},{"name":"LECHE ENTERA","price":0.89}]}"""
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.5), ParsedReceiptLine("LECHE ENTERA", 0.89)),
            mapGeminiOutputTextToLines(json)
        )
    }

    @Test fun `skips entries missing a name or price instead of crashing`() {
        val json = """{"products":[
            {"name":"TOMATE RAMA","price":1.5},
            {"name":null,"price":2.0},
            {"name":"SIN PRECIO","price":null}
        ]}"""
        assertEquals(listOf(ParsedReceiptLine("TOMATE RAMA", 1.5)), mapGeminiOutputTextToLines(json))
    }

    @Test fun `skips entries with a zero or negative price`() {
        val json = """{"products":[
            {"name":"TOMATE RAMA","price":1.5},
            {"name":"GRATIS","price":0},
            {"name":"DESCUENTO","price":-0.5}
        ]}"""
        assertEquals(listOf(ParsedReceiptLine("TOMATE RAMA", 1.5)), mapGeminiOutputTextToLines(json))
    }

    @Test fun `throws GeminiResponseException on malformed JSON`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiOutputTextToLines("not json") }
    }

    @Test fun `throws GeminiResponseException when the products field is missing`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiOutputTextToLines("""{"other":"field"}""") }
    }

    @Test fun `extracts the model_output text from a well-formed interaction response`() {
        val response = GeminiInteractionResponse(
            status = "completed",
            steps = listOf(
                GeminiStep(type = "tool_call", content = null),
                GeminiStep(
                    type = "model_output",
                    content = listOf(GeminiStepContent(type = "text", text = """{"products":[]}"""))
                )
            )
        )
        assertEquals("""{"products":[]}""", extractOutputText(response))
    }

    @Test fun `joins every text part of the model_output step`() {
        val response = GeminiInteractionResponse(
            status = "completed",
            steps = listOf(
                GeminiStep(
                    type = "model_output",
                    content = listOf(
                        GeminiStepContent(type = "text", text = "{\"products\":[{\"name\":\"PAN\","),
                        GeminiStepContent(type = "thought", text = "ignorar esto"),
                        GeminiStepContent(type = "text", text = "\"price\":1.2}]}")
                    )
                )
            )
        )
        assertEquals("""{"products":[{"name":"PAN","price":1.2}]}""", extractOutputText(response))
    }

    @Test fun `throws GeminiResponseException when there is no model_output step`() {
        val response = GeminiInteractionResponse(status = "failed", steps = emptyList())
        assertThrows(GeminiResponseException::class.java) { extractOutputText(response) }
    }

    @Test fun `classifies 400 401 and 403 as auth errors`() {
        // generativelanguage devuelve 400 API_KEY_INVALID para una key mal formada.
        assertTrue(classifyHttpErrorCode(400) is GeminiAuthException)
        assertTrue(classifyHttpErrorCode(401) is GeminiAuthException)
        assertTrue(classifyHttpErrorCode(403) is GeminiAuthException)
    }

    @Test fun `classifies 404 as the configured model being unavailable`() {
        assertTrue(classifyHttpErrorCode(404) is GeminiModelUnavailableException)
    }

    @Test fun `classifies 429 as a quota error`() {
        assertTrue(classifyHttpErrorCode(429) is GeminiQuotaException)
    }

    @Test fun `returns null for an unrelated status code`() {
        assertNull(classifyHttpErrorCode(500))
    }
}
