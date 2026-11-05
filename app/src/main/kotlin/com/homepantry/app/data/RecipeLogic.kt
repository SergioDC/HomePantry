package com.homepantry.app.data

import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

/** Id fijo de la entrada Future Life: así dos móviles que la cargan a la vez escriben el mismo documento. */
const val FUTURLIFE_SOURCE_ID = "futurlife21"

/** Ruta en assets del índice de Future Life. */
const val FUTURLIFE_ASSET = "recipes/futurlife.json"

/** Entrada y recetas que se cargan una vez por casa desde un JSON de assets. */
data class RecipeSeed(val source: RecipeSource, val recipes: List<Recipe>)

/** Minúsculas y sin tildes, para comparar como escribe la gente en el buscador. */
fun normalizeRecipeText(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Slug para ids estables: «Pisto al Horno» → «pisto-al-horno». */
fun recipeSlug(text: String): String =
    normalizeRecipeText(text).replace(Regex("[^a-z0-9]+"), "-").trim('-')

/**
 * Lee el JSON de assets (formato de `recipes/futurlife.json`: libros → secciones → nombres).
 * Los ids salen de `<clave del libro>-<slug del nombre>`, estables entre instalaciones, para que
 * recargar no duplique recetas. Si un libro repite nombre, se añade un sufijo numérico.
 */
fun parseRecipeSeed(json: String, sourceId: String): RecipeSeed {
    val root = JsonParser.parseString(json).asJsonObject
    val source = RecipeSource(id = sourceId, name = root["name"].asString, type = RecipeSourceType.BOOK.name)
    val recipes = mutableListOf<Recipe>()
    val used = mutableSetOf<String>()
    root["books"].asJsonArray.forEach { bookElement ->
        val book = bookElement.asJsonObject
        val key = book["key"].asString
        val bookName = book["name"].asString
        book["sections"].asJsonArray.forEach { sectionElement ->
            val section = sectionElement.asJsonObject
            val sectionName = section["name"].asString
            val sub = section["sub"]?.takeUnless { it.isJsonNull }?.asString
            section["recipes"].asJsonArray.forEach { nameElement ->
                val name = nameElement.asString
                val base = "$sourceId-$key-${recipeSlug(name)}"
                var id = base
                var n = 2
                while (!used.add(id)) id = "$base-${n++}"
                recipes += Recipe(
                    id = id,
                    sourceId = sourceId,
                    name = name,
                    book = bookName,
                    section = sectionName,
                    subsection = sub,
                    order = recipes.size.toLong()
                )
            }
        }
    }
    return RecipeSeed(source, recipes)
}

/**
 * Busca en las recetas: cada palabra de la consulta tiene que aparecer (sin tildes ni mayúsculas)
 * en el nombre, la sección, la subsección o el libro. Primero van las que casan solo con el
 * nombre, que suele ser lo que se busca, y dentro de cada grupo por orden alfabético.
 */
fun searchRecipes(recipes: List<Recipe>, query: String): List<Recipe> {
    val words = normalizeRecipeText(query).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return recipes
        .mapNotNull { recipe ->
            val name = normalizeRecipeText(recipe.name)
            val rest = listOfNotNull(recipe.section, recipe.subsection, recipe.book)
                .joinToString(" ") { normalizeRecipeText(it) }
            val all = "$name $rest"
            if (words.all { it in all }) recipe to words.all { it in name } else null
        }
        .sortedWith(compareByDescending<Pair<Recipe, Boolean>> { it.second }.thenBy { normalizeRecipeText(it.first.name) })
        .map { it.first }
}

/** Bloque de la lista de una entrada: recetas de un mismo libro y sección. */
data class RecipeGroup(val book: String?, val section: String?, val recipes: List<Recipe>)

/** Agrupa por libro y sección (con subsección si la hay) respetando [Recipe.order]. */
fun groupRecipes(recipes: List<Recipe>): List<RecipeGroup> {
    val groups = LinkedHashMap<Pair<String?, String?>, MutableList<Recipe>>()
    recipes.sortedBy { it.order }.forEach { recipe ->
        val section = listOfNotNull(recipe.section, recipe.subsection).joinToString(" · ").ifBlank { null }
        groups.getOrPut(recipe.book?.ifBlank { null } to section) { mutableListOf() } += recipe
    }
    return groups.map { (key, list) -> RecipeGroup(key.first, key.second, list) }
}

/**
 * Enlace listo para abrir, o null si no es web. Los enlaces los escribe cualquier miembro de la
 * casa, así que solo se abren http y https: un `intent:` o `file:` podría lanzar otras apps.
 * Sin esquema se supone https («www.receta.com»).
 */
fun safeRecipeUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(trimmed)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
    return when {
        scheme == null -> "https://$trimmed"
        (scheme == "http" || scheme == "https") && trimmed.startsWith("$scheme://", ignoreCase = true) -> trimmed
        else -> null
    }
}

/** Nulo si el texto queda vacío tras recortarlo: los campos opcionales no guardan "". */
fun String.trimToNull(): String? = trim().ifEmpty { null }
