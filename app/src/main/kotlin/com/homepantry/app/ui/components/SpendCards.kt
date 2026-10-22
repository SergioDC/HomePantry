package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homepantry.app.data.Ticket
import com.homepantry.app.data.storesByFirstPurchase
import com.homepantry.app.ui.theme.NocturneOnSurfaceVariant
import com.homepantry.app.ui.theme.StoreColors
import java.util.Locale

/*
 * Piezas comunes de las pantallas de gastos (panel, detalle de ticket, productos y detalle de
 * producto), para que todas usen los mismos colores de tienda, tarjetas y cabeceras.
 */

private val esLocale = Locale.forLanguageTag("es-ES")

/** Importe con separador de miles y dos decimales: "1.284,10 €". */
fun euros(value: Double): String = String.format(esLocale, "%,.2f €", value)

/**
 * Color fijo de cada supermercado (por clave), de [StoreColors] por orden de primera compra; las
 * compras sin supermercado van en gris. Es el mismo mapa en todas las pantallas de gastos.
 */
@Composable
fun rememberStoreColors(tickets: List<Ticket>): Map<String, Color> {
    val noStoreColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(tickets, noStoreColor) {
        var next = 0
        storesByFirstPurchase(tickets).associateWith { key ->
            if (key.isEmpty()) noStoreColor else StoreColors[next++ % StoreColors.size]
        }
    }
}

/**
 * Color de [storeKey] en un mapa de [rememberStoreColors]; gris si no está (no debería pasar:
 * el mapa incluye todas las tiendas con tickets). Se puede llamar fuera de un Composable, p. ej.
 * al montar los items de una lista.
 */
fun Map<String, Color>.colorOf(storeKey: String): Color = this[storeKey] ?: NocturneOnSurfaceVariant

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun SpendSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp)
    )
}

/** Tarjeta de resumen (fondo del contenedor primario) que abre cada pantalla de gastos. */
@Composable
fun SpendHeaderCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Tarjeta neutra con la franja del color de su tienda a la izquierda (tickets, líneas y compras). */
@Composable
fun StripedCard(
    color: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), content = content)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    }
}

/** Cabecera de un mes dentro de una lista agrupada por meses: nombre a la izquierda, resumen a la derecha. */
@Composable
fun MonthHeader(title: String, detail: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        Text(detail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
