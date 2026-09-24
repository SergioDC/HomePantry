package com.homepantry.app.data

/**
 * Ingredientes del plato que no están en ninguna zona ni en la lista de la compra.
 * Compara por [normalizeProductName]: un producto con el mismo nombre normalizado cuenta
 * como "lo tengo" (done) o "ya está pedido" (pendiente), y en ambos casos no se sugiere.
 * Un ingrediente repetido en el plato se devuelve una sola vez; los nombres en blanco se ignoran.
 */
fun missingIngredients(dish: Dish, items: List<Item>): List<Ingredient> {
    val known = items.map { normalizeProductName(it.name) }.toSet()
    val seen = mutableSetOf<String>()
    return dish.ingredients.filter { ingredient ->
        val key = normalizeProductName(ingredient.name)
        key.isNotEmpty() && key !in known && seen.add(key)
    }
}

/** Ingrediente que falta junto con la zona elegida para añadirlo a la lista de la compra. */
data class MissingChoice(val ingredient: Ingredient, val zoneId: String)

/** Productos pendientes (`done = false`) para las elecciones del usuario; sin cantidad, 1 unidad. */
fun itemsForMissing(choices: List<MissingChoice>, addedBy: String): List<Item> =
    choices.map { choice ->
        Item(
            name = choice.ingredient.name.trim(),
            qty = choice.ingredient.qty ?: 1.0,
            unit = choice.ingredient.unit ?: Unit.UD.name,
            zone = choice.zoneId,
            done = false,
            addedBy = addedBy
        )
    }

/**
 * Convierte una fila del formulario en un [Ingredient]. Acepta coma decimal; una cantidad vacía,
 * no numérica o menor o igual que 0 se descarta y con ella la unidad. Nombre en blanco: null.
 */
fun parseIngredient(name: String, qtyText: String, unit: String): Ingredient? {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return null
    val qty = qtyText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    return Ingredient(name = cleanName, qty = qty, unit = if (qty != null) unit else null)
}
