package com.homepantry.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(classifyHttpErrorCode(418))
    }

    @Test fun `treats 500 502 503 and 504 as transient and everything else as not`() {
        listOf(500, 502, 503, 504).forEach { assertTrue("$it", isTransientHttpCode(it)) }
        listOf(200, 400, 401, 403, 404, 429).forEach { assertFalse("$it", isTransientHttpCode(it)) }
    }

    @Test fun `extracts the message from a Google error body`() {
        val body = """{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}"""
        assertEquals("The model is overloaded. Please try again later.", extractGoogleErrorMessage(body))
    }

    @Test fun `returns null when the error body has no readable message`() {
        assertNull(extractGoogleErrorMessage(null))
        assertNull(extractGoogleErrorMessage(""))
        assertNull(extractGoogleErrorMessage("not json"))
        assertNull(extractGoogleErrorMessage("""["not","an","object"]"""))
        assertNull(extractGoogleErrorMessage("""{"other":"field"}"""))
        assertNull(extractGoogleErrorMessage("""{"error":{"message":"  "}}"""))
    }

    @Test fun `a transient failure carries the status code and Google's message`() {
        val body = """{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}"""
        val failure = httpFailure(503, body)
        assertTrue(failure is GeminiServerException)
        assertTrue(failure.message!!.contains("503"))
        assertTrue(failure.message!!.contains("The model is overloaded."))
    }

    @Test fun `a transient failure without a body still reports the status code`() {
        val failure = httpFailure(503, null)
        assertTrue(failure is GeminiServerException)
        assertTrue(failure.message!!.contains("503"))
    }

    @Test fun `an unexpected non-transient failure carries the status code and Google's message`() {
        val failure = httpFailure(418, """{"error":{"message":"Soy una tetera"}}""")
        assertTrue(failure is GeminiResponseException)
        assertTrue(failure.message!!.contains("418"))
        assertTrue(failure.message!!.contains("Soy una tetera"))
    }

    @Test fun `retries a transient failure with a growing delay until it succeeds`() {
        var attempts = 0
        val sleeps = mutableListOf<Long>()
        val result = runBlocking {
            retryOnTransientGeminiError(maxAttempts = 3, initialDelayMs = 100, sleep = { sleeps += it }) {
                attempts++
                if (attempts < 3) throw GeminiServerException(503, null)
                "ok"
            }
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(listOf(100L, 200L), sleeps)
    }

    @Test fun `gives up after the last attempt and rethrows the transient failure`() {
        var attempts = 0
        val sleeps = mutableListOf<Long>()
        assertThrows(GeminiServerException::class.java) {
            runBlocking {
                retryOnTransientGeminiError(maxAttempts = 3, initialDelayMs = 100, sleep = { sleeps += it }) {
                    attempts++
                    throw GeminiServerException(503, null)
                }
            }
        }
        assertEquals(3, attempts)
        assertEquals(listOf(100L, 200L), sleeps)
    }

    @Test fun `does not retry errors that another attempt cannot fix`() {
        var attempts = 0
        assertThrows(GeminiAuthException::class.java) {
            runBlocking {
                retryOnTransientGeminiError(maxAttempts = 3, initialDelayMs = 100, sleep = {}) {
                    attempts++
                    throw GeminiAuthException()
                }
            }
        }
        assertEquals(1, attempts)
    }

    @Test fun `does not wait when the first attempt succeeds`() {
        val sleeps = mutableListOf<Long>()
        val result = runBlocking {
            retryOnTransientGeminiError(maxAttempts = 3, initialDelayMs = 100, sleep = { sleeps += it }) { "ok" }
        }
        assertEquals("ok", result)
        assertTrue(sleeps.isEmpty())
    }

    @Test fun `the model list offers the current models and not the retired one`() {
        assertTrue(DEFAULT_GEMINI_MODEL in GEMINI_MODEL_OPTIONS)
        assertTrue("gemini-3.7-flash" in GEMINI_MODEL_OPTIONS)
        assertTrue("gemini-3.1-flash-lite" in GEMINI_MODEL_OPTIONS)
        assertFalse("gemini-2.5-flash" in GEMINI_MODEL_OPTIONS)
        assertEquals(GEMINI_MODEL_OPTIONS.distinct(), GEMINI_MODEL_OPTIONS)
    }
}
