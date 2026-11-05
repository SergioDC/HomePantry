package com.homepantry.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLogicTest {

    private fun recipe(name: String, section: String? = null, book: String? = null, order: Long = 0) =
        Recipe(id = name, sourceId = "s", name = name, section = section, book = book, order = order)

    @Test
    fun `el indice de Future Life trae las recetas de los cuatro libros con ids unicos`() {
        // Los tests JVM se ejecutan desde el módulo app.
        val json = File("src/main/assets/$FUTURLIFE_ASSET").readText()
        val seed = parseRecipeSeed(json, FUTURLIFE_SOURCE_ID)

        assertEquals("Future Life", seed.source.name)
        assertEquals(FUTURLIFE_SOURCE_ID, seed.source.id)
        val perBook = seed.recipes.groupingBy { it.book }.eachCount()
        assertEquals(
            mapOf("Futurlibro 1" to 76, "Futurlibro 2" to 81, "Futurlibro 3" to 75, "Nutre Su Futuro" to 105),
            perBook
        )
        assertEquals(seed.recipes.size, seed.recipes.map { it.id }.toSet().size)
        assertTrue(seed.recipes.all { it.sourceId == FUTURLIFE_SOURCE_ID && it.location == null })
        assertEquals((0L until seed.recipes.size).toList(), seed.recipes.map { it.order })
    }

    @Test
    fun `los ids son estables y la subseccion se conserva`() {
        val json = """
            {"name": "X", "books": [{"key": "b1", "name": "Libro 1", "sections": [
              {"name": "Bases", "sub": "Base de Tomate", "recipes": ["Pisto al Horno", "Pisto al horno"]},
              {"name": "Dulces", "recipes": ["Flan"]}
            ]}]}
        """.trimIndent()
        val recipes = parseRecipeSeed(json, "src").recipes

        assertEquals(listOf("src-b1-pisto-al-horno", "src-b1-pisto-al-horno-2", "src-b1-flan"), recipes.map { it.id })
        assertEquals("Base de Tomate", recipes[0].subsection)
        assertNull(recipes[2].subsection)
    }

    @Test
    fun `la busqueda ignora tildes y mayusculas y exige todas las palabras`() {
        val recipes = listOf(
            recipe("Salchichas con Puré de Patata y Coliflor"),
            recipe("Boloñesa de Bacalao y Berenjena con Puré de Coliflor"),
            recipe("Tortilla de Coliflor")
        )

        assertEquals(2, searchRecipes(recipes, "pure").size)
        assertEquals(
            listOf("Salchichas con Puré de Patata y Coliflor"),
            searchRecipes(recipes, "PURE patata").map { it.name }
        )
        assertTrue(searchRecipes(recipes, "   ").isEmpty())
    }

    @Test
    fun `primero las que casan por nombre y despues las que casan por seccion`() {
        val recipes = listOf(
            recipe("Tarta de Limacate", section = "Dulces y Postres"),
            recipe("Galletas de Avena", section = "Dulces"),
            recipe("Dulces de Membrillo", section = "Otros")
        )

        assertEquals(
            listOf("Dulces de Membrillo", "Galletas de Avena", "Tarta de Limacate"),
            searchRecipes(recipes, "dulces").map { it.name }
        )
    }

    @Test
    fun `agrupa por libro y seccion en el orden de la entrada`() {
        val recipes = listOf(
            recipe("C", section = "Dulces", book = "L1", order = 2),
            recipe("A", section = "Cremas", book = "L1", order = 0),
            recipe("B", section = "Cremas", book = "L1", order = 1),
            recipe("D", section = "Cremas", book = "L2", order = 3),
            recipe("Manual", order = 99)
        )

        val groups = groupRecipes(recipes)

        assertEquals(
            listOf("L1" to "Cremas", "L1" to "Dulces", "L2" to "Cremas", null to null),
            groups.map { it.book to it.section }
        )
        assertEquals(listOf("A", "B"), groups[0].recipes.map { it.name })
    }

    @Test
    fun `solo se abren enlaces web`() {
        assertEquals("https://www.receta.com/pisto", safeRecipeUrl(" www.receta.com/pisto "))
        assertEquals("http://receta.com", safeRecipeUrl("http://receta.com"))
        assertEquals("HTTPS://receta.com", safeRecipeUrl("HTTPS://receta.com"))
        assertNull(safeRecipeUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(safeRecipeUrl("javascript:alert(1)"))
        assertNull(safeRecipeUrl("file:///sdcard/x"))
        assertNull(safeRecipeUrl("https:receta.com"))
        assertNull(safeRecipeUrl("   "))
    }

    @Test
    fun `la subseccion va junto a la seccion en el titulo del grupo`() {
        val groups = groupRecipes(listOf(recipe("A", section = "Bases calientes").copy(subsection = "Base de Tomate")))
        assertEquals("Bases calientes · Base de Tomate", groups.single().section)
    }

    @Test
    fun `tocar la entrada abre la web solo si tiene enlace valido y ninguna receta`() {
        val web = RecipeSource(id = "w", name = "Pisto", url = "www.receta.com/pisto")
        assertTrue(recipeSourceOpensLink(web, recipeCount = 0))
        assertFalse(recipeSourceOpensLink(web, recipeCount = 3))
        assertFalse(recipeSourceOpensLink(web.copy(url = null), recipeCount = 0))
        assertFalse(recipeSourceOpensLink(web.copy(url = "file:///sdcard/x"), recipeCount = 0))
    }

    @Test
    fun `la busqueda de entradas ignora tildes y mayusculas y exige todas las palabras`() {
        val sources = listOf(
            RecipeSource(id = "b", name = "Pisto de mi madre"),
            RecipeSource(id = "a", name = "Blog de Cocína")
        )
        assertEquals(listOf("a"), searchRecipeSources(sources, "COCINA").map { it.id })
        assertEquals(listOf("a", "b"), searchRecipeSources(sources, "de").map { it.id })
        assertEquals(listOf("b"), searchRecipeSources(sources, "madre pisto").map { it.id })
        assertTrue(searchRecipeSources(sources, "pisto blog").isEmpty())
        assertTrue(searchRecipeSources(sources, "   ").isEmpty())
    }
}
