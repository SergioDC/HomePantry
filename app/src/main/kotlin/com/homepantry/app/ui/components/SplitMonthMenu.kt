package com.homepantry.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.cancelSplitPushNotification
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.shareSplitMonth
import com.homepantry.app.data.splitShareUrl
import com.homepantry.app.ui.SplitViewModel

/**
 * Botón ⋮ de un grupo con Editar, Compartir, Cerrar/Reabrir, Archivar/Desarchivar y Eliminar. Es
 * el mismo en la pantalla del grupo y en las tarjetas de las listas; compartir, cerrar, archivar y
 * las confirmaciones van aquí para no repetirlas en cada pantalla.
 *
 * [onMessage] lo pinta la pantalla con su propio snackbar: al archivar desde una lista, la tarjeta
 * (y con ella este menú) desaparece al momento y no podría mostrar nada.
 */
@Composable
fun SplitMonthMenu(
    month: SplitMonth,
    viewModel: SplitViewModel,
    onEdit: () -> Unit,
    onMessage: (String) -> Unit,
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val shareText = stringResource(R.string.split_share_text, month.name, splitShareUrl(month.id))
    val chooserTitle = stringResource(R.string.split_share_chooser)
    val closedMessage = stringResource(R.string.split_closed_done)
    val reopenedMessage = stringResource(R.string.split_reopened_done)
    val archiveMessage = stringResource(if (month.archived) R.string.split_unarchived_done else R.string.split_archived_done)

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.split_month_menu_cd))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(R.string.split_month_edit, Icons.Filled.Edit) {
                open = false
                onEdit()
            }
            MenuItem(R.string.split_month_share, Icons.Filled.Share) {
                open = false
                shareSplitMonth(context, shareText, chooserTitle)
            }
            MenuItem(
                if (month.closed) R.string.split_month_reopen else R.string.split_month_close_action,
                if (month.closed) Icons.Filled.LockOpen else Icons.Filled.Lock
            ) {
                open = false
                if (month.closed) {
                    viewModel.setClosed(month.id, false)
                    onMessage(reopenedMessage)
                } else {
                    confirmClose = true
                }
            }
            MenuItem(
                if (month.archived) R.string.split_month_unarchive else R.string.split_month_archive,
                if (month.archived) Icons.Filled.Unarchive else Icons.Filled.Archive
            ) {
                open = false
                viewModel.setArchived(month.id, !month.archived)
                onMessage(archiveMessage)
            }
            MenuItem(R.string.split_month_delete, Icons.Filled.Delete, destructive = true) {
                open = false
                confirmDelete = true
            }
        }
    }

    // Cerrar manda un push a los demás participantes, así que se confirma.
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.split_month_close_title, month.name)) },
            text = { Text(stringResource(R.string.split_month_close_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    viewModel.setClosed(month.id, true)
                    // El recordatorio de este mes, si seguía en la bandeja, ya no hace falta.
                    cancelSplitPushNotification(context, month.id)
                    onMessage(closedMessage)
                }) {
                    Text(stringResource(R.string.split_month_close_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.split_month_delete_title)) },
            text = { Text(stringResource(R.string.split_month_delete_message, month.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteMonth(month.id)
                    onDeleted()
                }) {
                    Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}

/** Opción del menú con su icono; las destructivas (borrar) en coral. */
@Composable
private fun MenuItem(label: Int, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(label), color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick
    )
}
