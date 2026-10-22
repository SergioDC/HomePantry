package com.homepantry.app.data

import java.text.Normalizer
import java.util.Locale

/** Tipo de gasto de SNcount, deducido del concepto para pintar su icono. No se guarda en Firestore. */
enum class ExpenseCategory {
    SUPERMARKET, COFFEE, RESTAURANT, DRINKS, CAR, TRANSPORT, FLIGHT, HOTEL, HOME,
    SHOPPING, GIFT, LEISURE, HEALTH, PETS, SPORT, OTHER
}

/**
 * Palabras clave por tipo, sin tildes y en minúsculas. El orden importa: gana el primer tipo
 * con alguna coincidencia (por eso «cafe» va antes que los restaurantes).
 */
private val CATEGORY_KEYWORDS: List<Pair<ExpenseCategory, List<String>>> = listOf(
    ExpenseCategory.SUPERMARKET to listOf(
        "super", "supermercado", "mercadona", "carrefour", "lidl", "dia", "aldi", "alcampo",
        "eroski", "hipercor", "consum", "compra", "mercado", "fruteria", "carniceria", "panaderia"
    ),
    ExpenseCategory.COFFEE to listOf("cafe", "cafes", "cafeteria", "desayuno", "starbucks"),
    ExpenseCategory.RESTAURANT to listOf(
        "restaurante", "cena", "cenas", "comida", "comidas", "almuerzo", "bar", "pizza", "pizzeria",
        "burger", "hamburguesa", "sushi", "tapas", "menu", "kebab", "japones", "chino", "glovo", "telepizza"
    ),
    ExpenseCategory.DRINKS to listOf("copas", "copa", "cerveza", "birras", "vino", "bebidas", "bebida", "chupitos"),
    ExpenseCategory.CAR to listOf(
        "gasolina", "gasolinera", "gasoil", "diesel", "combustible", "parking", "aparcamiento",
        "peaje", "coche", "taller", "taxi", "uber", "cabify", "itv"
    ),
    ExpenseCategory.FLIGHT to listOf("vuelo", "vuelos", "avion", "aeropuerto", "ryanair", "vueling", "iberia"),
    ExpenseCategory.TRANSPORT to listOf("tren", "renfe", "bus", "autobus", "metro", "billete", "billetes", "blablacar"),
    ExpenseCategory.HOTEL to listOf("hotel", "airbnb", "apartamento", "alojamiento", "hostal", "booking", "camping"),
    ExpenseCategory.HOME to listOf(
        "alquiler", "luz", "agua", "gas", "internet", "wifi", "factura", "comunidad", "hipoteca",
        "limpieza", "ikea", "muebles", "seguro"
    ),
    ExpenseCategory.GIFT to listOf("regalo", "regalos", "cumpleanos"),
    ExpenseCategory.SHOPPING to listOf("ropa", "zapatos", "zara", "primark", "amazon", "tienda"),
    ExpenseCategory.LEISURE to listOf(
        "cine", "entradas", "entrada", "concierto", "teatro", "museo", "parque", "netflix",
        "spotify", "excursion", "juego", "bolera"
    ),
    ExpenseCategory.HEALTH to listOf("farmacia", "medico", "dentista", "hospital", "clinica", "medicinas"),
    ExpenseCategory.PETS to listOf("veterinario", "veterinaria", "perro", "gato", "pienso", "mascota"),
    ExpenseCategory.SPORT to listOf("gimnasio", "gym", "padel", "futbol", "piscina", "deporte")
)

/** Las palabras clave de 5 letras o más valen también como prefijo («hoteles», «cervezas»). */
private const val PREFIX_MIN_LENGTH = 5

/**
 * Tipo de un gasto según su concepto. Las palabras cortas solo cuentan enteras para que «dia»
 * (el súper) no se cuele en «diario» ni «bar» en «barco». Sin coincidencias, [ExpenseCategory.OTHER].
 */
fun expenseCategoryOf(title: String): ExpenseCategory {
    val words = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() }
    if (words.isEmpty()) return ExpenseCategory.OTHER
    return CATEGORY_KEYWORDS.firstOrNull { (_, keywords) ->
        keywords.any { keyword ->
            words.any { word -> word == keyword || (keyword.length >= PREFIX_MIN_LENGTH && word.startsWith(keyword)) }
        }
    }?.first ?: ExpenseCategory.OTHER
}

/**
 * Gasto por tipo, de mayor a menor, para la barra de reparto del grupo. Los reembolsos mueven
 * dinero entre miembros y no son gasto de ningún tipo.
 */
fun categoryTotals(expenses: List<SplitExpense>): List<Pair<ExpenseCategory, Long>> =
    expenses.filter { !it.reimbursement && it.amountCents > 0 }
        .groupBy { expenseCategoryOf(it.title) }
        .map { (category, list) -> category to list.sumOf { it.amountCents } }
        .sortedByDescending { it.second }
