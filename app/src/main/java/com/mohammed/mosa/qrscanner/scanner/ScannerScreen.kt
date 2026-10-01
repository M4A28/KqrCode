package com.mohammed.mosa.qrscanner.scanner


import android.Manifest
import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.R
import com.mohammed.mosa.qrscanner.data.AppSettings
import com.mohammed.mosa.qrscanner.data.CsvExporter
import com.mohammed.mosa.qrscanner.data.ScanEntity
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.data.SettingsRepository
import com.mohammed.mosa.qrscanner.generate.BarcodeRenderer
import com.mohammed.mosa.qrscanner.generate.GenFormat
import com.mohammed.mosa.qrscanner.ui.theme.Brand
import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.Base64Utils
import com.mohammed.mosa.qrscanner.util.ScanTypes
import com.mohammed.mosa.qrscanner.util.ShareUtils
import com.mohammed.mosa.qrscanner.util.SmartAction
import com.mohammed.mosa.qrscanner.util.SmartActions
import com.mohammed.mosa.qrscanner.util.typeLabelRes
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.text.format.DateUtils

/** One captured code during a batch scan session. */
data class SessionScan(val value: String, val format: String, val at: Long)

private val SessionSaver = listSaver<SnapshotStateList<SessionScan>, String>(
    save = { list -> list.flatMap { listOf(it.value, it.format, it.at.toString()) } },
    restore = { flat ->
        flat.chunked(3)
            .map { SessionScan(it[0], it[1], it[2].toLong()) }
            .toMutableStateList()
    },
)

