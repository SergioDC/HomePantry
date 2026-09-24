package com.homepantry.app.data

import java.time.DayOfWeek
import java.time.YearMonth

/** Los platos que Gemini leyó bajo el número de un día del menú mensual. */
data class ParsedMenuDay(val day: Int, val dishes: List<String>)

/** Menú mensual leído de una foto. `month` es el mes que Gemini leyó en la cabecera, si lo hizo. */
data class ParsedMenu(val month: YearMonth?, val days: List<ParsedMenuDay>)

/**
 * Lo que hay que escribir al importar: los platos nuevos (ya con id) y las entradas de la comida
 * (sin id, apuntando a esos platos o a los que ya existían). `alreadyPresent` cuenta los platos
 * omitidos por estar ya en esa comida y `droppedDays` los días que el mes elegido no tiene.
 */
data class MenuImportPlan(
    val newDishes: List<Dish>,
    val entries: List<MealEntry>,
    val alreadyPresent: Int,
    val droppedDays: Int
)

private const val BREAD_KEY = "pan"

/** Un nombre entero en MAYÚSCULAS (típico de los menús impresos) pasa a «Mayúscula inicial». */
private fun tidyDishName(raw: String): String {
    val name = raw.trim()
    return if (name.any { it.isLetter() } && name == name.uppercase()) {
        name.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        name
    }
}

/**
 * Deja el resultado de Gemini listo para revisar: sin platos vacíos ni repetidos en un mismo día
 * (ignorando mayúsculas, acentos y plural simple), sin días fuera de 1..31, con un mismo día
 * fusionado y ordenado, y sin pan (red de seguridad por si el prompt falla).
 */
fun cleanParsedMenu(month: YearMonth?, days: List<ParsedMenuDay>): ParsedMenu {
    val merged = sortedMapOf<Int, MutableList<String>>()
    for (day in days) {
        if (day.day !in 1..31) continue
        val bucket = merged.getOrPut(day.day) { mutableListOf() }
        for (raw in day.dishes) {
            val name = tidyDishName(raw)
            val key = normalizeProductName(name)
            if (key.isEmpty() || key == BREAD_KEY) continue
            if (bucket.none { normalizeProductName(it) == key }) bucket += name
        }
    }
    return ParsedMenu(
        month = month,
        days = merged.filterValues { it.isNotEmpty() }.map { (day, dishes) -> ParsedMenuDay(day, dishes.toList()) }
    )
}

/**
 * Plan para volcar [menu] en [month]: cada plato va como entrada de la comida de su día. Nunca
 * borra nada: va detrás de lo que ya haya en esa comida y omite lo que ya está (mismo nombre
 * normalizado), de modo que importar dos veces la misma foto no duplica. Un plato sin coincidencia
 * exacta con [dishes] se crea una sola vez aunque salga en muchos días; [newId] da su id. Todas las
 * entradas van a [person] (null = sin asignar, ver [normalizePerson]); el mismo plato para otra
 * persona el mismo día no cuenta como repetido, pero el plato en sí se comparte entre personas.
 */
fun planMenuImport(
    menu: ParsedMenu,
    month: YearMonth,
    dishes: List<Dish>,
    existing: List<MealEntry>,
    newId: () -> String,
    addedBy: String,
    person: String? = null
): MenuImportPlan {
    val targetPerson = personKey(person.orEmpty())
    val lunchByDate = existing.filter { it.slot == MealSlot.LUNCH.name }.groupBy { it.date }
    val newDishes = linkedMapOf<String, Dish>()
    val entries = mutableListOf<MealEntry>()
    var alreadyPresent = 0
    var droppedDays = 0

    for (day in menu.days.sortedBy { it.day }) {
        if (day.day !in 1..month.lengthOfMonth()) {
            droppedDays++
            continue
        }
        val date = month.atDay(day.day).toString()
        val present = lunchByDate[date].orEmpty()
        // Lo ya presente cuenta solo para la misma persona: (plato normalizado, persona normalizada).
        val seen = present
            .filter { personKey(it.person.orEmpty()) == targetPerson }
            .map { normalizeProductName(it.name) }
            .toMutableSet()
        var order = (present.maxOfOrNull { it.order } ?: -1) + 1
        for (raw in day.dishes) {
            val name = raw.trim()
            val key = normalizeProductName(name)
            if (key.isEmpty()) continue
            if (!seen.add(key)) {
                alreadyPresent++
                continue
            }
            val dish = matchDish(name, dishes)
                ?: newDishes.getOrPut(key) { Dish(id = newId(), name = name, addedBy = addedBy) }
            entries += MealEntry(
                date = date,
                slot = MealSlot.LUNCH.name,
                dishId = dish.id,
                name = dish.name,
                order = order++,
                addedBy = addedBy,
                person = person
            )
        }
    }
    return MenuImportPlan(newDishes.values.toList(), entries, alreadyPresent, droppedDays)
}

/**
 * Días leídos que caen en sábado o domingo de [month]. El menú es solo de lunes a viernes, así que
 * un resultado mayor que 0 indica que el mes (o el año) elegido no es el de la foto.
 */
fun weekendDayCount(menu: ParsedMenu, month: YearMonth): Int =
    menu.days.count {
        it.day in 1..month.lengthOfMonth() &&
            month.atDay(it.day).dayOfWeek.let { d -> d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY }
    }
