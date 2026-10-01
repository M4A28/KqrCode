package com.mohammed.mosa.qrscanner.util

import androidx.annotation.StringRes
import com.mohammed.mosa.qrscanner.R

/**
 * Maps the canonical (English) type keys stored in the database —
 * see [ScanTypes.describe] — to localized display names.
 */
@StringRes
fun typeLabelRes(type: String): Int = when (type) {
    "Link" -> R.string.type_link
    "Wi-Fi" -> R.string.type_wifi
    "Contact" -> R.string.type_contact
    "Phone" -> R.string.type_phone
    "SMS" -> R.string.type_sms
    "Email" -> R.string.type_email
    "Location" -> R.string.type_location
    "OTP" -> R.string.type_otp
    "Play Store" -> R.string.type_play_store
    else -> R.string.type_text
}
