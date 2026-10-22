package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MemberBalance
import com.homepantry.app.data.Settlement
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.components.MemberAvatar
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.SplitPositiveColor
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.centsText
import com.homepantry.app.ui.components.memberColor
import com.homepantry.app.ui.components.memberColors
import com.homepantry.app.ui.components.splitMemberName
import kotlin.math.abs

/**
 * Pestaña Saldos: cada miembro con su color, lo pagado y su parte, y una barra centrada que
 * crece a la derecha en verde si le deben y a la izquierda en coral si debe (como Tricount).
 * Debajo, los pagos que dejan todo a 0, con un botón para saldar cada uno.
 */
@Composable
fun SplitBalancesTab(
    state: SplitUiState,
    currentUid: String,
    onSettle: (Settlement) -> Unit
) {
    var confirming by remember { mutableStateOf<Settlement?>(null) }
    val colors = remember(state.openMonth?.memberIds) { memberColors(state.openMonth?.memberIds.orEmpty()) }
    val maxNet = state.balances.maxOfOrNull { abs(it.netCents) }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.balances, key = { it.memberId }) { balance ->
            BalanceRow(
                state = state,
                balance = balance,
                color = colors.memberColor(balance.memberId),
                isMe = balance.memberId == currentUid,
                maxNet = maxNet
            )
        }

        item { SpendSectionTitle(stringResource(R.string.split_settlements_title)) }

        if (state.settlements.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.split_all_settled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitPositiveColor
                )
            }
        }
        items(state.settlements, key = { "${it.fromId}>${it.toId}" }) { settlement ->
            val fromName = splitMemberName(state, settlement.fromId)
            val toName = splitMemberName(state, settlement.toId)
            // La franja es del color de quien paga; la tarjeta propia (si debe este móvil) resalta en coral.
            val mine = settlement.fromId == currentUid
            StripedCard(color = if (mine) MaterialTheme.colorScheme.error else colors.memberColor(settlement.fromId), onClick = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(fromName, colors.memberColor(settlement.fromId), size = 30.dp)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    MemberAvatar(toName, colors.memberColor(settlement.toId), size = 30.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            stringResource(R.string.split_settlement_line, fromName, centsText(settlement.amountCents), toName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    FilledTonalButton(onClick = { confirming = settlement }) {
                        Text(stringResource(R.string.split_settle))
                    }
                }
            }
        }
    }

    confirming?.let { settlement ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.split_settle_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.split_settle_message,
                        splitMemberName(state, settlement.fromId),
                        centsText(settlement.amountCents),
                        splitMemberName(state, settlement.toId)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSettle(settlement)
                    confirming = null
                }) { Text(stringResource(R.string.split_settle)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}

@Composable
private fun BalanceRow(state: SplitUiState, balance: MemberBalance, color: Color, isMe: Boolean, maxNet: Long) {
    val name = splitMemberName(state, balance.memberId)
    val net = balance.netCents
    val (label, netColor) = when {
        net > 0 -> stringResource(R.string.split_balance_owed) to SplitPositiveColor
        net < 0 -> stringResource(R.string.split_balance_owes) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.split_balance_even) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(name, color)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (isMe) stringResource(R.string.split_member_you, name) else name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.split_balance_detail, centsText(balance.paidCents), centsText(balance.shareCents)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(centsText(abs(net)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = netColor)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = netColor)
                }
            }
            NetBar(net = net, maxNet = maxNet, color = netColor, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Barra con el eje en el centro: a la derecha lo que le deben, a la izquierda lo que debe. */
@Composable
private fun NetBar(net: Long, maxNet: Long, color: Color, modifier: Modifier = Modifier) {
    val fraction = (abs(net).toFloat() / maxNet).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth().height(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Half(fraction = if (net < 0) fraction else 0f, color = color, alignEnd = true, modifier = Modifier.weight(1f))
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Half(fraction = if (net > 0) fraction else 0f, color = color, alignEnd = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Half(fraction: Float, color: Color, alignEnd: Boolean, modifier: Modifier) {
    Row(modifier.fillMaxHeight(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        } else {
            Spacer(Modifier.width(0.dp))
        }
    }
}
