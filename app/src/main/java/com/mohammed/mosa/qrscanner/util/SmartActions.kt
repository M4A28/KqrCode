package com.mohammed.mosa.qrscanner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.mohammed.mosa.qrscanner.R

/** A content-aware action derived from a scanned value, e.g. "Join Wi-Fi". */
data class SmartAction(
    val labelRes: Int,
    val icon: ImageVector,
    val execute: (Context) -> Unit,
)

object SmartActions {

    /** The type-specific action for [value], or null for plain text/links. */
    fun forValue(value: String): SmartAction? = when {
        ScanTypes.parseWifi(value) != null -> joinWifi(value)
        ScanTypes.parseContact(value) != null -> addContact(value)
        value.startsWith("tel:", true) -> dial(value)
        value.startsWith("mailto:", true) -> sendEmail(value)
        value.startsWith("smsto:", true) || value.startsWith("sms:", true) -> sendSms(value)
        value.startsWith("geo:", true) -> openMap(value)
        else -> null
    }

    /** Best action for result/history rows: the smart action, else "Open" for links. */
    fun primary(value: String): SmartAction? =
        forValue(value)
            ?: if (ScanTypes.isLink(value))
                SmartAction(R.string.action_open, Icons.Rounded.OpenInNew) { c ->
                    ShareUtils.openLink(c, value)
                }
            else null

    private fun joinWifi(value: String) = SmartAction(R.string.action_join_wifi, Icons.Rounded.Wifi) { context ->
        val creds = ScanTypes.parseWifi(value) ?: return@SmartAction
        // Android 10+: register a suggestion and the system prompts the user to
        // connect. WEP isn't supported there, and neither is anything below
        // Android 10 — both fall back to opening Wi-Fi settings.
        val wep = creds.security.equals("WEP", true)
        val applied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !wep) {
            runCatching {
                val manager = context.getSystemService(WifiManager::class.java)
                    ?: return@runCatching false
                val builder = WifiNetworkSuggestion.Builder().setSsid(creds.ssid)
                creds.password?.let { builder.setWpa2Passphrase(it) }
                manager.addNetworkSuggestions(listOf(builder.build())) ==
                        WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
            }.getOrDefault(false)
        } else {
            false
        }
        if (!applied) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun addContact(value: String) = SmartAction(R.string.action_add_contact, Icons.Rounded.PersonAdd) { c ->
        val info = ScanTypes.parseContact(value) ?: return@SmartAction
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            info.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            info.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            info.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            info.org?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        }
        runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun dial(value: String) = SmartAction(R.string.action_call, Icons.Rounded.Call) { c ->
        runCatching {
            c.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse(value.trim()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun sendEmail(value: String) = SmartAction(R.string.action_email, Icons.Rounded.Email) { c ->
        runCatching {
            c.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse(value.trim()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun sendSms(value: String) = SmartAction(R.string.action_sms, Icons.Rounded.Sms) { c ->
        // SMSTO:number:body — prefill the message body when present.
        val parts = value.split(':', limit = 3)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${parts.getOrNull(1) ?: ""}"))
        if (parts.size == 3) intent.putExtra("sms_body", parts[2])
        runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openMap(value: String) = SmartAction(R.string.action_open_map, Icons.Rounded.Map) { c ->
        runCatching {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(value.trim()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
