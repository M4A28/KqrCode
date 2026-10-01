package com.mohammed.mosa.qrscanner.generate


import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.Writer
import com.google.zxing.WriterException
import com.google.zxing.aztec.AztecWriter
import com.google.zxing.datamatrix.DataMatrixWriter
import com.google.zxing.oned.*
import com.google.zxing.pdf417.PDF417Writer
import com.google.zxing.qrcode.QRCodeWriter

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
        val hints = mapOf(EncodeHintType.MARGIN to 2)

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
}