package com.mohammed.mosa.qrscanner.generate


import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mohammed.mosa.qrscanner.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.ui.theme.Brand

import com.mohammed.mosa.qrscanner.ui.theme.PC
import com.mohammed.mosa.qrscanner.ui.theme.Ui
import com.mohammed.mosa.qrscanner.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
/* ---------------- tokens ---------------- */


private val Error = Color(0xFFFF6B6B)

private val CodeColors = listOf(
    Color(0xFF111111),
    Color(0xFF7C5CFC),
    Color(0xFF4C8DFF),
    Color(0xFF2EC4B6),
    Color(0xFF34C77B),
    Color(0xFFFF5C8A),
)

private enum class ContentType(val labelRes: Int, val icon: ImageVector) {
    TEXT(R.string.gen_type_text, Icons.Rounded.Notes),
    LINK(R.string.gen_type_link, Icons.Rounded.Link),
    WIFI(R.string.gen_type_wifi, Icons.Rounded.Wifi),
    CONTACT(R.string.gen_type_contact, Icons.Rounded.Person),
    EMAIL(R.string.gen_type_email, Icons.Rounded.Email),
    PHONE(R.string.gen_type_phone, Icons.Rounded.Call),
    SMS(R.string.gen_type_sms, Icons.Rounded.Sms),
    LOCATION(R.string.gen_type_location, Icons.Rounded.Place),
}

private enum class WifiSecurity(val token: String, val labelRes: Int) {
    NONE("nopass", R.string.gen_sec_open),
    WPA("WPA", R.string.gen_sec_wpa),
    WEP("WEP", R.string.gen_sec_wep),
}

/** Localized input hint per barcode format. */
private fun GenFormat.hintRes(): Int = when (this) {
    GenFormat.QR -> R.string.gen_hint_qr
    GenFormat.EAN13 -> R.string.gen_hint_ean13
    GenFormat.EAN8 -> R.string.gen_hint_ean8
    GenFormat.UPCA -> R.string.gen_hint_upca
    GenFormat.UPCE -> R.string.gen_hint_upce
    GenFormat.CODE39 -> R.string.gen_hint_code39
    GenFormat.CODE93 -> R.string.gen_hint_code93
    GenFormat.CODE128 -> R.string.gen_hint_code128
    GenFormat.CODABAR -> R.string.gen_hint_codabar
    GenFormat.ITF -> R.string.gen_hint_itf
    GenFormat.PDF417 -> R.string.gen_hint_pdf417
    GenFormat.AZTEC -> R.string.gen_hint_aztec
    GenFormat.DATA_MATRIX -> R.string.gen_hint_datamatrix
}

/* ---------------- payload builders ---------------- */

private fun escapeWifi(s: String) = s
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace(":", "\\:")

