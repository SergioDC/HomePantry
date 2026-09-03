package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdCodeTest {
    @Test fun `generated code matches CASA-#### format`() {
        val code = generateHouseholdCode()
        assertTrue(code.matches(Regex("^CASA-\\d{4}$")))
    }

    @Test fun `valid code accepted, lowercase and untrimmed normalized`() {
        assertTrue(isValidHouseholdCode("CASA-4821"))
        assertTrue(isValidHouseholdCode(" casa-4821 "))
    }

    @Test fun `invalid codes rejected`() {
        assertFalse(isValidHouseholdCode("CASA-482"))
        assertFalse(isValidHouseholdCode("CASA4821"))
        assertFalse(isValidHouseholdCode(""))
    }

    @Test fun `normalize trims and uppercases`() {
        assertEquals("CASA-4821", normalizeHouseholdCode(" casa-4821 "))
    }
}
