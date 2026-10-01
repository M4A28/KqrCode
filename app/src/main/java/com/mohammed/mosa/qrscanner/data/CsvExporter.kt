package com.mohammed.mosa.qrscanner.data


import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    suspend fun export(context: Context, scans: List<ScanEntity>): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "qr_history_$stamp.csv")

            file.bufferedWriter().use { w ->
                w.write("\uFEFF")
                w.appendLine("id,value,format,type,is_link,is_favorite,is_generated,created_at")
                val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                scans.forEach { s ->
                    w.appendLine(
                        listOf(
                            s.id, s.value.csvEscape(), s.format, s.type,
                            s.isLink, s.isFavorite, s.isGenerated,
                            iso.format(Date(s.createdAt)),
                        ).joinToString(",")
                    )
                }
            }
            file
        }

    private fun String.csvEscape(): String =
        if (contains('"') || contains(',') || contains('\n') || contains('\r'))
            "\"" + replace("\"", "\"\"") + "\""
        else this
}