/* ---------------- screen ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(repository: ScanRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var format by remember { mutableStateOf(GenFormat.QR) }
    var contentType by remember { mutableStateOf(ContentType.LINK) }

    var textValue by remember { mutableStateOf("") }
    var linkValue by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf(WifiSecurity.WPA) }
    var wifiHidden by remember { mutableStateOf(false) }
    var cName by remember { mutableStateOf("") }
    var cOrg by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cEmail by remember { mutableStateOf("") }
    var mailAddr by remember { mutableStateOf("") }
    var mailSubject by remember { mutableStateOf("") }
    var mailBody by remember { mutableStateOf("") }
    var telNumber by remember { mutableStateOf("") }
    var smsNumber by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }

    var codeColor by remember { mutableStateOf(CodeColors.first()) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }

    fun resetAll() {
        textValue = ""; linkValue = ""
        ssid = ""; wifiPass = ""; wifiSecurity = WifiSecurity.WPA; wifiHidden = false
        cName = ""; cOrg = ""; cPhone = ""; cEmail = ""
        mailAddr = ""; mailSubject = ""; mailBody = ""
        telNumber = ""; smsNumber = ""; smsBody = ""
        lat = ""; lng = ""
        codeColor = CodeColors.first()
        bitmap = null; error = null
    }

    /** Builds the raw string the code will encode. "" = not ready yet. */
    fun payload(): String {
        if (format != GenFormat.QR) return textValue.trim()
        return when (contentType) {
            ContentType.TEXT -> textValue.trim()
            ContentType.LINK -> {
                val v = linkValue.trim()
                when {
                    v.isEmpty() -> ""
                    v.startsWith("http", true) -> v
                    else -> "https://$v"
                }
            }
            ContentType.WIFI -> {
                val s = ssid.trim()
                if (s.isEmpty()) "" else buildString {
                    append("WIFI:T:${wifiSecurity.token};S:${escapeWifi(s)};")
                    if (wifiPass.isNotEmpty()) append("P:${escapeWifi(wifiPass)};")
                    if (wifiHidden) append("H:true;")
                    append(";")
                }
            }
            ContentType.CONTACT -> {
                val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
                cName.trim().takeIf { it.isNotEmpty() }?.let { lines.add("FN:$it") }
                cOrg.trim().takeIf { it.isNotEmpty() }?.let { lines.add("ORG:$it") }
                cPhone.trim().takeIf { it.isNotEmpty() }?.let { lines.add("TEL:$it") }
                cEmail.trim().takeIf { it.isNotEmpty() }?.let { lines.add("EMAIL:$it") }
                lines.add("END:VCARD")
                if (lines.size > 3) lines.joinToString("\n") else ""
            }
            ContentType.EMAIL -> {
                val a = mailAddr.trim()
                if (!a.contains("@")) "" else {
                    val params = mutableListOf<String>()
                    if (mailSubject.isNotBlank()) params.add("subject=${Uri.encode(mailSubject)}")
                    if (mailBody.isNotBlank()) params.add("body=${Uri.encode(mailBody)}")
                    if (params.isEmpty()) "mailto:$a" else "mailto:$a?${params.joinToString("&")}"
                }
            }
            ContentType.PHONE -> telNumber.trim().takeIf { it.isNotEmpty() }?.let { "tel:$it" } ?: ""
            ContentType.SMS -> smsNumber.trim().takeIf { it.isNotEmpty() }
                ?.let { "SMSTO:$it:${smsBody.trim()}" } ?: ""
            ContentType.LOCATION -> {
                val la = lat.trim().toDoubleOrNull()
                val lo = lng.trim().toDoubleOrNull()
                if (la != null && lo != null) "geo:$la,$lo" else ""
            }
        }
    }

    val encoded by remember { derivedStateOf { payload() } }

    // LIVE GENERATION — debounced, off the main thread
    LaunchedEffect(encoded, format, codeColor) {
        if (encoded.isBlank()) {
            bitmap = null; error = null
            return@LaunchedEffect
        }
        delay(300) // debounce keystrokes
        generating = true

        // Social media links get their platform logo in the QR center.
        val platform = SocialPlatform.detect(encoded)
        val logo = if (format == GenFormat.QR && platform != null) {
            SocialBranding.logoBitmap(context, platform, 320)
        } else {
            null
        }

        val outcome = withContext(Dispatchers.Default) {
            runCatching {
                BarcodeRenderer.renderStyled(
                    encoded,
                    format,
                    foregroundColor = codeColor.toArgb(),
                    logo = logo,
                    footer = SocialBranding.FOOTER_TEXT,
                )
            }
        }
        outcome.fold(
            onSuccess = { bitmap = it; error = null },
            onFailure = { bitmap = null; error = it.message ?: context.getString(R.string.gen_invalid_content, format.label) },
        )
        generating = false
    }

    val dirty = listOf(textValue, linkValue, ssid, cName, cOrg, cPhone, cEmail,
        mailAddr, mailSubject, mailBody, telNumber, smsNumber, smsBody, lat, lng)
        .any { it.isNotEmpty() }

    Box(modifier.fillMaxSize().background(PC.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            /* ---- header ---- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.gen_title), style = MaterialTheme.typography.titleLarge, color = PC.text)
                    Text(
                        stringResource(R.string.gen_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PC.text.copy(alpha = 0.55f),
                    )
                }
                TextButton(
                    onClick = { resetAll() },
                    enabled = dirty || bitmap != null,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = PC.text.copy(alpha = 0.6f),
                        disabledContentColor = PC.text.copy(alpha = 0.2f),
                    ),
                ) { Text(stringResource(R.string.common_clear)) }
            }

            Spacer(Modifier.height(16.dp))

            /* ---- format selector ---- */
            FormatSelector(selected = format, onClick = { showFormatSheet = true })

            Spacer(Modifier.height(20.dp))

            /* ---- content input ---- */
            if (format == GenFormat.QR) {
                TemplateChips(current = contentType, onSelect = { contentType = it })
                Spacer(Modifier.height(14.dp))
                TemplateForm(
                    type = contentType,
                    text = textValue, onText = { textValue = it },
                    link = linkValue, onLink = { linkValue = it },
                    ssid = ssid, onSsid = { ssid = it },
                    wifiPass = wifiPass, onWifiPass = { wifiPass = it },
                    wifiSecurity = wifiSecurity, onWifiSecurity = { wifiSecurity = it },
                    wifiHidden = wifiHidden, onWifiHidden = { wifiHidden = it },
                    cName = cName, onCName = { cName = it },
                    cOrg = cOrg, onCOrg = { cOrg = it },
                    cPhone = cPhone, onCPhone = { cPhone = it },
                    cEmail = cEmail, onCEmail = { cEmail = it },
                    mailAddr = mailAddr, onMailAddr = { mailAddr = it },
                    mailSubject = mailSubject, onMailSubject = { mailSubject = it },
                    mailBody = mailBody, onMailBody = { mailBody = it },
                    telNumber = telNumber, onTel = { telNumber = it },
                    smsNumber = smsNumber, onSmsNumber = { smsNumber = it },
                    smsBody = smsBody, onSmsBody = { smsBody = it },
                    lat = lat, onLat = { lat = it },
                    lng = lng, onLng = { lng = it },
                )
            } else {
                DarkField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = stringResource(R.string.gen_field_barcode_content),
                    supporting = stringResource(format.hintRes()),
                    singleLine = true,
                )
            }

            /* ---- code color ---- */
            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.gen_code_color))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CodeColors.forEach { c ->
                    val selected = c == codeColor
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) PC.text else PC.text.copy(alpha = 0.15f),
                                CircleShape,
                            )
                            .clickable { codeColor = c },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = PC.text, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            /* ---- encoded payload strip ---- */
            AnimatedVisibility(visible = encoded.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    EncodedBox(encoded)
                }
            }

            /* ---- preview ---- */
            Spacer(Modifier.height(18.dp))
            when {
                error != null -> ErrorPreview(error!!)
                bitmap != null -> SuccessPreview(
                    bitmap = bitmap!!,
                    format = format,
                    chars = encoded.length,
                    generating = generating,
                    onShare = { scope.launch { ShareUtils.shareBitmap(context, bitmap!!) } },
                    onSave = {
                        scope.launch {
                            val msg = ShareUtils.saveToGallery(context, bitmap!!, "code_${System.currentTimeMillis()}")
                            snackbar.showSnackbar(msg)
                        }
                    },
                    onSaveToHistory = {
                        scope.launch {
                            repository.addGenerated(encoded, format.label)
                            snackbar.showSnackbar(context.getString(R.string.common_saved_to_history))
                        }
                    },
                )
                else -> EmptyPreview()
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }

    /* ---- format picker sheet ---- */
    if (showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
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
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(stringResource(R.string.gen_format_sheet_title), style = MaterialTheme.typography.titleMedium, color = PC.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.gen_format_sheet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PC.text.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(16.dp))

                GroupLabel(stringResource(R.string.gen_group_2d))
                GenFormat.entries.filter { it.isTwoDimensional }.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { f ->
                            FormatCard(f, f == format, Modifier.weight(1f)) {
                                format = f; showFormatSheet = false
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                GroupLabel(stringResource(R.string.gen_group_1d))
                GenFormat.entries.filter { !it.isTwoDimensional }.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { f ->
                            FormatCard(f, f == format, Modifier.weight(1f)) {
                                // carry an already-built QR payload into the raw field
                                if (f != GenFormat.QR && format == GenFormat.QR) {
                                    val p = payload()
                                    if (p.isNotBlank() && textValue.isBlank()) textValue = p
                                }
                                format = f; showFormatSheet = false
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/* ---------------- preview states ---------------- */

@Composable
private fun EmptyPreview() {
    var t2 = PC.text.copy(alpha = 0.22f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .drawBehind {
                drawRoundRect(
                    color = t2,
                    cornerRadius = CornerRadius(Ui.radius2xl.toPx(), Ui.radius2xl.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.QrCode2, null, tint = PC.text.copy(alpha = 0.25f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.gen_preview_title), style = MaterialTheme.typography.titleSmall, color = PC.text.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.gen_preview_body),
                style = MaterialTheme.typography.bodySmall,
                color = PC.text.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorPreview(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(Error.copy(alpha = 0.08f))
            .border(1.dp, Error.copy(alpha = 0.35f), RoundedCornerShape(Ui.radius2xl))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFA3A3))
    }
}

@Composable
private fun SuccessPreview(
    bitmap: Bitmap,
    format: GenFormat,
    chars: Int,
    generating: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onSaveToHistory: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(Ui.radius2xl))
                .background(PC.text)
                .padding(12.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.gen_chars, format.label, chars),
            style = MaterialTheme.typography.labelMedium,
            color = PC.text3,
        )

        if (generating) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.5f).height(3.dp),
                color = Brand,
                trackColor = PC.well,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Ui.radiusXl),
                colors = ButtonDefaults.buttonColors(containerColor = PC.well, contentColor = PC.text),
                border = androidx.compose.foundation.BorderStroke(1.dp, PC.border),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.gen_share_png))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Ui.radiusXl),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = PC.text),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.gen_save_png))
            }
        }

        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = onSaveToHistory,
            shape = RoundedCornerShape(Ui.radiusXl),
            colors = ButtonDefaults.textButtonColors(contentColor = Brand),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.gen_save_to_history))
        }
    }
}
/* ---------------- building blocks ---------------- */

