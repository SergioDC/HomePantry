package com.homepantry.app.data

/** Nombre que se muestra: el genérico o, en compras antiguas sin normalizar, el impreso. */
fun Purchase.displayName(): String = productName.ifBlank { rawName }

/** Clave de agrupación de una compra a partir de su nombre genérico (o del impreso si no tiene). */
fun productKey(productName: String, rawName: String): String = normalizeProductName(productName.ifBlank { rawName })

/** Cantidad comprada en la unidad con la que se compara el precio ("KG", "L" o "UD"). */
data class Measure(val amount: Double, val unit: String)

/**
 * Lo pesado se mide por su cantidad; una unidad con envase conocido, por lo que pesa en total
 * (2 bandejas de 300 g son 0,6 kg); el resto, por unidades. Así una bandeja y una compra a
 * granel se comparan en €/kg, y nunca se mezcla €/kg con €/ud.
 */
fun Purchase.measure(): Measure {
    val pack = packSize
    val packUnitName = packUnit
    return when {
        unit == Unit.KG.name || unit == Unit.L.name -> Measure(quantity, unit)
        pack != null && pack > 0 && (packUnitName == Unit.KG.name || packUnitName == Unit.L.name) ->
            Measure((if (quantity > 0) quantity else 1.0) * pack, packUnitName)
        else -> Measure(quantity, Unit.UD.name)
    }
}

/** Precio por kg, l o unidad según [measure]; una cantidad no positiva cuenta como 1. */
fun Purchase.comparablePrice(): Double {
    val amount = measure().amount
    return price / (if (amount > 0) amount else 1.0)
}
