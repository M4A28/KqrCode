package com.mohammed.mosa.qrscanner.util


import java.util.Base64

object Base64Utils {

    private val charset = Regex("^[A-Za-z0-9+/_-]+={0,2}$")

    /** True if [value] looks like Base64 AND decodes to mostly-printable text. */
    fun isBase64(value: String): Boolean = decodeOrNull(value) != null

    /**
     * Heuristic detection: length ≥ 8 and a multiple of 4, only Base64 chars
     * (standard + URL-safe), not purely numeric, decodes cleanly, and the
     * result is ≥90% printable — filters out ordinary words that happen to
     * match the alphabet.
     */
    fun decodeOrNull(value: String): String? {
        val v = value.trim()
        if (v.length < 8 || v.length % 4 != 0) return null
        if (!charset.matches(v)) return null
        if (v.all { it.isDigit() }) return null

        val normalized = v.replace('-', '+').replace('_', '/')
        val bytes = runCatching { Base64.getDecoder().decode(normalized) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null

        val printable = bytes.count { it in 0x20..0x7E || it in 0x09..0x0D || it < 0 }
        if (printable.toFloat() / bytes.size < 0.9f) return null

        val decoded = String(bytes, Charsets.UTF_8).trim()
        return decoded.ifBlank { null }
    }
}