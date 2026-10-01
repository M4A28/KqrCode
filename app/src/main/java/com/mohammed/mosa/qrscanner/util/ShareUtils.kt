package com.mohammed.mosa.qrscanner.util


import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mohammed.mosa.qrscanner.R
import com.mohammed.mosa.qrscanner.generate.BarcodeRenderer
import com.mohammed.mosa.qrscanner.generate.GenFormat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareUtils {

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun openLink(context: Context, value: String) {
        val target = value.trim().let { if (it.startsWith("http", true)) it else "https://$it" }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share text"))
    }

    /** Renders `value` as a QR code PNG and opens the share sheet. */
    suspend fun shareAsImage(context: Context, value: String) {
        val bitmap = withContext(Dispatchers.Default) {
            BarcodeRenderer.render(value, GenFormat.QR)
        }
        shareBitmap(context, bitmap)
    }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "code_${System.currentTimeMillis()}.png").also { f ->
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, "Share image"))
        }
    }

    suspend fun shareCsv(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, "Export CSV"))
        }
    }

    /** Android 10+ → Pictures/QR Scanner via MediaStore; older → app folder. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/QR Scanner")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext context.getString(R.string.common_save_failed)
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                context.getString(R.string.common_saved_to_gallery)
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "QR Scanner",
                ).apply { mkdirs() }
                val file = File(dir, "$name.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "Saved to ${file.absolutePath}"
            }
        }
}