package com.homepantry.app.data

/**
 * Un nombre impreso del histórico (con todas sus compras, de cualquier tamaño) y el nombre
 * genérico y envase que se le van a poner. [proposed] es lo que llegó (Gemini, reglas o alias) y
 * [final] lo que queda tras la revisión del usuario.
 */
data class NormalizationRow(
    val key: String,
    val rawName: String,
    val purchases: List<Purchase>,
    val proposed: ProductProposal,
    val final: ProductProposal,
    val selected: Boolean = true,
    val fromAlias: Boolean
)

/** Grupo de un nombre impreso: el de su alias o, si el nombre es solo un tamaño, el nombre entero. */
private fun groupKey(rawName: String): String = aliasKey(rawName) ?: normalizeProductName(rawName)

/** Compras aún sin nombre genérico, por grupo; cada grupo con su nombre impreso más reciente. */
private fun pendingGroups(purchases: List<Purchase>): Map<String, List<Purchase>> =
    purchases.filter { it.productName.isBlank() && it.rawName.isNotBlank() }
        .groupBy { groupKey(it.rawName) }
        .mapValues { (_, group) -> group.sortedByDescending { it.date } }

private fun rulesProposal(rawName: String): ProductProposal =
    splitPackSize(rawName).let { ProductProposal(it.name.ifBlank { rawName.trim() }, it.pack) }

/**
 * Lo que falta por normalizar: las filas que ya resuelve un alias (no van a Gemini) y los
 * nombres impresos que hay que pedirle, uno por grupo.
 */
fun pendingRawNames(
    purchases: List<Purchase>,
    aliases: Map<String, ProductAlias>
): Pair<List<NormalizationRow>, List<String>> {
    val rows = mutableListOf<NormalizationRow>()
    val names = mutableListOf<String>()
    pendingGroups(purchases).forEach { (key, group) ->
        val rawName = group.first().rawName
        val alias = aliasKey(rawName)?.let { aliases[it] }
        if (alias != null) {
            val proposal = applyAlias(rulesProposal(rawName), alias)
            rows += NormalizationRow(key, rawName, group, proposal, proposal, fromAlias = true)
        } else {
            names += rawName
        }
    }
    return rows to names
}

/**
 * Filas de los [rawNames] pedidos a Gemini con sus [proposals] (por nombre impreso). Un nombre
 * sin propuesta se queda con las reglas; las propuestas de nombres que no se pidieron se ignoran.
 */
fun rowsFromProposals(
    purchases: List<Purchase>,
    rawNames: List<String>,
    proposals: Map<String, ProductProposal>
): List<NormalizationRow> {
    val groups = pendingGroups(purchases)
    return rawNames.mapNotNull { rawName ->
        val key = groupKey(rawName)
        val group = groups[key] ?: return@mapNotNull null
        val proposal = proposals[rawName]?.takeIf { it.productName.isNotBlank() } ?: rulesProposal(rawName)
        NormalizationRow(key, rawName, group, proposal, proposal, fromAlias = false)
    }
}

/** Orden de la revisión: por nombre genérico, para que se vea qué se va a juntar. */
fun sortedForReview(rows: List<NormalizationRow>): List<NormalizationRow> =
    rows.sortedWith(compareBy({ normalizeProductName(it.final.productName) }, { it.rawName }))

/**
 * Lo que hay que escribir para las filas marcadas. El envase de cada compra sale de su propio
 * nombre impreso cuando lleva tamaño (300 g y 150 g son el mismo producto con envases distintos);
 * si no, del de la fila. Lo pesado no lleva envase. Cada fila aplicada deja un alias, salvo las
 * que ya venían de uno y no se han tocado.
 */
fun applyPlan(rows: List<NormalizationRow>): Pair<List<ProductFieldsUpdate>, List<ProductAlias>> {
    val selected = rows.filter { it.selected && it.final.productName.isNotBlank() }
    val updates = selected.flatMap { row ->
        val name = row.final.productName.trim()
        row.purchases.map { purchase ->
            val pack = if (purchase.unit == Unit.UD.name) splitPackSize(purchase.rawName).pack ?: row.final.pack else null
            ProductFieldsUpdate(
                purchaseId = purchase.id,
                productName = name,
                normalizedName = productKey(name, purchase.rawName),
                pack = pack,
                setPack = true
            )
        }
    }
    val aliases = selected
        .filter { !it.fromAlias || it.final != it.proposed }
        .mapNotNull { aliasForCorrection(it.rawName, ProductProposal("", null), it.final) }
    return updates to aliases
}
