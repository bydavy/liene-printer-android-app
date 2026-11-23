package io.github.bydavy.openliene.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTest {
    @Test
    fun mapsTheFourLevelBatteryScale() {
        assertEquals("Battery empty", batteryLabel(0))
        assertEquals("Battery low", batteryLabel(1))
        assertEquals("Battery 50%", batteryLabel(2))
        assertEquals("Battery 75%", batteryLabel(3))
        assertEquals("Battery full", batteryLabel(4))
    }

    @Test
    fun onlyEmptyOrLowWarns() {
        assertTrue(isBatteryLow(0))
        assertTrue(isBatteryLow(1))
        assertFalse(isBatteryLow(2))
        assertFalse(isBatteryLow(4))
    }
}
