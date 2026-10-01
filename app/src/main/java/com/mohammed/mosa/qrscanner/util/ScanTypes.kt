package com.mohammed.mosa.qrscanner.util


object ScanTypes {

    data class WifiCredentials(
        val ssid: String,
        val password: String?,
        val security: String?,
    )

    data class ContactInfo(
        val name: String?,
        val phone: String?,
        val email: String?,
        val org: String?,
    )

    // Pure-JVM stand-in for android.util.Patterns.WEB_URL, so the logic stays
    // unit-testable without Robolectric: dotted host, optional port and path.
    private val webUrl = Regex(
        "^(?:https?://)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+(?::\\d{1,5})?(?:[/?#]\\S*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun isLink(value: String): Boolean {
        val v = value.trim()
        if (v.startsWith("http://", true) || v.startsWith("https://", true)) return true
        return !v.contains(' ') && v.contains('.') && webUrl.matches(v)
    }

    fun describe(value: String): String = when {
        value.startsWith("WIFI:", true) -> "Wi-Fi"
        value.startsWith("BEGIN:VCARD", true) -> "Contact"
        value.startsWith("MECARD:", true) -> "Contact"
        value.startsWith("tel:", true) -> "Phone"
        value.startsWith("smsto:", true) || value.startsWith("sms:", true) -> "SMS"
        value.startsWith("mailto:", true) -> "Email"
        value.startsWith("geo:", true) -> "Location"
        value.startsWith("otpauth:", true) -> "OTP"
        value.startsWith("market://", true) -> "Play Store"
        else -> if (isLink(value)) "Link" else "Text"
    }

    /** Parses `WIFI:T:WPA;S:name;P:pass;;` payloads, honoring the `\; \, \: \\` escapes. */
    fun parseWifi(value: String): WifiCredentials? {
        if (!value.startsWith("WIFI:", true)) return null
        var ssid: String? = null
        var password: String? = null
        var security: String? = null
        for (field in splitUnescaped(value.substring(5), ';')) {
            val idx = field.indexOf(':')
            if (idx <= 0) continue
            when (field.substring(0, idx).uppercase()) {
                "S" -> ssid = field.substring(idx + 1)
                "P" -> password = field.substring(idx + 1)
                "T" -> security = field.substring(idx + 1)
            }
        }
        ssid = ssid?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return WifiCredentials(ssid, password?.takeIf { it.isNotBlank() }, security)
    }

    /** Minimal VCARD 2/3 and MECARD parsing — just enough to prefill a contact. */
    fun parseContact(value: String): ContactInfo? {
        val v = value.trim()
        if (v.startsWith("BEGIN:VCARD", true)) {
            var name: String? = null
            var fallbackName: String? = null
            var phone: String? = null
            var email: String? = null
            var org: String? = null
            for (line in v.lines()) {
                val upper = line.uppercase()
                val idx = line.indexOf(':')
                if (idx < 0) continue
                val body = line.substring(idx + 1).trim()
                when {
                    upper.startsWith("FN:") -> name = body
                    upper.startsWith("N:") -> fallbackName = body.split(';')
                        .filter { it.isNotBlank() }
                        .reversed()
                        .joinToString(" ")
                        .ifBlank { null }
                    upper.startsWith("TEL") && phone == null -> phone = body.ifBlank { null }
                    upper.startsWith("EMAIL") && email == null -> email = body.ifBlank { null }
                    upper.startsWith("ORG:") -> org = body.ifBlank { null }
                }
            }
            return ContactInfo(name ?: fallbackName, phone, email, org)
                .takeIf { it.name != null || it.phone != null || it.email != null }
        }
        if (v.startsWith("MECARD:", true)) {
            var name: String? = null
            var phone: String? = null
            var email: String? = null
            for (field in splitUnescaped(v.substring(7), ';')) {
                val idx = field.indexOf(':')
                if (idx <= 0) continue
                when (field.substring(0, idx).uppercase()) {
                    "N" -> name = field.substring(idx + 1)
                        .split(',')
                        .filter { it.isNotBlank() }
                        .reversed()
                        .joinToString(" ")
                        .ifBlank { null }
                    "TEL" -> if (phone == null) phone = field.substring(idx + 1).ifBlank { null }
                    "EMAIL" -> if (email == null) email = field.substring(idx + 1).ifBlank { null }
                }
            }
            return ContactInfo(name, phone, email, null)
                .takeIf { it.name != null || it.phone != null || it.email != null }
        }
        return null
    }

    /** Splits on [sep] while folding `\c` escape sequences into the value. */
    private fun splitUnescaped(s: String, sep: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> { current.append(s[i + 1]); i += 2 }
                c == sep -> { parts.add(current.toString()); current.clear(); i++ }
                else -> { current.append(c); i++ }
            }
        }
        parts.add(current.toString())
        return parts
    }
}
