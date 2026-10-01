package com.mohammed.mosa.qrscanner.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.data.AppSettings
import com.mohammed.mosa.qrscanner.data.CsvExporter
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.data.SettingsRepository
import com.mohammed.mosa.qrscanner.ui.theme.Brand
import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.ThemeMode
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.ShareUtils
import kotlinx.coroutines.launch

private val AccentOptions = listOf(
    "#7C5CFC" to "Violet",
    "#4C8DFF" to "Blue",
    "#2EC4B6" to "Teal",
    "#34C77B" to "Green",
    "#FF9F43" to "Orange",
    "#FF5C8A" to "PPC.bg",
)
private val Destructive = Color(0xFFFF6B6B)

@Composable
fun SettingsScreen(
    repository: ScanRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    val settings by settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val scans by repository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val weekCount = remember(scans) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        scans.count { it.createdAt >= weekAgo }
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Box(modifier.fillMaxSize().background(PC.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(16.dp))

            /* ---- stats ---- */
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stat("Total scans", scans.size.toString(), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.08f)))
                    Stat("This week", weekCount.toString(), Modifier.weight(1f))
                }
            }
            SectionHeader("Scanning")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.SaveAlt,
                    title = "Auto-save scans",
                    subtitle = "Every scan is stored in history",
                    checked = settings.autoSave,
                ) { scope.launch { settingsRepository.setAutoSave(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.AllInclusive,
                    title = "Continuous scan",
                    subtitle = "Keep scanning while a result is shown",
                    checked = settings.continuousScan,
                ) { scope.launch { settingsRepository.setContinuousScan(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Auto-copy to clipboard",
                    subtitle = "Copy the value as soon as it's detected",
                    checked = settings.autoCopy,
                ) { scope.launch { settingsRepository.setAutoCopy(it) } }

                Divider()
                ToggleRow(
                    icon = Icons.Rounded.OpenInNew,
                    title = "Auto-open Urls",
                    subtitle = "Open the browser when a URL is scanned",
                    checked = settings.autoOpenUrl,
                ) { scope.launch { settingsRepository.setAutoOpenUrl(it) } }
            }

            SectionHeader("Feedback")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = "Beep on scan",
                    subtitle = "Play a confirmation tone",
                    checked = settings.sound,
                ) { scope.launch { settingsRepository.setSound(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Vibrate on scan",
                    subtitle = "Short haptic pulse on detection",
                    checked = settings.vibrate,
                ) { scope.launch { settingsRepository.setVibrate(it) } }
            }

            SectionHeader("Camera")
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.CameraFront,
                    title = "Use front camera",
                    subtitle = "Switch between rear and front lens",
                    checked = settings.useFrontCamera,
                ) { scope.launch { settingsRepository.setUseFrontCamera(it) } }
            }

            SectionHeader("Appearance")
            SettingsCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Palette)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Accent color", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                "Applies across the whole app instantly",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentOptions.forEach { (hex, name) ->
                            val color = remember(hex) {
                                runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(Brand)
                            }
                            val selected = settings.accent == hex
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.15f),
                                        CircleShape,
                                    )
                                    .clickable { scope.launch { settingsRepository.setAccent(hex) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, name, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Divider()
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Brightness6)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Theme", style = MaterialTheme.typography.bodyMedium, color = PC.text)
                            Text("App-wide appearance", style = MaterialTheme.typography.labelSmall, color = PC.text3)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { settingsRepository.setTheme(mode) } },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = PC.card,
                                    labelColor = PC.text2,
                                    selectedContainerColor = Brand.copy(alpha = 0.2f),
                                    selectedLabelColor = Brand,
                                ),
                            )
                        }
                    }
                }
            }

            SectionHeader("Data")
            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "Export history to CSV",
                    subtitle = "${scans.size} ${if (scans.size == 1) "row" else "rows"} • shared as a file",
                    enabled = scans.isNotEmpty(),
                ) {
                    scope.launch {
                        val file = CsvExporter.export(context, scans)
                        snackbar.showSnackbar("Exported ${scans.size} rows to CSV")
                        ShareUtils.shareCsv(context, file)
                    }
                }
                Divider()
                ActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear history",
                    subtitle = "Delete all saved scans permanently",
                    tint = Destructive,
                    enabled = scans.isNotEmpty(),
                ) { confirmClear = true }
            }

            SectionHeader("About")
            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.Info,
                    title = "Version",
                    subtitle = versionName,
                    enabled = false,
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy",
                    subtitle = "All scanning happens on-device. Frames never leave your phone.",
                    enabled = false,
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.Person,
                    title = "Developer",
                    subtitle = "Mohammed Mosa",
                    enabled = false,
                )

            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF1B1B22),
            title = { Text("Clear history?", color = Color.White) },
            text = { Text("All saved scans will be deleted permanently.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        repository.clearAll()
                        snackbar.showSnackbar("History cleared")
                    }
                }) { Text("Clear", color = Destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Color.White) }
            },
        )
    }
}

/* ---------------- building blocks ---------------- */

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(Color(0xFF14141A))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Ui.radius2xl)),
        content = content,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = Brand) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brand.copy(alpha = 0.14f))
            .border(1.dp, Brand.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Brand,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = PC.text2,
                uncheckedTrackColor = Color(0xFF2A2A33),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint = if (tint == Color.White) Brand else tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tint)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Brand)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}