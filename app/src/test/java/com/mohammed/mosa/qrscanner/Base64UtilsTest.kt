package com.mohammed.mosa.qrscanner

import com.mohammed.mosa.qrscanner.util.Base64Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class Base64UtilsTest {

    @Test
    fun `decodes standard base64`() {
        val encoded = Base64.getEncoder().encodeToString("Hello world".toByteArray())
        assertEquals("Hello world", Base64Utils.decodeOrNull(encoded))
    }

    @Test
    fun `decodes plus and slash alphabet`() {
        // "Hello>" → SGVsbG8+, "Hello?" → SGVsbG8/
        assertEquals("Hello>", Base64Utils.decodeOrNull("SGVsbG8+"))
        assertEquals("Hello?", Base64Utils.decodeOrNull("SGVsbG8/"))
    }

    @Test
    fun `decodes url safe alphabet`() {
        // URL-safe encoding swaps '+' and '/' for '-' and '_'
        val bytes = "Hello world".toByteArray()
        val urlSafe = Base64.getUrlEncoder().encodeToString(bytes)
        assertEquals("Hello world", Base64Utils.decodeOrNull(urlSafe))
    }

    @Test
    fun `rejects non base64 and short values`() {
        assertNull(Base64Utils.decodeOrNull("short")) // < 8 chars
        assertNull(Base64Utils.decodeOrNull("!!!not base64!!!"))
        assertNull(Base64Utils.decodeOrNull("12345678")) // all digits
        assertNull(Base64Utils.decodeOrNull(""))
    }
}
