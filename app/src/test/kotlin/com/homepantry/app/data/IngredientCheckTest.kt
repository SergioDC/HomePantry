package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientCheckTest {
    private fun dish(vararg names: String) = Dish(name = "Plato", ingredients = names.map { Ingredient(name = it) })
    private fun item(name: String, done: Boolean) = Item(name = name, done = done, zone = "z")

    @Test fun `does not suggest what you already have`() {
        val missing = missingIngredients(dish("tomate"), listOf(item("Tomates", done = true)))
        assertEquals(emptyList<Ingredient>(), missing)
    }

    @Test fun `does not suggest what is already on the shopping list`() {
        val missing = missingIngredients(dish("leche"), listOf(item("Leche", done = false)))
        assertEquals(emptyList<Ingredient>(), missing)
    }

    @Test fun `suggests ingredients without a matching product`() {
        val missing = missingIngredients(dish("Harina", "Huevos"), listOf(item("Leche", done = true)))
        assertEquals(listOf("Harina", "Huevos"), missing.map { it.name })
    }

    @Test fun `matching ignores case, accents and simple plural`() {
        val items = listOf(item("PLÁTANOS", done = true))
        assertEquals(emptyList<Ingredient>(), missingIngredients(dish("platano"), items))
    }

    @Test fun `repeated ingredients are returned once`() {
        val missing = missingIngredients(dish("Huevos", "huevo"), emptyList())
        assertEquals(listOf("Huevos"), missing.map { it.name })
    }

    @Test fun `blank ingredient names are ignored`() {
        val missing = missingIngredients(dish("  ", "Sal"), emptyList())
        assertEquals(listOf("Sal"), missing.map { it.name })
    }

    @Test fun `itemsForMissing builds pending items with the chosen zone`() {
        val items = itemsForMissing(
            listOf(
                MissingChoice(Ingredient(name = "Harina ", qty = 500.0, unit = "G"), "z1"),
                MissingChoice(Ingredient(name = "Sal"), "z2")
            ),
            addedBy = "Ana"
        )
        assertEquals(Item(name = "Harina", qty = 500.0, unit = "G", zone = "z1", done = false, addedBy = "Ana"), items[0])
        assertEquals(Item(name = "Sal", qty = 1.0, unit = "UD", zone = "z2", done = false, addedBy = "Ana"), items[1])
    }

    @Test fun `parseIngredient trims the name and parses quantity with unit`() {
        assertEquals(Ingredient("Harina", 500.0, "G"), parseIngredient("  Harina ", "500", "G"))
    }

    @Test fun `parseIngredient accepts a decimal comma`() {
        assertEquals(1.5, parseIngredient("Leche", "1,5", "L")?.qty)
    }

    @Test fun `parseIngredient drops the unit when there is no valid quantity`() {
        assertEquals(Ingredient("Sal", null, null), parseIngredient("Sal", "", "G"))
        assertEquals(Ingredient("Sal", null, null), parseIngredient("Sal", "0", "KG"))
        assertEquals(Ingredient("Pan", null, null), parseIngredient("Pan", "abc", "UD"))
    }

    @Test fun `parseIngredient returns null for a blank name`() {
        assertNull(parseIngredient("   ", "2", "UD"))
    }
}
