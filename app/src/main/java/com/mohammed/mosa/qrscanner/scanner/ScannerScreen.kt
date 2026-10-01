package com.mohammed.mosa.qrscanner.scanner


import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammed.mosa.qrscanner.data.AppSettings
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.data.SettingsRepository
import com.mohammed.mosa.qrscanner.ui.theme.Brand
import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.Base64Utils
import com.mohammed.mosa.qrscanner.util.ScanTypes
import com.mohammed.mosa.qrscanner.util.ShareUtils
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(repository: ScanRepository, modifier: Modifier = Modifier) {
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

    // Base64 detection — recomputed only when the result changes
    val decoded = remember(result) { result?.let { Base64Utils.decodeOrNull(it) } }

    // Feedback + auto-save + auto-open, the moment a code is detected
    LaunchedEffect(result) {
        val current = result ?: return@LaunchedEffect
        val format = resultFormat

        if (settings.vibrate) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            //vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (settings.sound) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        }
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

    LaunchedEffect(showSavedPill) {
        if (showSavedPill) { delay(2000); showSavedPill = false }
    }

    val hazeState = rememberHazeState()

    Surface(modifier.fillMaxSize(), color = PC.bg) {
        if (!hasPermission) {
            PermissionCard(askedOnce) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        } else {
            Box(Modifier.fillMaxSize()) {

                CameraPreview(
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    isPaused = !scanning && !settings.continuousScan,
                    torchEnabled = torchOn,
                    useFrontCamera = settings.useFrontCamera,
                    onResult = { value, format ->
                        resultFormat = format
                        result = value
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
                )

                SavedPill(
                    visible = showSavedPill,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 76.dp),
                )

                HintPill(
                    hazeState = hazeState,
                    visible = scanning,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )

                AnimatedVisibility(
                    visible = !scanning,
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
                        onScanAgain = { result = null; savedToHistory = false },
                    )
                }
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

/* ---------------- saved pill ---------------- */

@Composable
private fun SavedPill(visible: Boolean, modifier: Modifier = Modifier) {
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
                .background(Brand.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Saved to history", style = MaterialTheme.typography.labelMedium, color = Color.White)
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
                Text("QR Scanner", style = MaterialTheme.typography.labelLarge, color = PC.text)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassPanel(
            shape = CircleShape, hazeState = hazeState,
            onClick = onToggleTorch, modifier = Modifier.size(46.dp),
        ) {
            Icon(
                if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                contentDescription = "Toggle torch",
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
                "Point your camera at a QR or barcode",
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
    onScanAgain: () -> Unit,
) {
    val isLink = remember(value) { ScanTypes.isLink(value) }

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
                    Text("Code detected", style = MaterialTheme.typography.titleSmall, color = PC.text)
                    Text(
                        "${ScanTypes.describe(value)} • $format",
                        style = MaterialTheme.typography.labelMedium,
                        color = PC.text3,
                    )
                }
                if (saved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircleOutline, null, tint = Brand, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Saved", style = MaterialTheme.typography.labelSmall, color = PC.text2)
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
                    Text("Base64 encoded", style = MaterialTheme.typography.labelSmall, color = PC.text2, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { showDecoded = !showDecoded },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Brand),
                    ) { Text(if (showDecoded) "Hide" else "Decode") }
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
                                Text("Decoded", style = MaterialTheme.typography.labelSmall, color = PC.text3, modifier = Modifier.weight(1f))
                                Box(
                                    Modifier.clip(CircleShape).clickable(onClick = onCopyDecoded).padding(4.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "Copy decoded", tint = PC.text2, modifier = Modifier.size(15.dp))
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
                if (isLink) {
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(Ui.radiusXl),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open link")
                    }
                    Spacer(Modifier.width(10.dp))
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
                    Text(if (copied) "Copied" else "Copy")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onScanAgain,
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.textButtonColors(contentColor = Brand),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Scan again")
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onShareText,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Ui.radiusXl),
                    colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                    border = BorderStroke(1.dp, PC.border),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share as text")
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
                    Spacer(Modifier.width(8.dp))
                    Text("Share as image")
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
        Text("Camera access needed", style = MaterialTheme.typography.titleLarge, color = PC.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "The camera is used to detect codes on-device. Frames never leave your phone.",
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
            Text(if (askedBefore) "Try again" else "Allow camera")
        }
    }
}