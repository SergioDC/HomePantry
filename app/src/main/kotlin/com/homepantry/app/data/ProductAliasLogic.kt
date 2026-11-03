package com.homepantry.app.data

/**
 * Cambio de los campos de producto de una compra. Con [setPack] false el envase no se toca (al
 * unir productos solo cambia el nombre).
 */
data class ProductFieldsUpdate(
    val purchaseId: String,
    val productName: String,
    val normalizedName: String,
    val pack: PackSize?,
    val setPack: Boolean
)

/** Nombre genérico y envase propuestos (o elegidos) para una línea de compra. */
data class ProductProposal(val productName: String, val pack: PackSize?)

private fun ProductAlias.pack(): PackSize? {
    val size = packSize
    val unit = packUnit
    return if (size != null && size > 0 && unit != null) PackSize(size, unit) else null
}

/** Las correcciones del usuario mandan: el nombre del alias siempre, y su envase si lo recordó. */
fun applyAlias(proposal: ProductProposal, alias: ProductAlias?): ProductProposal {
    if (alias == null) return proposal
    return ProductProposal(
        productName = alias.productName.ifBlank { proposal.productName },
        pack = if (alias.packKnown) alias.pack() else proposal.pack
    )
}

/**
 * Alias que hay que guardar cuando el usuario ha pasado de [proposed] a [final] en la línea de
 * [rawName]; null si no cambió nada o si el nombre impreso no da una clave. El envase solo se
 * recuerda cuando el nombre impreso no lleva tamaño (si lo lleva, cada línea tiene el suyo).
 */
fun aliasForCorrection(rawName: String, proposed: ProductProposal, final: ProductProposal): ProductAlias? {
    if (proposed == final) return null
    val key = aliasKey(rawName) ?: return null
    val rememberPack = splitPackSize(rawName).pack == null && proposed.pack != final.pack
    return ProductAlias(
        id = key,
        rawName = rawName.trim(),
        productName = final.productName.trim(),
        packKnown = rememberPack,
        packSize = if (rememberPack) final.pack?.amount else null,
        packUnit = if (rememberPack) final.pack?.unit else null
    )
}

/** Nombre genérico y envase que tiene ahora una compra guardada. */
fun Purchase.productProposal(): ProductProposal {
    val size = packSize
    val unit = packUnit
    return ProductProposal(displayName(), if (size != null && size > 0 && unit != null) PackSize(size, unit) else null)
}

/**
 * Nombre genérico que se guarda al editar una compra. En una compra antigua sin normalizar, dejar
 * el nombre impreso tal cual no la normaliza: si no, saldría de la normalización del histórico
 * con el tamaño metido en el nombre.
 */
fun editedProductName(purchase: Purchase, typedName: String): String {
    val typed = typedName.trim()
    return if (purchase.productName.isBlank() && typed == purchase.rawName.trim()) "" else typed
}

/** Alias de una edición de compra; las compras antiguas sin normalizar no enseñan nada. */
fun aliasForEdit(previous: Purchase, edited: Purchase): ProductAlias? {
    if (previous.productName.isBlank() || edited.productName.isBlank()) return null
    return aliasForCorrection(edited.rawName, previous.productProposal(), edited.productProposal())
}

/**
 * Un alias nuevo que no trae envase conserva el que ya recordaba la casa: corregir solo el
 * nombre no debe borrar el envase. Uno que lo trae (incluido "sin envase") lo sustituye.
 */
fun keepRememberedPack(alias: ProductAlias, existing: ProductAlias?): ProductAlias =
    if (alias.packKnown || existing == null || !existing.packKnown) {
        alias
    } else {
        alias.copy(packKnown = true, packSize = existing.packSize, packUnit = existing.packUnit)
    }
