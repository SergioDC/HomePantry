package com.homepantry.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.homepantry.app.R
import com.homepantry.app.data.SplitPhoto
import com.homepantry.app.data.createReceiptCaptureUri
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.components.SplitAddButton

/**
 * Pestaña Fotos: galería del mes en tres columnas. Las fotos se suben desde la cámara o la
 * galería (mismos lanzadores y permiso que la pantalla de Gastos), con una nota y un gasto
 * opcionales.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SplitPhotosTab(
    state: SplitUiState,
    onUpload: (uri: Uri, note: String, expenseId: String?) -> Unit,
    onDelete: (SplitPhoto) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var chosenUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<SplitPhoto?>(null) }
    val captureLost = stringResource(R.string.purchase_history_capture_lost)
    val permissionDenied = stringResource(R.string.camera_permission_denied)

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) chosenUri = uri
        // La foto se hizo pero se perdió su Uri (actividad recreada): se avisa en vez de fallar en silencio.
        else if (success) onMessage(captureLost)
    }

    fun launchCamera() {
        val uri = createReceiptCaptureUri(context)
        pendingCaptureUri = uri
        takePicture.launch(uri)
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA) { granted ->
        if (granted) launchCamera() else onMessage(permissionDenied)
    }
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) chosenUri = uri
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.uploadingPhoto) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.split_photo_uploading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (state.photos.isEmpty()) {
                Text(
                    stringResource(R.string.split_photos_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = photo.url,
                            contentDescription = photo.note.ifBlank { stringResource(R.string.split_photo_cd) },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewing = photo }
                        )
                    }
                }
            }
        }
        SplitAddButton(
            label = stringResource(R.string.split_photo_add_cd),
            onClick = { showSource = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showSource) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text(stringResource(R.string.split_photo_source_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showSource = false
                    if (cameraPermission.status.isGranted) launchCamera() else cameraPermission.launchPermissionRequest()
                }) { Text(stringResource(R.string.split_photo_camera)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSource = false
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.split_photo_gallery)) }
            }
        )
    }

    chosenUri?.let { uri ->
        NewPhotoSheet(
            uri = uri,
            state = state,
            onUpload = { note, expenseId ->
                onUpload(uri, note, expenseId)
                chosenUri = null
            },
            onDismiss = { chosenUri = null }
        )
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            expenseTitle = photo.expenseId?.let { id -> state.expenses.firstOrNull { it.id == id }?.title },
            onDelete = {
                onDelete(photo)
                viewing = null
            },
            onDismiss = { viewing = null }
        )
    }
}

/** Nota y gasto opcionales antes de subir la foto elegida. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewPhotoSheet(
    uri: Uri,
    state: SplitUiState,
    onUpload: (note: String, expenseId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var expenseId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, sheetGesturesEnabled = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.split_photo_new_title), style = MaterialTheme.typography.titleLarge)
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.split_photo_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.expenses.isNotEmpty()) {
                Text(stringResource(R.string.split_photo_expense), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = expenseId == null,
                        onClick = { expenseId = null },
                        label = { Text(stringResource(R.string.split_photo_no_expense)) }
                    )
                    state.expenses.forEach { expense ->
                        FilterChip(
                            selected = expenseId == expense.id,
                            onClick = { expenseId = expense.id },
                            label = { Text(expense.title) }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.split_cancel)) }
                Button(onClick = { onUpload(note, expenseId) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.split_photo_upload))
                }
            }
        }
    }
}

/** Foto a pantalla completa con su nota, el gasto ligado y el botón de borrar (con confirmación). */
@Composable
private fun PhotoViewer(
    photo: SplitPhoto,
    expenseTitle: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = photo.note.ifBlank { stringResource(R.string.split_photo_cd) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                if (photo.note.isNotBlank()) {
                    Text(photo.note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                }
                if (expenseTitle != null) {
                    Text(
                        stringResource(R.string.split_photo_linked, expenseTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.split_close)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.split_photo_delete_title)) },
            text = { Text(stringResource(R.string.split_photo_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}
