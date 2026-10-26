package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.ExpenseCategory
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.categoryTotals
import com.homepantry.app.data.expensesByDay
import com.homepantry.app.data.myShareCents
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.SplitIcon
import com.homepantry.app.ui.components.MonthHeader
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SplitAddButton
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.centsText
import com.homepantry.app.ui.components.color
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.label
import com.homepantry.app.ui.components.splitMemberName
import java.text.SimpleDateFormat
import java.util.Locale

/** Tipos que se nombran en la leyenda de la barra de reparto; el resto solo aparece en la barra. */
private const val LEGEND_SIZE = 4

/**
 * Pestaña Gastos, con las mismas piezas que la pantalla de Gastos: tarjeta de resumen («Mis
 * gastos», «Total gastos» y barra de reparto por tipo), cabecera por día con su total y cada
 * gasto en una tarjeta con la franja y el icono del color de su tipo.
 */
@Composable
fun SplitExpensesTab(
    state: SplitUiState,
    currentUid: String,
    onAdd: () -> Unit,
    onOpen: (SplitExpense) -> Unit
) {
    val dayFormat = remember { SimpleDateFormat("EEEE d MMM", Locale.forLanguageTag("es-ES")) }
    val days = remember(state.expenses) { expensesByDay(state.expenses) }
    val byCategory = remember(state.expenses) { categoryTotals(state.expenses) }
    val myShare = remember(state.expenses, currentUid) { myShareCents(state.expenses, currentUid) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SpendHeaderCard {
                    Row(Modifier.fillMaxWidth()) {
                        SummaryFigure(stringResource(R.string.split_my_expenses), centsText(myShare), Modifier.weight(1f))
                        SummaryFigure(stringResource(R.string.split_total_expenses), centsText(state.monthTotalCents), Modifier.weight(1f))
                    }
                    if (byCategory.isNotEmpty()) CategoryBar(byCategory)
                }
            }
            if (state.expenses.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.split_expenses_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    )
                }
            }
            days.forEach { (dayKey, expenses) ->
                item(key = "day-$dayKey") {
                    val title = expenses.first().date?.let { dayFormat.format(it) } ?: stringResource(R.string.split_no_date)
                    val dayTotal = expenses.filter { !it.reimbursement }.sumOf { it.amountCents }
                    MonthHeader(title = title.uppercase(Locale.forLanguageTag("es-ES")), detail = centsText(dayTotal))
                }
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(state, currentUid, expense, onClick = { onOpen(expense) })
                }
            }
        }
        SplitAddButton(
            label = stringResource(R.string.split_expense_add_cd),
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

/** Barra apilada con lo gastado en cada tipo y una leyenda con los tipos principales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBar(totals: List<Pair<ExpenseCategory, Long>>) {
    val sum = totals.sumOf { it.second }.coerceAtLeast(1L)
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            totals.forEach { (category, cents) ->
                Box(Modifier.weight(cents.toFloat() / sum).fillMaxHeight().background(category.color()))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            totals.take(LEGEND_SIZE).forEach { (category, cents) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(category.color())
                    Text(
                        "${category.label()} · ${(cents * 100 / sum)} %",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(state: SplitUiState, currentUid: String, expense: SplitExpense, onClick: () -> Unit) {
    val color = expense.color()
    val payerName = splitMemberName(state, expense.payerId)
    val payer = if (expense.payerId == currentUid) stringResource(R.string.split_member_you, payerName) else payerName
    StripedCard(color = color, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SplitIcon(expense.emoji.takeUnless { expense.reimbursement }.orEmpty(), expense.icon(), color)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (expense.reimbursement) {
                        val receiver = expense.participants.firstOrNull()?.let { splitMemberName(state, it) }.orEmpty()
                        stringResource(R.string.split_reimbursement_detail, payerName, receiver)
                    } else {
                        stringResource(R.string.split_paid_by, payer)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                centsText(expense.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (expense.reimbursement) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
