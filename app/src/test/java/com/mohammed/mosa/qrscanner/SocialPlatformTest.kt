package com.mohammed.mosa.qrscanner

import com.mohammed.mosa.qrscanner.generate.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialPlatformTest {

    @Test
    fun `detects platforms from urls`() {
        assertEquals(SocialPlatform.INSTAGRAM, SocialPlatform.detect("https://instagram.com/mohammed"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialPlatform.detect("instagram.com/p/123"))
        assertEquals(SocialPlatform.WHATSAPP, SocialPlatform.detect("https://wa.me/201234567"))
        assertEquals(SocialPlatform.WHATSAPP, SocialPlatform.detect("https://chat.whatsapp.com/abc"))
        assertEquals(SocialPlatform.X, SocialPlatform.detect("https://x.com/user"))
        assertEquals(SocialPlatform.X, SocialPlatform.detect("https://twitter.com/user"))
        assertEquals(SocialPlatform.YOUTUBE, SocialPlatform.detect("https://youtu.be/abc"))
        assertEquals(SocialPlatform.TELEGRAM, SocialPlatform.detect("https://t.me/channel"))
        assertEquals(SocialPlatform.SPOTIFY, SocialPlatform.detect("https://open.spotify.com/track/1"))
        assertEquals(SocialPlatform.GITHUB, SocialPlatform.detect("https://github.com/M4A28"))
    }

    @Test
    fun `subdomains match but lookalike hosts do not`() {
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.detect("https://www.facebook.com/page"))
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.detect("https://m.facebook.com/page"))
        assertNull(SocialPlatform.detect("https://notinstagram.com"))
        assertNull(SocialPlatform.detect("https://example.com"))
    }

    @Test
    fun `non urls return null`() {
        assertNull(SocialPlatform.detect("hello world"))
        assertNull(SocialPlatform.detect("WIFI:T:WPA;S:x;;"))
        assertNull(SocialPlatform.detect(""))
    }
}