@Composable
fun ScannerScreen(
    repository: ScanRepository,
    incomingImage: Uri? = null,
    onIncomingImageHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val settingsRepo = remember { SettingsRepository.get(context) }
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }
    val vibrator = remember { ContextCompat.getSystemService(context, Vibrator::class.java) }

    // The camera is live on this tab — keep the screen awake while it's visible.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ---- camera permission ----
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted; askedOnce = true }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // ---- scan state ----
    var result by remember { mutableStateOf<String?>(null) }
    var resultFormat by remember { mutableStateOf("QR_CODE") }
    var torchOn by remember { mutableStateOf(false) }
    var savedToHistory by remember { mutableStateOf(false) }
    var showSavedPill by remember { mutableStateOf(false) }
    val scanning = result == null

    // ---- batch scan session ----
    var sessionMode by rememberSaveable { mutableStateOf(false) }
    val session = rememberSaveable(saver = SessionSaver) { mutableStateListOf<SessionScan>() }
    var showSession by remember { mutableStateOf(false) }
    var sessionPillCount by remember { mutableStateOf(0) }

    fun playScanFeedback() {
        if (settings.vibrate) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (settings.sound) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        }
    }

    // Base64 detection — recomputed only when the result changes
    val decoded = remember(result) { result?.let { Base64Utils.decodeOrNull(it) } }

    // ---- decode from a still image (gallery pick or image shared into the app) ----
    var decoding by remember { mutableStateOf(false) }
    var imageNotFound by remember { mutableStateOf(false) }

    fun onImagePicked(uri: Uri?) {
        uri ?: return
        scope.launch {
            decoding = true
            imageNotFound = false
            val hit = runCatching { ImageCodeDecoder.decode(context, uri) }.getOrNull()
            decoding = false
            if (hit != null) {
                if (sessionMode) {
                    session.add(SessionScan(hit.first, hit.second, System.currentTimeMillis()))
                    sessionPillCount = session.size
                } else {
                    resultFormat = hit.second
                    result = hit.first
                }
            } else {
                imageNotFound = true
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onImagePicked(uri) }

    LaunchedEffect(incomingImage) {
        if (incomingImage != null) {
            onImagePicked(incomingImage)
            onIncomingImageHandled()
        }
    }

    LaunchedEffect(imageNotFound) {
        if (imageNotFound) { delay(2500); imageNotFound = false }
    }

    LaunchedEffect(sessionPillCount) {
        if (sessionPillCount > 0) { delay(1800); sessionPillCount = 0 }
    }

    LaunchedEffect(showSavedPill) {
        if (showSavedPill) { delay(2000); showSavedPill = false }
    }

    // Feedback + auto-save + auto-open, the moment a code is detected
    LaunchedEffect(result) {
        val current = result ?: return@LaunchedEffect
        val format = resultFormat

        playScanFeedback()
        if (settings.autoCopy) {
            clipboard.setText(AnnotatedString(current))
        }
        if (settings.autoSave) {
            savedToHistory = false
            repository.saveAsync(current, format) { saved ->
                if (saved) {
                    savedToHistory = true
                    showSavedPill = true
                }
            }
        }
        // AUTO-OPEN: short delay so the card is visible; tapping "Scan again"
        // sets result = null which cancels this coroutine before opening.
        if (settings.autoOpenUrl && ScanTypes.isLink(current)) {
            delay(1500)
            ShareUtils.openLink(context, current)
        }
    }

    val hazeState = rememberHazeState()

    Surface(modifier.fillMaxSize(), color = PC.bg) {
        if (!hasPermission) {
            PermissionCard(askedOnce) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        } else {
            Box(Modifier.fillMaxSize()) {

                CameraPreview(
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    isPaused = !scanning && !settings.continuousScan && !sessionMode,
                    torchEnabled = torchOn,
                    useFrontCamera = settings.useFrontCamera,
                    onResult = { value, format ->
                        if (sessionMode) {
                            // Batch mode: collect, don't stop on a result.
                            val now = System.currentTimeMillis()
                            val last = session.lastOrNull()
                            if (last?.value != value || now - last.at > 2500) {
                                playScanFeedback()
                                session.add(SessionScan(value, format, now))
                                sessionPillCount = session.size
                                if (settings.autoSave) {
                                    repository.saveAsync(value, format)
                                }
                            }
                        } else {
                            resultFormat = format
                            result = value
                        }
                    },
                )

                ViewfinderOverlay(Modifier.fillMaxSize(), scanning)

                ScannerTopBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    hazeState = hazeState,
                    torchOn = torchOn,
                    onToggleTorch = { torchOn = !torchOn },
                    onPickImage = { galleryLauncher.launch("image/*") },
                    sessionMode = sessionMode,
                    onToggleSession = { sessionMode = !sessionMode },
                )

                val pillSlot = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp)
                StatusPill(
                    visible = showSavedPill,
                    text = stringResource(R.string.common_saved_to_history),
                    modifier = pillSlot,
                )
                StatusPill(
                    visible = decoding,
                    text = stringResource(R.string.scan_pill_decoding),
                    modifier = pillSlot,
                )
                StatusPill(
                    visible = imageNotFound,
                    text = stringResource(R.string.scan_pill_not_found),
                    isError = true,
                    modifier = pillSlot,
                )
                StatusPill(
                    visible = sessionPillCount > 0,
                    text = stringResource(R.string.scan_pill_session_added, sessionPillCount),
                    modifier = pillSlot,
                )

                if (sessionMode) {
                    SessionChip(
                        count = session.size,
                        hazeState = hazeState,
                        onClick = { showSession = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    )
                } else {
                    HintPill(
                        hazeState = hazeState,
                        visible = scanning,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    )
                }

                AnimatedVisibility(
                    visible = !scanning && !sessionMode,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    enter = slideInVertically(tween(Ui.duration, easing = Ui.easing)) { it } +
                            fadeIn(tween(Ui.duration, easing = Ui.easing)),
                    exit = slideOutVertically(tween(Ui.duration, easing = Ui.easing)) { it } +
                            fadeOut(tween(Ui.duration, easing = Ui.easing)),
                ) {
                    ResultCard(
                        value = result.orEmpty(),
                        format = resultFormat,
                        saved = savedToHistory,
                        base64Decoded = decoded,
                        hazeState = hazeState,
                        onCopy = { clipboard.setText(AnnotatedString(result.orEmpty())) },
                        onCopyDecoded = { clipboard.setText(AnnotatedString(decoded.orEmpty())) },
                        onOpen = { ShareUtils.openLink(context, result.orEmpty()) },
                        onShareText = { ShareUtils.shareText(context, result.orEmpty()) },
                        onShareImage = {
                            scope.launch { ShareUtils.shareAsImage(context, result.orEmpty()) }
                        },
                        onSaveImage = {
                            scope.launch {
                                val msg = runCatching {
                                    val bmp = BarcodeRenderer.render(result.orEmpty(), GenFormat.QR)
                                    ShareUtils.saveToGallery(
                                        context, bmp, "scan_${System.currentTimeMillis()}",
                                    )
                                }.getOrDefault(context.getString(R.string.common_save_failed))
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onScanAgain = {
                            result = null; savedToHistory = false; imageNotFound = false
                        },
                    )
                }
            }
        }
    }

    if (showSession) {
        SessionSheet(
            session = session,
            onDismiss = { showSession = false },
            onCopyAll = {
                clipboard.setText(AnnotatedString(session.joinToString("\n") { it.value }))
                Toast.makeText(
                    context, context.getString(R.string.common_copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onExport = {
                scope.launch {
                    runCatching {
                        val entities = session.map {
                            ScanEntity(
                                value = it.value,
                                format = it.format,
                                type = ScanTypes.describe(it.value),
                                isLink = ScanTypes.isLink(it.value),
                                createdAt = it.at,
                            )
                        }
                        val file = CsvExporter.export(context, entities)
                        ShareUtils.shareCsv(context, file)
                    }
                }
            },
            onRemove = { session.remove(it) },
            onClear = { session.clear() },
            onEnd = {
                session.clear()
                sessionMode = false
                showSession = false
            },
        )
    }
}

/* ---------------- scan session ---------------- */

@Composable
private fun SessionChip(
    count: Int,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(tween(Ui.duration, easing = Ui.easing)) +
                slideInVertically(tween(Ui.duration, easing = Ui.easing)) { it / 2 },
        exit = fadeOut(tween(Ui.duration, easing = Ui.easing)),
    ) {
        GlassPanel(shape = RoundedCornerShape(50), hazeState = hazeState, onClick = onClick) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Inventory2, null, tint = Brand, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.session_chip, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = PC.text,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheet(
    session: List<SessionScan>,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onExport: () -> Unit,
    onRemove: (SessionScan) -> Unit,
    onClear: () -> Unit,
    onEnd: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.session_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = PC.text,
                    )
                    Text(
                        stringResource(R.string.session_count, session.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = PC.text2)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCopyAll,
                    enabled = session.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.session_copy_all))
                }
                Button(
                    onClick = onExport,
                    enabled = session.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.FileDownload, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_export_csv))
                }
                Button(
                    onClick = onClear,
                    enabled = session.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_clear))
                }
            }

            Spacer(Modifier.height(12.dp))

            if (session.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Inventory2, null, tint = PC.text4, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.session_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PC.text3,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(session, key = { _, it -> it.at }) { _, item ->
                        val rel = remember(item.at) {
                            DateUtils.getRelativeTimeSpanString(item.at).toString()
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Ui.radiusXl))
                                .background(PC.well)
                                .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radiusXl))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PC.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    rel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PC.text3,
                                )
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(item.value)) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy, stringResource(R.string.common_copy),
                                    tint = PC.text2, modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { onRemove(item) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close, stringResource(R.string.common_remove),
                                    tint = PC.text3, modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onEnd,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(Ui.radiusXl),
                colors = ButtonDefaults.textButtonColors(contentColor = Brand),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.session_end))
            }
        }
    }
}

