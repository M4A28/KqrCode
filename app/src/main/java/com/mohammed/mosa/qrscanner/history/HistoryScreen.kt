package com.mohammed.mosa.qrscanner.history

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.R
import com.mohammed.mosa.qrscanner.data.CsvExporter
import com.mohammed.mosa.qrscanner.data.ScanEntity
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.generate.BarcodeRenderer
import com.mohammed.mosa.qrscanner.generate.GenFormat
import com.mohammed.mosa.qrscanner.ui.theme.Brand
import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.Base64Utils
import com.mohammed.mosa.qrscanner.util.ScanTypes
import com.mohammed.mosa.qrscanner.util.SmartActions
import com.mohammed.mosa.qrscanner.util.ShareUtils
import com.mohammed.mosa.qrscanner.util.typeLabelRes
import kotlinx.coroutines.launch

private val Danger = Color(0xFFFF6B6B)

private enum class HistoryFilter(val labelRes: Int) {
    ALL(R.string.hist_filter_all),
    SCANNED(R.string.hist_filter_scanned),
    GENERATED(R.string.hist_filter_generated),
    FAVORITES(R.string.hist_filter_favorites),
    DUPLICATES(R.string.hist_filter_duplicates),
}

@Composable
fun HistoryScreen(repository: ScanRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    val items by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    var detailScan by remember { mutableStateOf<ScanEntity?>(null) }

    // ---- multi-select + undo ----
    var selecting by remember { mutableStateOf(false) }
    val selectedIds: SnapshotStateList<Long> = remember { mutableStateListOf() }
    var lastDeleted by remember { mutableStateOf<List<ScanEntity>>(emptyList()) }

    fun exitSelection() {
        selecting = false
        selectedIds.clear()
    }

    /** Deletes now, offers Undo in the snackbar, re-inserts on undo. */
    fun deleteWithUndo(targets: List<ScanEntity>) {
        if (targets.isEmpty()) return
        scope.launch {
            lastDeleted = targets
            repository.deleteAll(targets.map { it.id })
            exitSelection()
            val result = snackbar.showSnackbar(
                message = context.resources.getQuantityString(
                    R.plurals.common_deleted_scans, targets.size, targets.size,
                ),
                actionLabel = context.getString(R.string.common_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.restoreAll(lastDeleted)
            }
        }
    }

    // Grouped once and shared by the Duplicates filter and its count badge.
    val duplicateGroups = remember(items) {
        items.groupBy { it.value }.filterValues { it.size > 1 }
    }

    val counts = remember(items, duplicateGroups) {
        mapOf(
            HistoryFilter.SCANNED to items.count { !it.isGenerated },
            HistoryFilter.GENERATED to items.count { it.isGenerated },
            HistoryFilter.FAVORITES to items.count { it.isFavorite },
            HistoryFilter.DUPLICATES to duplicateGroups.values.sumOf { it.size },
        )
    }

    val filtered = remember(items, query, filter, duplicateGroups) {
        val base = when (filter) {
            HistoryFilter.ALL -> items
            HistoryFilter.SCANNED -> items.filter { !it.isGenerated }
            HistoryFilter.GENERATED -> items.filter { it.isGenerated }
            HistoryFilter.FAVORITES -> items.filter { it.isFavorite }
            HistoryFilter.DUPLICATES -> duplicateGroups.values
                .flatten()
                .sortedByDescending { it.createdAt }
        }
        if (query.isBlank()) base else base.filter { it.value.contains(query, ignoreCase = true) }
    }

    val emptyTitle = if (query.isNotBlank()) stringResource(R.string.hist_empty_query) else when (filter) {
        HistoryFilter.FAVORITES -> stringResource(R.string.hist_empty_favorites)
        HistoryFilter.DUPLICATES -> stringResource(R.string.hist_empty_duplicates)
        HistoryFilter.GENERATED -> stringResource(R.string.hist_empty_generated)
        else -> stringResource(R.string.hist_empty_scans)
    }

    Box(modifier.fillMaxSize().background(PC.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selecting)
                            context.resources.getQuantityString(
                                R.plurals.hist_selected, selectedIds.size, selectedIds.size,
                            )
                        else stringResource(R.string.hist_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = PC.text,
                    )
                    Text(
                        if (selecting) stringResource(R.string.hist_select_hint)
                        else context.resources.getQuantityString(
                            R.plurals.hist_records, items.size, items.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                if (selecting) {
                    IconButton(
                        onClick = { deleteWithUndo(items.filter { it.id in selectedIds }) },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_delete_selected),
                            tint = Danger,
                        )
                    }
                    IconButton(onClick = { exitSelection() }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_cancel_selection),
                            tint = PC.text2,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val file = CsvExporter.export(context, items)
                                snackbar.showSnackbar(
                                    context.resources.getQuantityString(
                                        R.plurals.common_exported_rows, items.size, items.size,
                                    )
                                )
                                ShareUtils.shareCsv(context, file)
                            }
                        },
                        enabled = items.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.common_export_csv),
                            tint = Brand,
                        )
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.common_clear_all),
                            tint = PC.text2,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.hist_search), color = PC.text4) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PC.text3) },
                singleLine = true,
                shape = RoundedCornerShape(Ui.radiusXl),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = PC.border,
                    cursorColor = Brand,
                    focusedContainerColor = PC.card,
                    unfocusedContainerColor = PC.card,
                    focusedTextColor = PC.text,
                    unfocusedTextColor = PC.text,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            // ---- filter chips ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = {
                            Text(
                                stringResource(f.labelRes) +
                                        if ((counts[f] ?: 0) > 0) " · ${counts[f]}" else ""
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        border = BorderStroke(1.dp, if (filter == f) Brand.copy(alpha = 0.5f) else PC.borderSoft),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = PC.card,
                            labelColor = PC.text2,
                            selectedContainerColor = Brand.copy(alpha = 0.2f),
                            selectedLabelColor = Brand,
                        ),
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.History, null, tint = PC.text4, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(emptyTitle, style = MaterialTheme.typography.titleSmall, color = PC.text2)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.hist_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PC.text3,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filtered, key = { it.id }) { scan ->
                        val isSelected = scan.id in selectedIds
                        HistoryRow(
                            scan = scan,
                            selecting = selecting,
                            selected = isSelected,
                            onClick = {
                                if (selecting) {
                                    if (isSelected) selectedIds.remove(scan.id) else selectedIds.add(scan.id)
                                } else {
                                    detailScan = scan
                                }
                            },
                            onLongClick = {
                                if (!selecting) selecting = true
                                if (scan.id !in selectedIds) selectedIds.add(scan.id)
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(scan.value))
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.common_copied_to_clipboard)
                                    )
                                }
                            },
                            onShareText = { ShareUtils.shareText(context, scan.value) },
                            onShareImage = {
                                scope.launch { ShareUtils.shareAsImage(context, scan.value) }
                            },
                            onFavorite = {
                                scope.launch { repository.setFavorite(scan.id, !scan.isFavorite) }
                            },
                            onDelete = { deleteWithUndo(listOf(scan)) },
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = PC.dialog,
            title = { Text(stringResource(R.string.common_clear_history_title), color = PC.text) },
            text = { Text(stringResource(R.string.common_clear_history_text), color = PC.text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        repository.clearAll()
                        snackbar.showSnackbar(context.getString(R.string.common_history_cleared))
                    }
                }) { Text(stringResource(R.string.common_clear), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel), color = PC.text)
                }
            },
        )
    }

    detailScan?.let { captured ->
        // Read the live row so favorite changes reflect immediately in the sheet.
        val scan = items.firstOrNull { it.id == captured.id } ?: captured
        DetailSheet(
            scan = scan,
            onDismiss = { detailScan = null },
            onCopy = {
                clipboard.setText(AnnotatedString(scan.value))
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.common_copied_to_clipboard))
                }
            },
            onShareText = { ShareUtils.shareText(context, scan.value) },
            onShareImage = { scope.launch { ShareUtils.shareAsImage(context, scan.value) } },
            onSavePng = {
                scope.launch {
                    val msg = runCatching {
                        val bmp = BarcodeRenderer.render(scan.value, GenFormat.QR)
                        ShareUtils.saveToGallery(
                            context, bmp, "scan_${scan.id}_${System.currentTimeMillis()}",
                        )
                    }.getOrDefault(context.getString(R.string.common_save_failed))
                    snackbar.showSnackbar(msg)
                }
            },
            onFavorite = {
                scope.launch { repository.setFavorite(scan.id, !scan.isFavorite) }
            },
            onDelete = {
                detailScan = null
                deleteWithUndo(listOf(scan))
            },
        )
    }
}

