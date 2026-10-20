package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.MonthlySpend
import com.homepantry.app.data.monthStart
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_BAR_HEIGHT = 80f

/**
 * Gasto de cada mes en barras, en orden cronológico. Tocar una barra elige ese mes; la elegida
 * va en el color primario. Con más meses de los que caben se desplaza en horizontal y empieza
 * mostrando los más recientes.
 */
@Composable
fun SpendMonthBars(
    months: List<MonthlySpend>,
    selectedYearMonth: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val max = months.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    val label = remember { SimpleDateFormat("LLL", Locale.forLanguageTag("es-ES")) }
    // Empieza al final: el valor se ajusta al máximo real en cuanto se mide la fila.
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(scroll).height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEach { month ->
            val selected = month.yearMonth == selectedYearMonth
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(month.yearMonth) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (month.total > 0) month.total.roundToInt().toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(24.dp)
                        .height((MAX_BAR_HEIGHT * (month.total / max)).toFloat().coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                        )
                )
                Text(
                    label.format(monthStart(month.yearMonth)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