/* ---------------- glass panel (theme-aware) ---------------- */

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Ui.radius2xl),
    hazeState: HazeState? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val pc = PC
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (hazeState != null)
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            tints = listOf(HazeTint(pc.glassTint)),
                            blurRadius = Ui.blurXl,
                            noiseFactor = 0f,
                        ),
                    )
                else Modifier.background(pc.glassFill, shape)
            )
            .border(1.dp, pc.glassStroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/* ---------------- status pill ---------------- */

@Composable
private fun StatusPill(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val bg = if (isError) Color(0xFFE5484D) else Brand
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Ui.duration, easing = Ui.easing)) +
                slideInVertically(tween(Ui.duration, easing = Ui.easing)) { -it / 2 },
        exit = fadeOut(tween(Ui.duration, easing = Ui.easing)) +
                slideOutVertically(tween(Ui.duration, easing = Ui.easing)) { -it / 2 },
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(bg.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Check,
                null, tint = Color.White, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

/* ---------------- top bar ---------------- */

@Composable
private fun ScannerTopBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onPickImage: () -> Unit,
    sessionMode: Boolean,
    onToggleSession: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassPanel(shape = RoundedCornerShape(50), hazeState = hazeState) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null, tint = Brand, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = PC.text,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GlassPanel(
            shape = CircleShape, hazeState = hazeState,
            onClick = onToggleSession, modifier = Modifier.size(46.dp),
        ) {
            Icon(
                Icons.Rounded.Inventory2,
                contentDescription = stringResource(R.string.scan_session_mode),
                tint = if (sessionMode) Brand else PC.text,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        GlassPanel(
            shape = CircleShape, hazeState = hazeState,
            onClick = onPickImage, modifier = Modifier.size(46.dp),
        ) {
            Icon(
                Icons.Rounded.PhotoLibrary,
                contentDescription = stringResource(R.string.scan_pick_image),
                tint = PC.text,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        GlassPanel(
            shape = CircleShape, hazeState = hazeState,
            onClick = onToggleTorch, modifier = Modifier.size(46.dp),
        ) {
            Icon(
                if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                contentDescription = stringResource(R.string.scan_toggle_torch),
                tint = if (torchOn) Brand else PC.text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* ---------------- hint pill ---------------- */

@Composable
private fun HintPill(hazeState: HazeState, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Ui.duration, easing = Ui.easing)) +
                slideInVertically(tween(Ui.duration, easing = Ui.easing)) { it / 2 },
        exit = fadeOut(tween(Ui.duration, easing = Ui.easing)),
    ) {
        GlassPanel(shape = RoundedCornerShape(50), hazeState = hazeState) {
            Text(
                stringResource(R.string.scan_hint),
                style = MaterialTheme.typography.labelMedium,
                color = PC.text.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/* ---------------- viewfinder + scrim ---------------- */

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier, scanning: Boolean) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val sideDp = (minOf(maxWidth, maxHeight) - 120.dp).coerceIn(220.dp, 320.dp)
        val side = with(density) { sideDp.toPx() }
        val radius = with(density) { Ui.radius2xl.toPx() }
        var scrim = PC.scrim
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {

            drawRect(scrim)
            drawRoundRect(
                Color.Black,
                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Clear,
            )
        }

        ViewfinderFrame(
            modifier = Modifier.align(Alignment.Center).size(sideDp),
            scanning = scanning,
        )
    }
}

@Composable
private fun ViewfinderFrame(modifier: Modifier = Modifier, scanning: Boolean) {
    val frameColor by animateColorAsState(
        targetValue = if (scanning) Brand else Color.White, // stays white over camera in both themes
        animationSpec = tween(Ui.duration, easing = Ui.easing),
        label = "frameColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (scanning) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "scale",
    )

    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val stroke = 5.dp.toPx()
            val r = Ui.radius2xl.toPx()
            val corner = 32.dp.toPx()
            val pad = 3.dp.toPx()
            val w = size.width
            val h = size.height

            drawRoundRect(
                color = frameColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(w - stroke, h - stroke),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            fun clear(l: Float, t: Float, rr: Float, b: Float) = drawRect(
                Color.Black, topLeft = Offset(l, t),
                size = Size(rr - l, b - t), blendMode = BlendMode.Clear,
            )

            clear(corner, -pad, w - corner, stroke + pad)
            clear(corner, h - stroke - pad, w - corner, h + pad)
            clear(-pad, corner, stroke + pad, h - corner)
            clear(w - stroke - pad, corner, w + pad, h - corner)
        }

        if (scanning) {
            val transition = rememberInfiniteTransition(label = "scanline")
            val t by transition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
                label = "t",
            )
            Canvas(Modifier.matchParentSize()) {
                val inset = Ui.radius2xl.toPx() + 6.dp.toPx()
                val y = inset + t * (size.height - inset * 2)
                val x0 = inset + 10f
                val x1 = size.width - inset - 10f
                drawLine(Brand.copy(alpha = 0.35f), Offset(x0, y), Offset(x1, y), 12f, StrokeCap.Round)
                drawLine(Color.White, Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/* ---------------- result card ---------------- */

@Composable
private fun ResultCard(
    value: String,
    format: String,
    saved: Boolean,
    base64Decoded: String?,
    hazeState: HazeState,
    onCopy: () -> Unit,
    onCopyDecoded: () -> Unit,
    onOpen: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onSaveImage: () -> Unit,
    onScanAgain: () -> Unit,
) {
    val context = LocalContext.current
    val isLink = remember(value) { ScanTypes.isLink(value) }
    val smart = remember(value) { SmartActions.forValue(value) }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    GlassPanel(modifier = Modifier.fillMaxWidth(), hazeState = hazeState) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Brand.copy(alpha = 0.18f))
                        .border(1.dp, Brand.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Brand, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.scan_result_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = PC.text,
                    )
                    Text(
                        stringResource(
                            R.string.scan_result_subtitle,
                            stringResource(typeLabelRes(ScanTypes.describe(value))),
                            format,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                if (saved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircleOutline, null, tint = Brand, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.scan_result_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = PC.text2,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(Ui.radiusXl))
                    .background(PC.well)
                    .border(1.dp, PC.borderSoft, RoundedCornerShape(Ui.radiusXl))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .heightIn(max = 120.dp)
            ) {
                SelectionContainer {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PC.text,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }

            // ---- Base64 section ----
            if (base64Decoded != null) {
                var showDecoded by remember(value) { mutableStateOf(false) }
                Spacer(Modifier.height(10.dp))
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.scan_base64_decoded),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PC.text3,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier.clip(CircleShape).clickable(onClick = onCopyDecoded).padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        stringResource(R.string.scan_copy_decoded),
                                        tint = PC.text2,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    isLink -> {
                        Button(
                            onClick = onOpen,
                            shape = RoundedCornerShape(Ui.radiusXl),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.common_open_link))
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    smart != null -> {
                        Button(
                            onClick = { smart.execute(context) },
                            shape = RoundedCornerShape(Ui.radiusXl),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(smart.icon, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(smart.labelRes))
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                }
                Button(
                    onClick = { onCopy(); copied = true },
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (copied) stringResource(R.string.common_copied)
                        else stringResource(R.string.common_copy)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onScanAgain,
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.textButtonColors(contentColor = Brand),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.scan_scan_again))
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSaveImage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_save))
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
        }
    }
}

/* ---------------- permission UI ---------------- */

@Composable
private fun PermissionCard(askedBefore: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(Brand.copy(alpha = 0.15f))
                .border(1.dp, Brand.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhotoCamera, null, tint = Brand, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.scan_permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = PC.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.scan_permission_text),
            style = MaterialTheme.typography.bodyMedium,
            color = PC.text2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(Ui.radiusXl),
            colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                if (askedBefore) stringResource(R.string.scan_permission_retry)
                else stringResource(R.string.scan_permission_allow)
            )
        }
    }
}