/* ---------------- detail bottom sheet ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    scan: ScanEntity,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onSavePng: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val isLink = remember(scan.value) { ScanTypes.isLink(scan.value) }
    val smart = remember(scan.value) { SmartActions.primary(scan.value) }
    val base64Decoded = remember(scan.value) { Base64Utils.decodeOrNull(scan.value) }
    val relative = remember(scan.createdAt) {
        DateUtils.getRelativeTimeSpanString(scan.createdAt).toString()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PC.sheet,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PC.text.copy(alpha = 0.3f))
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brand.copy(alpha = 0.15f))
                        .border(1.dp, Brand.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(typeIcon(scan.type), null, tint = Brand, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.hist_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = PC.text,
                    )
                    Text(
                        stringResource(
                            R.string.hist_detail_meta,
                            stringResource(typeLabelRes(scan.type)),
                            scan.format,
                        ) + " • $relative",
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (scan.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(R.string.hist_fav),
                        tint = if (scan.isFavorite) Brand else PC.text2,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = Danger)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- value ----
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Ui.radiusXl))
                    .background(PC.well)
                    .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radiusXl))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .heightIn(max = 180.dp)
            ) {
                SelectionContainer {
                    Text(
                        scan.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PC.text,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }

            // ---- Base64 section ----
            if (base64Decoded != null) {
                var showDecoded by remember(scan.value) { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Code, null, tint = Brand, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.scan_base64_base),
                        style = MaterialTheme.typography.labelSmall,
                        color = PC.text2,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { showDecoded = !showDecoded },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Brand),
                    ) {
                        Text(
                            if (showDecoded) stringResource(R.string.scan_base64_hide)
                            else stringResource(R.string.scan_base64_decode)
                        )
                    }
                }
                AnimatedVisibility(visible = showDecoded) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Ui.radiusXl))
                            .background(PC.well)
                            .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radiusXl))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.scan_base64_decoded),
                                style = MaterialTheme.typography.labelSmall,
                                color = PC.text3,
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    base64Decoded,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = PC.text,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- smart action ----
            when {
                smart != null && smart.labelRes != R.string.action_open -> {
                    Button(
                        onClick = { smart.execute(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Ui.radiusXl),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(smart.icon, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(smart.labelRes))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                isLink -> {
                    Button(
                        onClick = { ShareUtils.openLink(context, scan.value) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Ui.radiusXl),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_open_link))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ---- actions ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_copy))
                }
                Button(
                    onClick = onShareText,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_share_text))
                }
                Button(
                    onClick = onShareImage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_share_image))
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onSavePng,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Ui.radiusXl),
                colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                border = BorderStroke(1.dp, PC.border),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hist_save_png))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    scan: ScanEntity,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val smart = remember(scan.value) { SmartActions.primary(scan.value) }
    val meta = stringResource(
        R.string.hist_row_meta,
        stringResource(typeLabelRes(scan.type)),
        scan.format,
        DateUtils.getRelativeTimeSpanString(scan.createdAt).toString(),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(if (selected) Brand.copy(alpha = 0.12f) else PC.card)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Brand else PC.borderSoft,
                RoundedCornerShape(Ui.radius2xl),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (selected) Brand else Color.Transparent)
                        .border(1.5.dp, if (selected) Brand else PC.borderSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Brand.copy(alpha = 0.15f))
                        .border(1.dp, Brand.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(typeIcon(scan.type), null, tint = Brand, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scan.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PC.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (scan.isGenerated) stringResource(R.string.hist_created_prefix, meta) else meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = PC.text3,
                )
            }
            if (!selecting) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = PC.text3, modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        if (!selecting) {
            Spacer(Modifier.height(10.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                smart?.let { action ->
                    MiniAction(
                        action.icon,
                        stringResource(action.labelRes),
                        onClick = { action.execute(context) },
                    )
                }
                MiniAction(
                    Icons.Rounded.ContentCopy,
                    stringResource(R.string.common_copy),
                    onClick = onCopy,
                )
                MiniAction(
                    Icons.Rounded.Share,
                    stringResource(R.string.common_share_text),
                    onClick = onShareText,
                )
                MiniAction(
                    Icons.Rounded.Image,
                    stringResource(R.string.common_share_image),
                    onClick = onShareImage,
                )
                MiniAction(
                    if (scan.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(R.string.hist_fav),
                    onClick = onFavorite,
                    highlight = scan.isFavorite,
                )
            }
        }
    }
}

@Composable
private fun MiniAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val tint = if (highlight) Brand else PC.text2
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

private fun typeIcon(type: String): ImageVector = when (type) {
    "Link" -> Icons.Rounded.Link
    "Wi-Fi" -> Icons.Rounded.Wifi
    "Contact" -> Icons.Rounded.Person
    "Phone" -> Icons.Rounded.Call
    "SMS" -> Icons.Rounded.Sms
    "Email" -> Icons.Rounded.Email
    "Location" -> Icons.Rounded.Place
    else -> Icons.Rounded.Notes
}
