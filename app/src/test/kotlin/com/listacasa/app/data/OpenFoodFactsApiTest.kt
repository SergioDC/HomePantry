package com.listacasa.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsApiTest {
    private val gson = Gson()

    @Test fun `parses product name when found`() {
        val json = """{"status":1,"product":{"product_name":"Leche Entera 1L"}}"""
        val response = gson.fromJson(json, OffResponse::class.java)
        assertEquals(1, response.status)
        assertEquals("Leche Entera 1L", response.product?.productName)
    }

    @Test fun `handles not-found response`() {
        val json = """{"status":0}"""
        val response = gson.fromJson(json, OffResponse::class.java)
        assertEquals(0, response.status)
        assertNull(response.product)
    }
}