@Composable
private fun FormatSelector(selected: GenFormat, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ui.radius2xl))
            .background(PC.card)
            .border(1.dp, PC.text.copy(alpha = 0.1f), RoundedCornerShape(Ui.radius2xl))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatGlyph(selected, Brand, Modifier.size(width = 26.dp, height = 20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.gen_format), style = MaterialTheme.typography.labelSmall, color = PC.text.copy(alpha = 0.45f))
            Text(selected.label, style = MaterialTheme.typography.bodyMedium, color = PC.text)
        }
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = PC.text.copy(alpha = 0.4f))
    }
}

@Composable
private fun TemplateChips(current: ContentType, onSelect: (ContentType) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        ContentType.entries.forEach { t ->
            FilterChip(
                selected = t == current,
                onClick = { onSelect(t) },
                label = { Text(stringResource(t.labelRes)) },
                leadingIcon = { Icon(t.icon, null, modifier = Modifier.size(15.dp)) },
                modifier = Modifier.padding(end = 8.dp),
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun TemplateForm(
    type: ContentType,
    text: String, onText: (String) -> Unit,
    link: String, onLink: (String) -> Unit,
    ssid: String, onSsid: (String) -> Unit,
    wifiPass: String, onWifiPass: (String) -> Unit,
    wifiSecurity: WifiSecurity, onWifiSecurity: (WifiSecurity) -> Unit,
    wifiHidden: Boolean, onWifiHidden: (Boolean) -> Unit,
    cName: String, onCName: (String) -> Unit,
    cOrg: String, onCOrg: (String) -> Unit,
    cPhone: String, onCPhone: (String) -> Unit,
    cEmail: String, onCEmail: (String) -> Unit,
    mailAddr: String, onMailAddr: (String) -> Unit,
    mailSubject: String, onMailSubject: (String) -> Unit,
    mailBody: String, onMailBody: (String) -> Unit,
    telNumber: String, onTel: (String) -> Unit,
    smsNumber: String, onSmsNumber: (String) -> Unit,
    smsBody: String, onSmsBody: (String) -> Unit,
    lat: String, onLat: (String) -> Unit,
    lng: String, onLng: (String) -> Unit,
) {
    when (type) {
        ContentType.TEXT -> DarkField(text, onText, stringResource(R.string.gen_field_text), singleLine = false, minLines = 3)
        ContentType.LINK -> DarkField(
            link, onLink, stringResource(R.string.gen_field_url),
            placeholder = stringResource(R.string.gen_field_url_placeholder),
            supporting = stringResource(R.string.gen_field_url_support),
            leading = Icons.Rounded.Link,
        )
        ContentType.WIFI -> Column {
            DarkField(ssid, onSsid, stringResource(R.string.gen_field_ssid), leading = Icons.Rounded.Wifi, placeholder = stringResource(R.string.gen_field_ssid_placeholder))
            Spacer(Modifier.height(10.dp))
            DarkField(
                wifiPass, onWifiPass, stringResource(R.string.gen_field_password),
                leading = Icons.Rounded.Key,
                supporting = stringResource(R.string.gen_field_password_support),
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.gen_security))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WifiSecurity.entries.forEach { s ->
                    FilterChip(
                        selected = s == wifiSecurity,
                        onClick = { onWifiSecurity(s) },
                        label = { Text(stringResource(s.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = PC.text.copy(alpha = 0.06f),
                            labelColor = PC.text.copy(alpha = 0.8f),
                            selectedContainerColor = Brand.copy(alpha = 0.28f),
                            selectedLabelColor = PC.text,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WifiOff, null, tint = PC.text.copy(alpha = 0.6f), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.gen_hidden_network), style = MaterialTheme.typography.bodyMedium, color = PC.text, modifier = Modifier.weight(1f))
                Switch(
                    checked = wifiHidden,
                    onCheckedChange = onWifiHidden,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PC.text,
                        checkedTrackColor = Brand,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = PC.text.copy(alpha = 0.6f),
                        uncheckedTrackColor = PC.switchOff,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        ContentType.CONTACT -> Column {
            DarkField(cName, onCName, stringResource(R.string.gen_field_name), leading = Icons.Rounded.Person)
            Spacer(Modifier.height(10.dp))
            DarkField(cOrg, onCOrg, stringResource(R.string.gen_field_company), leading = Icons.Rounded.Work)
            Spacer(Modifier.height(10.dp))
            DarkField(cPhone, onCPhone, stringResource(R.string.gen_field_phone), leading = Icons.Rounded.Call, keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(10.dp))
            DarkField(cEmail, onCEmail, stringResource(R.string.gen_field_email), leading = Icons.Rounded.Email, keyboardType = KeyboardType.Email)
        }
        ContentType.EMAIL -> Column {
            DarkField(
                mailAddr, onMailAddr, stringResource(R.string.gen_field_recipient),
                placeholder = stringResource(R.string.gen_field_recipient_placeholder),
                leading = Icons.Rounded.Email,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(10.dp))
            DarkField(mailSubject, onMailSubject, stringResource(R.string.gen_field_subject), leading = Icons.Rounded.ShortText)
            Spacer(Modifier.height(10.dp))
            DarkField(mailBody, onMailBody, stringResource(R.string.gen_field_message), singleLine = false, minLines = 3)
        }
        ContentType.PHONE -> DarkField(
            telNumber, onTel, stringResource(R.string.gen_field_phone_number),
            leading = Icons.Rounded.Call,
            keyboardType = KeyboardType.Phone,
        )
        ContentType.SMS -> Column {
            DarkField(
                smsNumber, onSmsNumber, stringResource(R.string.gen_field_phone_number),
                leading = Icons.Rounded.Call,
                keyboardType = KeyboardType.Phone,
            )
            Spacer(Modifier.height(10.dp))
            DarkField(smsBody, onSmsBody, stringResource(R.string.gen_field_message), singleLine = false, minLines = 3)
        }
        ContentType.LOCATION -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DarkField(
                lat, onLat, stringResource(R.string.gen_field_latitude),
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal,
            )
            DarkField(
                lng, onLng, stringResource(R.string.gen_field_longitude),
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

@Composable
private fun EncodedBox(payload: String) {
    Column {
        Text(
            stringResource(R.string.gen_encoded_data),
            style = MaterialTheme.typography.labelSmall,
            color = PC.text.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ui.radiusXl))
                .background(PC.well)
                .border(1.dp, PC.text.copy(alpha = 0.08f), RoundedCornerShape(Ui.radiusXl))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                payload,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = PC.text.copy(alpha = 0.65f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Deterministic mini-glyph: pixel grid for 2D, bars for 1D. */
@Composable
private fun FormatGlyph(format: GenFormat, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (format.isTwoDimensional) {
            val cells = 5
            val cell = min(size.width, size.height) / cells
            val ox = (size.width - cells * cell) / 2f
            val oy = (size.height - cells * cell) / 2f
            for (y in 0 until cells) {
                for (x in 0 until cells) {
                    if (((x * 7 + y * 3 + x * y) % 4) != 0) {
                        drawRect(
                            tint,
                            topLeft = Offset(ox + x * cell, oy + y * cell),
                            size = Size(cell * 0.8f, cell * 0.8f),
                        )
                    }
                }
            }
        } else {
            val widths = floatArrayOf(3f, 1f, 2f, 1f, 4f, 1f, 2f, 3f, 1f, 2f, 1f, 3f, 2f, 1f)
            val total = widths.sum()
            var x = 0f
            widths.forEach { w ->
                val bar = (w / total) * size.width
                drawRect(tint, topLeft = Offset(x, 0f), size = Size(bar * 0.7f, size.height))
                x += bar
            }
        }
    }
}

@Composable
private fun FormatCard(f: GenFormat, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(Ui.radiusXl))
            .background(if (selected) Brand.copy(alpha = 0.15f) else PC.text.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (selected) Brand.copy(alpha = 0.8f) else PC.text.copy(alpha = 0.1f),
                RoundedCornerShape(Ui.radiusXl),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatGlyph(f, if (selected) Brand else PC.text.copy(alpha = 0.65f), Modifier.size(width = 24.dp, height = 18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                f.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) PC.text else PC.text.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (f.isTwoDimensional) "2D" else "1D",
                style = MaterialTheme.typography.labelSmall,
                color = PC.text.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = PC.text.copy(alpha = 0.35f)) } },
        supportingText = supporting?.let { { Text(it) } },
        leadingIcon = leading?.let { { Icon(it, null, tint = PC.text.copy(alpha = 0.5f)) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 6,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(Ui.radiusXl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand,
            unfocusedBorderColor = PC.text.copy(alpha = 0.15f),
            cursorColor = Brand,
            focusedLabelColor = Brand,
            unfocusedLabelColor = PC.text.copy(alpha = 0.5f),
            focusedContainerColor = PC.card,
            unfocusedContainerColor = PC.card,
            focusedTextColor = PC.text,
            unfocusedTextColor = PC.text,
            focusedSupportingTextColor = PC.text.copy(alpha = 0.4f),
            unfocusedSupportingTextColor = PC.text.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PC.text.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = PC.text.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
}