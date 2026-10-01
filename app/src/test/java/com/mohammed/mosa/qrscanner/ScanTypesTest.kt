package com.mohammed.mosa.qrscanner

import com.mohammed.mosa.qrscanner.util.ScanTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTypesTest {

    // ---------- isLink ----------

    @Test
    fun `links detected in common forms`() {
        assertTrue(ScanTypes.isLink("https://example.com/page?q=1"))
        assertTrue(ScanTypes.isLink("http://example.com"))
        assertTrue(ScanTypes.isLink("example.com"))
        assertTrue(ScanTypes.isLink("sub.domain.example.co.uk/path"))
        assertTrue(ScanTypes.isLink("EXAMPLE.COM"))
        assertTrue(ScanTypes.isLink("https://example.com:8080/x"))
        assertTrue(ScanTypes.isLink("  example.com  "))
    }

    @Test
    fun `non links rejected`() {
        assertFalse(ScanTypes.isLink("hello world with spaces"))
        assertFalse(ScanTypes.isLink("nodot"))
        assertFalse(ScanTypes.isLink("WIFI:T:WPA;S:home;P:pass;;"))
        assertFalse(ScanTypes.isLink("tel:+15551234"))
        assertFalse(ScanTypes.isLink("just some text"))
        assertFalse(ScanTypes.isLink(""))
    }

    // ---------- describe ----------

    @Test
    fun `payload prefixes map to friendly types`() {
        assertEquals("Wi-Fi", ScanTypes.describe("WIFI:T:WPA;S:x;;"))
        assertEquals("Contact", ScanTypes.describe("BEGIN:VCARD\nFN:John\nEND:VCARD"))
        assertEquals("Contact", ScanTypes.describe("MECARD:N:Doe,John;TEL:1;;"))
        assertEquals("Phone", ScanTypes.describe("tel:+15551234"))
        assertEquals("SMS", ScanTypes.describe("SMSTO:+15551234:hi"))
        assertEquals("Email", ScanTypes.describe("mailto:a@b.com"))
        assertEquals("Location", ScanTypes.describe("geo:51.5,-0.12"))
        assertEquals("OTP", ScanTypes.describe("otpauth://totp/x?secret=y"))
        assertEquals("Play Store", ScanTypes.describe("market://details?id=x"))
        assertEquals("Link", ScanTypes.describe("https://example.com"))
        assertEquals("Text", ScanTypes.describe("plain text"))
    }

    // ---------- parseWifi ----------

    @Test
    fun `wifi payload with password parses`() {
        val c = ScanTypes.parseWifi("WIFI:T:WPA;S:home-net;P:secret12;;")!!
        assertEquals("home-net", c.ssid)
        assertEquals("secret12", c.password)
        assertEquals("WPA", c.security)
    }

    @Test
    fun `open network has null password`() {
        val c = ScanTypes.parseWifi("WIFI:T:nopass;S:CoffeeShop;;")!!
        assertEquals("CoffeeShop", c.ssid)
        assertNull(c.password)
    }

    @Test
    fun `escaped separators are unescaped`() {
        val c = ScanTypes.parseWifi("""WIFI:T:WPA;S:net\;work;P:a\,b;;""")!!
        assertEquals("net;work", c.ssid)
        assertEquals("a,b", c.password)
    }

    @Test
    fun `missing ssid returns null`() {
        assertNull(ScanTypes.parseWifi("WIFI:T:WPA;;"))
        assertNull(ScanTypes.parseWifi("https://example.com"))
        assertNull(ScanTypes.parseWifi(""))
    }

    // ---------- parseContact ----------

    @Test
    fun `vcard fields are extracted`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nN:Doe;John;;;\nFN:John Doe\n" +
                "ORG:Acme\nTEL;TYPE=CELL:+15551234\nEMAIL:john@acme.com\nEND:VCARD"
        val c = ScanTypes.parseContact(vcard)!!
        assertEquals("John Doe", c.name)
        assertEquals("+15551234", c.phone)
        assertEquals("john@acme.com", c.email)
        assertEquals("Acme", c.org)
    }

    @Test
    fun `vcard without FN falls back to N field`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nN:Doe;John;;;\nEND:VCARD"
        assertEquals("John Doe", ScanTypes.parseContact(vcard)!!.name)
    }

    @Test
    fun `mecard fields are extracted`() {
        val c = ScanTypes.parseContact("MECARD:N:Doe,John;TEL:+15551234;EMAIL:john@acme.com;;")!!
        assertEquals("John Doe", c.name)
        assertEquals("+15551234", c.phone)
        assertEquals("john@acme.com", c.email)
    }

    @Test
    fun `non contact returns null`() {
        assertNull(ScanTypes.parseContact("https://example.com"))
        assertNull(ScanTypes.parseContact("WIFI:T:WPA;S:x;;"))
        assertNull(ScanTypes.parseContact("plain text"))
    }
}
