package dev.flipperdroid.ir

import java.util.UUID

/**
 * Satu tombol IR.
 *
 * - protocol = "RAW"  -> [raw] berisi durasi bergantian (positif = carrier ON, negatif = OFF)
 * - protocol lainnya   -> [address] + [command] di-encode oleh [IrEncoder]
 */
data class IrButton(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String = "NEC",
    val address: Int = 0,
    val command: Int = 0,
    val frequency: Int = 38000,
    val raw: IntArray? = null,
    val source: String = ""
) {

    fun addressHex(): String = "0x%02X".format(address and 0xFF)

    fun commandHex(): String = "0x%02X".format(command and 0xFF)

    fun signature(): String =
        "$name|$protocol|$address|$command|${raw?.joinToString(",") ?: ""}"

    fun describe(): String = when {
        protocol.equals("RAW", ignoreCase = true) ->
            "RAW • ${frequency / 1000} kHz • ${raw?.size ?: 0} step"
        else ->
            "$protocol ${addressHex()} ${commandHex()} • ${frequency / 1000} kHz"
    }
}
