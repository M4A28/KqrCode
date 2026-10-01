package com.mohammed.mosa.qrscanner.util


import android.util.Patterns

object ScanTypes {

    fun isLink(value: String): Boolean {
        val v = value.trim()
        return v.startsWith("http://", true) ||
                v.startsWith("https://", true) ||
                (!v.contains(" ") && v.contains(".") && Patterns.WEB_URL.matcher(v).matches())
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
}