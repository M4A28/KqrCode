package com.mohammed.mosa.qrscanner.generate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.mohammed.mosa.qrscanner.R

/**
 * Social platforms recognized by URL, with brand glyph + color for the
 * logo-overlay feature in the generator.
 */
enum class SocialPlatform(
    val domains: List<String>,
    val iconRes: Int,
    val brandColor: Int,
) {
    INSTAGRAM(listOf("instagram.com"), R.drawable.ic_brand_instagram, 0xFFE4405F.toInt()),
    FACEBOOK(listOf("facebook.com", "fb.com", "fb.me"), R.drawable.ic_brand_facebook, 0xFF1877F2.toInt()),
    WHATSAPP(listOf("whatsapp.com", "wa.me"), R.drawable.ic_brand_whatsapp, 0xFF25D366.toInt()),
    X(listOf("x.com", "twitter.com", "t.co"), R.drawable.ic_brand_x, 0xFF14171A.toInt()),
    TIKTOK(listOf("tiktok.com"), R.drawable.ic_brand_tiktok, 0xFF14171A.toInt()),
    YOUTUBE(listOf("youtube.com", "youtu.be"), R.drawable.ic_brand_youtube, 0xFFFF0000.toInt()),
    TELEGRAM(listOf("t.me", "telegram.me", "telegram.dog"), R.drawable.ic_brand_telegram, 0xFF26A5E4.toInt()),
    SNAPCHAT(listOf("snapchat.com"), R.drawable.ic_brand_snapchat, 0xFF14171A.toInt()),
    LINKEDIN(listOf("linkedin.com", "lnkd.in"), R.drawable.ic_brand_linkedin, 0xFF0A66C2.toInt()),
    PINTEREST(listOf("pinterest.com", "pin.it"), R.drawable.ic_brand_pinterest, 0xFFBD081C.toInt()),
    GITHUB(listOf("github.com"), R.drawable.ic_brand_github, 0xFF181717.toInt()),
    SPOTIFY(listOf("spotify.com", "spoti.fi"), R.drawable.ic_brand_spotify, 0xFF1DB954.toInt()),
    MESSENGER(listOf("m.me", "messenger.com"), R.drawable.ic_brand_messenger, 0xFF00B2FF.toInt()),
    DISCORD(listOf("discord.com", "discord.gg", "discordapp.com"), R.drawable.ic_brand_discord, 0xFF5865F2.toInt()),
    REDDIT(listOf("reddit.com", "redd.it"), R.drawable.ic_brand_reddit, 0xFFFF4500.toInt()),
    TWITCH(listOf("twitch.tv"), R.drawable.ic_brand_twitch, 0xFF9146FF.toInt());

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** Detects a platform from any URL/text whose host matches a known domain. */
        fun detect(content: String): SocialPlatform? {
            val t = content.trim().lowercase()
            val host = (if (t.contains("://")) t.substringAfter("://") else t)
                .substringBefore('/')
                .takeIf { it.contains('.') } ?: return null
            return entries.firstOrNull { p ->
                p.domains.any { d -> host == d || host.endsWith(".$d") }
            }
        }
    }
}

object SocialBranding {

    /** Stamped under every generated code image. */
    const val FOOTER_TEXT = "mohammedpro.vercel.app"

    fun logoDrawable(context: Context, platform: SocialPlatform): Drawable =
        ContextCompat.getDrawable(context, platform.iconRes)!!
            .mutate()
            .apply { setTint(platform.brandColor) }

    /** Rasterizes the platform glyph into a square bitmap of [sizePx] px. */
    fun logoBitmap(context: Context, platform: SocialPlatform, sizePx: Int): Bitmap {
        val drawable = logoDrawable(context, platform)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
