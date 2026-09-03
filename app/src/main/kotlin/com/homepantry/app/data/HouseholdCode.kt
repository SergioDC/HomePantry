package com.homepantry.app.data

import kotlin.random.Random

private val CODE_REGEX = Regex("^CASA-\\d{4}$")

/** Genera un código de casa nuevo, ej. "CASA-4821". */
fun generateHouseholdCode(): String =
    "CASA-" + Random.nextInt(0, 10000).toString().padStart(4, '0')

fun isValidHouseholdCode(code: String): Boolean =
    CODE_REGEX.matches(normalizeHouseholdCode(code))

fun normalizeHouseholdCode(code: String): String = code.trim().uppercase()
