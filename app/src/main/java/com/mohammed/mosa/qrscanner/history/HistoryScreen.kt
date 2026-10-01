package com.mohammed.mosa.qrscanner.history

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.data.CsvExporter
import com.mohammed.mosa.qrscanner.data.ScanEntity
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.ui.theme.Brand
import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.ShareUtils
import kotlinx.coroutines.launch

private enum class HistoryFilter(val label: String) {
    ALL("All"), SCANNED("Scanned"), GENERATED("Generated"),
    FAVORITES("Favorites"), DUPLICATES("Duplicates"),
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

    val counts = remember(items) {
        mapOf(
            HistoryFilter.SCANNED to items.count { !it.isGenerated },
            HistoryFilter.GENERATED to items.count { it.isGenerated },
            HistoryFilter.FAVORITES to items.count { it.isFavorite },
            HistoryFilter.DUPLICATES to items.groupBy { it.value }
                .filterValues { it.size > 1 }.values.sumOf { it.size },
        )
    }

    val filtered = remember(items, query, filter) {
        val base = when (filter) {
            HistoryFilter.ALL -> items
            HistoryFilter.SCANNED -> items.filter { !it.isGenerated }
            HistoryFilter.GENERATED -> items.filter { it.isGenerated }
            HistoryFilter.FAVORITES -> items.filter { it.isFavorite }
            HistoryFilter.DUPLICATES -> items.groupBy { it.value }
                .filterValues { it.size > 1 }
                .values.flatten()
                .sortedByDescending { it.createdAt }
        }
        if (query.isBlank()) base else base.filter { it.value.contains(query, ignoreCase = true) }
    }

    val emptyTitle = if (query.isNotBlank()) "No matches" else when (filter) {
        HistoryFilter.FAVORITES -> "No favorites yet"
        HistoryFilter.DUPLICATES -> "No duplicates found"
        HistoryFilter.GENERATED -> "Nothing generated yet"
        else -> "No scans yet"
    }

    Box(modifier.fillMaxSize().background(PC.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("History", style = MaterialTheme.typography.titleLarge, color = PC.text)
                    Text(
                        "${items.size} ${if (items.size == 1) "record" else "records"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val file = CsvExporter.export(context, items)
                            snackbar.showSnackbar("Exported ${items.size} rows to CSV")
                            ShareUtils.shareCsv(context, file)
                        }
                    },
                    enabled = items.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "Export CSV", tint = Brand)
                }
                IconButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.DeleteSweep, contentDescription = "Clear all",
                        tint = PC.text2,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search scans…", color = PC.text4) },
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
                        label = { Text(if ((counts[f] ?: 0) > 0) "${f.label} · ${counts[f]}" else f.label) },
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
                        "Every code you scan or create is saved here automatically.",
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
                        HistoryRow(
                            scan = scan,
                            onOpen = { ShareUtils.openLink(context, scan.value) },
                            onCopy = {
                                clipboard.setText(AnnotatedString(scan.value))
                                scope.launch { snackbar.showSnackbar("Copied to clipboard") }
                            },
                            onShareText = { ShareUtils.shareText(context, scan.value) },
                            onShareImage = {
                                scope.launch { ShareUtils.shareAsImage(context, scan.value) }
                            },
                            onFavorite = {
                                scope.launch { repository.setFavorite(scan.id, !scan.isFavorite) }
                            },
                            onDelete = { scope.launch { repository.delete(scan.id) } },
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
            title = { Text("Clear history?", color = PC.text) },
            text = { Text("All saved scans will be deleted permanently.", color = PC.text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        repository.clearAll()
                        snackbar.showSnackbar("History cleared")
                    }
                }) { Text("Clear", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = PC.text) }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    scan: ScanEntity,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(PC.card)
            .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radius2xl))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(Brand.copy(alpha = 0.15f))
                    .border(1.dp, Brand.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(typeIcon(scan.type), null, tint = Brand, modifier = Modifier.size(17.dp))
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
                    buildString {
                        if (scan.isGenerated) append("Created • ")
                        append("${scan.type} • ${scan.format} • ${DateUtils.getRelativeTimeSpanString(scan.createdAt)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = PC.text3,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Delete, contentDescription = "Delete",
                    tint = PC.text3, modifier = Modifier.size(17.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (scan.isLink) MiniAction(Icons.Rounded.OpenInNew, "Open", onOpen)
            MiniAction(Icons.Rounded.ContentCopy, "Copy", onCopy)
            MiniAction(Icons.Rounded.Share, "Text", onShareText)
            MiniAction(Icons.Rounded.Image, "Image", onShareImage)
            MiniAction(
                if (scan.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                "Fav", onFavorite, highlight = scan.isFavorite,
            )
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