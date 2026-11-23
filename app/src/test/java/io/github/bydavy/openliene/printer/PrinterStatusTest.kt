package io.github.bydavy.openliene.printer

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterStatusTest {
    @Test
    fun labelsKnownAlertsAndFallsBackForUnknownCodes() {
        assertEquals("Paper empty — load a 2×3 cartridge", alertLabel(5402))
        assertEquals("Printer door is open", alertLabel(5506))
        assertEquals("Battery low — connect power", alertLabel(9005))
        assertEquals("Printer alert", alertLabel(1234))
    }

    @Test
    fun parsesAlertCodeLists() {
        assertEquals(listOf(5402, 9005), parseAlerts("5402,0,9005"))
        assertEquals(emptyList<Int>(), parseAlerts(""))
        assertEquals(emptyList<Int>(), parseAlerts("0, 0"))
    }

    @Test
    fun parsesStatusAndInfoResponses() {
        val status = parseStatus(
            JSONArray()
                .put("20")
                .put("0")
                .put("5402,0")
                .put("")
                .put("4"),
        )
        requireNotNull(status)
        assertEquals(20, status.state)
        assertEquals(listOf(5402), status.alerts)
        assertEquals(4, status.battery)
        assertTrue(status.hasAlert)
        assertEquals("Paper empty — load a 2×3 cartridge", status.headline())

        val info = parseInfo(
            JSONArray()
                .put("Liene N200 Pro")
                .put("")
                .put("SN123456")
                .put("")
                .put("1.2.3"),
            "AA:BB:CC:DD:EE:FF",
        )
        requireNotNull(info)
        assertEquals("Liene N200 Pro", info.model)
        assertEquals("SN123456", info.serial)
        assertEquals("1.2.3", info.firmware)
        assertEquals("AA:BB:CC:DD:EE:FF", info.bluetooth)
    }

    @Test
    fun rejectsMalformedStatusResponses() {
        assertNull(parseStatus("not an array"))
        assertNull(parseStatus(JSONArray().put("not-a-number")))
        assertNull(parseInfo("not an array", "AA:BB"))
    }

    @Test
    fun reportsHeadlinesForPrinterStates() {
        val ready = PrinterStatus(state = 20, subState = 0, alerts = emptyList(), battery = 4)
        val printing = ready.copy(state = 40)
        val error = ready.copy(state = 60)
        val unknown = ready.copy(state = 999)

        assertEquals("Ready", ready.headline())
        assertEquals("Printing", printing.headline())
        assertEquals("Printer error", error.headline())
        assertFalse(ready.hasAlert)
        assertTrue(error.hasAlert)
        assertEquals("Unknown state", unknown.headline())
    }

    @Test
    fun labelsJobStates() {
        assertEquals("Printing", jobStateLabel(3))
        assertEquals("Aborted", jobStateLabel(7))
        assertEquals("Canceled", jobStateLabel(8))
        assertEquals("Finished", jobStateLabel(9))
        assertEquals("Pending", jobStateLabel(4))
        assertEquals("Pending", jobStateLabel(null))
    }
}
