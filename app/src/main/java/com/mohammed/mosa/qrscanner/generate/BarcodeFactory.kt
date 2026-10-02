package com.mohammed.mosa.qrscanner.generate


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.Writer
import com.google.zxing.WriterException
import com.google.zxing.aztec.AztecWriter
import com.google.zxing.datamatrix.DataMatrixWriter
import com.google.zxing.oned.*
import com.google.zxing.pdf417.PDF417Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.roundToInt

enum class GenFormat(
    val label: String,
    val zxing: BarcodeFormat,
    val hint: String,
) {
    QR("QR Code", BarcodeFormat.QR_CODE, "Any text, URL, Wi-Fi, vCard…"),
    EAN13("EAN-13", BarcodeFormat.EAN_13, "12–13 digits"),
    EAN8("EAN-8", BarcodeFormat.EAN_8, "7–8 digits"),
    UPCA("UPC-A", BarcodeFormat.UPC_A, "11–12 digits"),
    UPCE("UPC-E", BarcodeFormat.UPC_E, "6–8 digits"),
    CODE39("Code 39", BarcodeFormat.CODE_39, "A–Z 0–9 - . $ / + % space"),
    CODE93("Code 93", BarcodeFormat.CODE_93, "A–Z 0–9 - . $ / + % space"),
    CODE128("Code 128", BarcodeFormat.CODE_128, "Any ASCII text"),
    CODABAR("Codabar", BarcodeFormat.CODABAR, "A–D guards, e.g. A2013B"),
    ITF("ITF", BarcodeFormat.ITF, "Even number of digits"),
    PDF417("PDF 417", BarcodeFormat.PDF_417, "Any text"),
    AZTEC("Aztec", BarcodeFormat.AZTEC, "Any text"),
    DATA_MATRIX("Data Matrix", BarcodeFormat.DATA_MATRIX, "Any text");

    val isTwoDimensional: Boolean
        get() = this == QR || this == AZTEC || this == DATA_MATRIX || this == PDF417
}

object BarcodeRenderer {

    fun render(
        content: String,
        format: GenFormat,
        size: Int = 1024,
        foregroundColor: Int = 0xFF000000.toInt(),
        hints: Map<EncodeHintType, Any> = mapOf(EncodeHintType.MARGIN to 2),
    ): Bitmap {
        val writer: Writer = when (format.zxing) {
            BarcodeFormat.QR_CODE -> QRCodeWriter()
            BarcodeFormat.EAN_13 -> EAN13Writer()
            BarcodeFormat.EAN_8 -> EAN8Writer()
            BarcodeFormat.UPC_A -> UPCAWriter()
            BarcodeFormat.UPC_E -> UPCEWriter()
            BarcodeFormat.CODE_39 -> Code39Writer()
            BarcodeFormat.CODE_93 -> Code93Writer()
            BarcodeFormat.CODE_128 -> Code128Writer()
            BarcodeFormat.CODABAR -> CodaBarWriter()
            BarcodeFormat.ITF -> ITFWriter()
            BarcodeFormat.PDF_417 -> PDF417Writer()
            BarcodeFormat.AZTEC -> AztecWriter()
            BarcodeFormat.DATA_MATRIX -> DataMatrixWriter()
            else -> throw WriterException("Unsupported format")
        }

        val (w, h) = if (format.isTwoDimensional) size to size else size to size / 3

        val matrix = try {
            writer.encode(content.trim(), format.zxing, w, h, hints)
        } catch (e: IllegalArgumentException) {
            throw WriterException(e.message ?: "Invalid content for ${format.label}")
        }

        val fg = foregroundColor
        val bg = 0xFFFFFFFF.toInt()
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix.get(x, y)) fg else bg
            }
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Render with generator branding: an optional platform logo on a white
     * plate in the QR center (QR only — the code is re-encoded at error
     * correction level H so the covered area stays decodable), and an
     * optional footer line below the code.
     */
    fun renderStyled(
        content: String,
        format: GenFormat,
        size: Int = 1024,
        foregroundColor: Int = 0xFF000000.toInt(),
        logo: Bitmap? = null,
        footer: String? = null,
    ): Bitmap {
        val withLogo = if (logo != null && format == GenFormat.QR) {
            val hints = mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            )
            overlayLogo(render(content, format, size, foregroundColor, hints), logo)
        } else {
            render(content, format, size, foregroundColor)
        }
        return if (footer != null) appendFooter(withLogo, footer) else withLogo
    }

    private fun overlayLogo(code: Bitmap, logo: Bitmap): Bitmap {
        val out = code.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val plate = (out.width * 0.26f).roundToInt()
        val left = (out.width - plate) / 2f
        val top = (out.height - plate) / 2f
        val radius = plate / 4.5f

        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(left, top, left + plate, top + plate, radius, radius, platePaint)
        platePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = plate * 0.02f
            color = 0xFFDDDDDD.toInt()
        }
        canvas.drawRoundRect(left, top, left + plate, top + plate, radius, radius, platePaint)

        val glyph = (plate * 0.62f).roundToInt()
        val dst = RectF(
            left + (plate - glyph) / 2f,
            top + (plate - glyph) / 2f,
            left + (plate + glyph) / 2f,
            top + (plate + glyph) / 2f,
        )
        canvas.drawBitmap(
            logo, null, dst,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    private fun appendFooter(code: Bitmap, text: String): Bitmap {
        val band = (code.width * 0.085f).roundToInt()
        val out = Bitmap.createBitmap(code.width, code.height + band, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(code, 0f, 0f, null)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2B2B.toInt()
            textSize = band * 0.40f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = code.height + band / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, out.width / 2f, y, paint)
        return out
    }
}