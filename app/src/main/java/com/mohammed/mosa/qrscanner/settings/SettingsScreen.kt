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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.R
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
    "#7C5CFC" to R.string.accent_violet,
    "#4C8DFF" to R.string.accent_blue,
    "#2EC4B6" to R.string.accent_teal,
    "#34C77B" to R.string.accent_green,
    "#FF9F43" to R.string.accent_orange,
    "#FF5C8A" to R.string.accent_pink,
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
            Text(
                stringResource(R.string.set_title),
                style = MaterialTheme.typography.titleLarge,
                color = PC.text,
            )
            Spacer(Modifier.height(16.dp))

            /* ---- stats ---- */
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stat(stringResource(R.string.set_stat_total), scans.size.toString(), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(PC.borderSoft))
                    Stat(stringResource(R.string.set_stat_week), weekCount.toString(), Modifier.weight(1f))
                }
            }
            SectionHeader(stringResource(R.string.set_section_scanning))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.SaveAlt,
                    title = stringResource(R.string.set_autosave_title),
                    subtitle = stringResource(R.string.set_autosave_subtitle),
                    checked = settings.autoSave,
                ) { scope.launch { settingsRepository.setAutoSave(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.AllInclusive,
                    title = stringResource(R.string.set_continuous_title),
                    subtitle = stringResource(R.string.set_continuous_subtitle),
                    checked = settings.continuousScan,
                ) { scope.launch { settingsRepository.setContinuousScan(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = stringResource(R.string.set_autocopy_title),
                    subtitle = stringResource(R.string.set_autocopy_subtitle),
                    checked = settings.autoCopy,
                ) { scope.launch { settingsRepository.setAutoCopy(it) } }

                Divider()
                ToggleRow(
                    icon = Icons.Rounded.OpenInNew,
                    title = stringResource(R.string.set_autoopen_title),
                    subtitle = stringResource(R.string.set_autoopen_subtitle),
                    checked = settings.autoOpenUrl,
                ) { scope.launch { settingsRepository.setAutoOpenUrl(it) } }
            }

            SectionHeader(stringResource(R.string.set_section_feedback))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = stringResource(R.string.set_beep_title),
                    subtitle = stringResource(R.string.set_beep_subtitle),
                    checked = settings.sound,
                ) { scope.launch { settingsRepository.setSound(it) } }
                Divider()
                ToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = stringResource(R.string.set_vibrate_title),
                    subtitle = stringResource(R.string.set_vibrate_subtitle),
                    checked = settings.vibrate,
                ) { scope.launch { settingsRepository.setVibrate(it) } }
            }

            SectionHeader(stringResource(R.string.set_section_camera))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.CameraFront,
                    title = stringResource(R.string.set_frontcam_title),
                    subtitle = stringResource(R.string.set_frontcam_subtitle),
                    checked = settings.useFrontCamera,
                ) { scope.launch { settingsRepository.setUseFrontCamera(it) } }
            }

            SectionHeader(stringResource(R.string.set_section_appearance))
            SettingsCard {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Palette)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.set_accent_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PC.text,
                            )
                            Text(
                                stringResource(R.string.set_accent_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = PC.text3,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentOptions.forEach { (hex, nameRes) ->
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
                                        if (selected) PC.text else PC.borderSoft,
                                        CircleShape,
                                    )
                                    .clickable { scope.launch { settingsRepository.setAccent(hex) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        stringResource(nameRes),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
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
                            Text(
                                stringResource(R.string.set_theme_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PC.text,
                            )
                            Text(
                                stringResource(R.string.set_theme_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = PC.text3,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { settingsRepository.setTheme(mode) } },
                                label = { Text(themeLabel(mode)) },
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

            SectionHeader(stringResource(R.string.set_section_data))
            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.set_export_title),
                    subtitle = context.resources.getQuantityString(
                        R.plurals.set_export_rows, scans.size, scans.size,
                    ),
                    enabled = scans.isNotEmpty(),
                ) {
                    scope.launch {
                        val file = CsvExporter.export(context, scans)
                        snackbar.showSnackbar(
                            context.resources.getQuantityString(
                                R.plurals.common_exported_rows, scans.size, scans.size,
                            )
                        )
                        ShareUtils.shareCsv(context, file)
                    }
                }
                Divider()
                ActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.set_clear_history),
                    subtitle = stringResource(R.string.set_clear_history_subtitle),
                    tint = Destructive,
                    enabled = scans.isNotEmpty(),
                ) { confirmClear = true }
            }

            SectionHeader(stringResource(R.string.set_section_about))
            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.set_version),
                    subtitle = versionName,
                    enabled = false,
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.set_privacy),
                    subtitle = stringResource(R.string.set_privacy_text),
                    enabled = false,
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.set_developer),
                    subtitle = stringResource(R.string.developer_name),
                    enabled = false,
                )
                Divider()
                val email = stringResource(R.string.developer_email)
                ActionRow(
                    icon = Icons.Rounded.Email,
                    title = email,
                    subtitle = stringResource(R.string.set_email_action),
                ) { ShareUtils.sendEmail(context, email) }
                Divider()
                val github = stringResource(R.string.developer_github)
                ActionRow(
                    icon = Icons.Rounded.Code,
                    title = github,
                    subtitle = stringResource(R.string.set_github_action),
                ) { ShareUtils.openLink(context, github) }

                Divider()
                val myWebsite = stringResource(R.string.developer_website)
                ActionRow(
                    icon = Icons.Rounded.Code,
                    title = github,
                    subtitle = stringResource(R.string.set_mywebsite_action),
                ) { ShareUtils.openLink(context, myWebsite) }

            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
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
                }) { Text(stringResource(R.string.common_clear), color = Destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel), color = PC.text)
                }
            },
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.set_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.set_theme_light)
    ThemeMode.DARK -> stringResource(R.string.set_theme_dark)
}

/* ---------------- building blocks ---------------- */

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(PC.card)
            .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radius2xl)),
        content = content,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PC.text3,
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
            .background(PC.borderSoft)
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
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PC.text)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PC.text3,
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
                uncheckedTrackColor = PC.switchOff,
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
    tint: Color? = null,
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
        RowIcon(icon, tint = tint ?: Brand)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tint ?: PC.text)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PC.text3,
            )
        }
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = PC.text3,
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
            color = PC.text3,
        )
    }
}
