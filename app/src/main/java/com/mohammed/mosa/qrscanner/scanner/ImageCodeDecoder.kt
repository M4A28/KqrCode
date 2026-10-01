package com.mohammed.mosa.qrscanner.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Decodes a code from a still image — gallery picks and images shared into the app. */
object ImageCodeDecoder {

    /** Returns `value to formatName`, or null when nothing was found / the image failed. */
    suspend fun decode(context: Context, uri: Uri): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            )
            try {
                val input = InputImage.fromFilePath(context, uri) // handles EXIF rotation
                suspendCancellableCoroutine { cont ->
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val hit = barcodes.firstOrNull()
                            val raw = hit?.rawValue
                            if (raw == null) cont.resume(null)
                            else cont.resume(raw to formatName(hit!!.format))
                        }
                        .addOnFailureListener { cont.resume(null) }
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { scanner.close() }
            }
        }
}
