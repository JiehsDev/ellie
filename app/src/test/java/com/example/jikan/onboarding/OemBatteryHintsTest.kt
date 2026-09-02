package com.example.jikan.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemBatteryHintsTest {
    @Test
    fun `xiaomi oppo and vivo get an autostart deep link`() {
        assertEquals(
            AutostartTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            OemBatteryHints.autostartTargetFor("Xiaomi"),
        )
        assertTrue(OemBatteryHints.showsAutostartNote("Oppo"))
        assertTrue(OemBatteryHints.showsAutostartNote("vivo"))
    }

    @Test
    fun `manufacturer matching is case insensitive`() {
        assertTrue(OemBatteryHints.showsAutostartNote("XIAOMI"))
        assertTrue(OemBatteryHints.showsAutostartNote("  Vivo  "))
    }

    @Test
    fun `other manufacturers get no autostart note`() {
        assertNull(OemBatteryHints.autostartTargetFor("Google"))
        assertFalse(OemBatteryHints.showsAutostartNote("Samsung"))
    }
}
