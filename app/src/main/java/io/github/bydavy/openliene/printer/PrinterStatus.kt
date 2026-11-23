package io.github.bydavy.openliene.printer

import org.json.JSONArray
import org.json.JSONObject

data class PrinterStatus(
    val state: Int,
    val subState: Int,
    val alerts: List<Int>,
    val battery: Int?,
) {
    val hasAlert: Boolean
        get() = alerts.isNotEmpty() || state == 60

    fun headline(): String = when {
        alerts.isNotEmpty() -> alertLabel(alerts.first())
        state == 10 -> "Initializing"
        state == 20 -> "Ready"
        state == 30 -> "Sleeping"
        state == 40 -> "Printing"
        state == 50 -> "Powered off"
        state == 60 -> "Printer error"
        else -> "Unknown state"
    }
}

data class DeviceInfo(
    val model: String,
    val serial: String,
    val firmware: String,
    val bluetooth: String,
)

fun parseStatus(result: Any?): PrinterStatus? {
    val values = result as? JSONArray ?: return null
    val state = values.optString(0).toIntOrNull() ?: return null
    val sub = values.optString(1).toIntOrNull() ?: 0
    return PrinterStatus(
        state = state,
        subState = sub,
        alerts = parseAlerts(values.optString(2)),
        battery = values.optString(4).toIntOrNull(),
    )
}

fun parseInfo(result: Any?, bluetoothAddress: String): DeviceInfo? {
    val values = result as? JSONArray ?: return null
    fun cell(index: Int): String =
        values.optString(index).takeIf { it.isNotBlank() } ?: "—"
    return DeviceInfo(
        model = cell(0),
        serial = cell(2),
        firmware = cell(4),
        bluetooth = bluetoothAddress.ifBlank { "—" },
    )
}

fun parseAlerts(value: String): List<Int> =
    Regex("""\d+""")
        .findAll(value)
        .mapNotNull { it.value.toIntOrNull() }
        .filter { it != 0 }
        .toList()

fun alertLabel(code: Int): String = when (code) {
    5402 -> "Paper empty — load a 2×3 cartridge"
    5403 -> "Paper jam or paper error"
    5414 -> "Paper was not picked up"
    5417 -> "Printed paper was not removed"
    5418 -> "Paper detection error"
    5419, 5420 -> "Paper feed error"
    5506 -> "Printer door is open"
    6002 -> "Printer needs a reboot"
    8101 -> "Ribbon empty — replace the cartridge"
    8103 -> "Ribbon jam"
    8106, 8199 -> "Ribbon error"
    9002 -> "Battery too hot or shut down"
    9004 -> "Battery temperature high"
    9005 -> "Battery low — connect power"
    else -> "Printer alert"
}

fun jobStateLabel(state: Int?): String = when (state) {
    3 -> "Printing"
    7 -> "Aborted"
    8 -> "Canceled"
    9 -> "Finished"
    else -> "Pending"
}

/**
 * The printer reports battery as a 0–4 level (the vendor battery painter uses
 * 0/25/50/75/100% fill), not a percentage. `4` means full.
 */
fun batteryLabel(level: Int): String = when (level) {
    0 -> "Battery empty"
    1 -> "Battery low"
    2 -> "Battery 50%"
    3 -> "Battery 75%"
    4 -> "Battery full"
    else -> "Battery $level/4"
}

fun isBatteryLow(level: Int): Boolean = level <= 1
