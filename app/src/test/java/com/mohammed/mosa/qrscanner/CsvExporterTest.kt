package com.mohammed.mosa.qrscanner

import com.mohammed.mosa.qrscanner.data.CsvExporter
import com.mohammed.mosa.qrscanner.data.ScanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun entity(value: String, id: Long = 1) = ScanEntity(
        id = id,
        value = value,
        format = "QR_CODE",
        type = "Text",
        isLink = false,
        isFavorite = false,
        isGenerated = false,
        createdAt = 0L,
    )

    @Test
    fun `plain values are written unquoted`() {
        val csv = CsvExporter.buildCsv(listOf(entity("hello")))
        val lines = csv.trim().removePrefix("\uFEFF").lines()
        assertEquals(
            "id,value,format,type,is_link,is_favorite,is_generated,created_at",
            lines[0],
        )
        assertTrue(lines[1].startsWith("1,hello,QR_CODE,Text,false,false,false,"))
        assertEquals(2, lines.size)
    }

    @Test
    fun `commas quotes and newlines are quoted and escaped`() {
        val csv = CsvExporter.buildCsv(listOf(entity("say \"hi\", friend\nnext")))
        assertTrue(csv.contains("1,\"say \"\"hi\"\", friend\nnext\",QR_CODE"))
    }

    @Test
    fun `empty list produces header only`() {
        val csv = CsvExporter.buildCsv(emptyList())
        assertEquals("\uFEFFid,value,format,type,is_link,is_favorite,is_generated,created_at\n", csv)
    }
}
