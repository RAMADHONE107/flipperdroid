package dev.flipperdroid.ir

import kotlin.math.abs

/**
 * Pembangun pattern IR.
 *
 * Nilai disimpan bertanda: positif = carrier ON (mark), negatif = OFF (space).
 * Bagian OFF di awal/akhir dibuang karena ConsumerIrManager mengharuskan
 * pattern dimulai dengan ON.
 */
class IrPattern {

    private val parts = ArrayList<Int>()

    fun on(micros: Int) = add(micros)

    fun off(micros: Int) = add(-micros)

    private fun add(micros: Int) {
        if (micros == 0) return
        val last = parts.lastOrNull()
        if (last != null && (last > 0) == (micros > 0)) {
            parts[parts.size - 1] = last + micros
        } else {
            parts.add(micros)
        }
    }

    fun build(): IntArray {
        var start = 0
        while (start < parts.size && parts[start] < 0) start++
        var end = parts.size
        while (end > start && parts[end - 1] < 0) end--
        val out = IntArray((end - start).coerceAtLeast(0))
        for (i in start until end) out[i - start] = abs(parts[i])
        return out
    }
}

/**
 * Encoder protokol IR yang umum ada di file .ir (format Flipper / IRDB).
 * Protokol yang belum didukung akan dilaporkan saat impor, bukan diam-diam gagal.
 */
object IrEncoder {

    private const val HZ_NEC = 38000
    private const val HZ_SAMSUNG = 38000
    private const val HZ_SONY = 40000

    fun supports(protocol: String): Boolean = when (protocol.uppercase()) {
        // RC5 sengaja belum didukung: frame-nya diawali level space, sedangkan
        // ConsumerIrManager mewajibkan pattern dimulai dengan mark.
        "NEC", "NECEXT", "SAMSUNG32", "SIRC", "SIRC15", "SIRC20", "RAW" -> true
        else -> false
    }

    fun frequencyFor(button: IrButton): Int = when (button.protocol.uppercase()) {
        "SIRC", "SIRC15", "SIRC20" -> HZ_SONY
        else -> if (button.frequency > 0) button.frequency else HZ_NEC
    }

    /** null = protokol tidak didukung / data kosong. */
    fun build(button: IrButton): IntArray? = when (button.protocol.uppercase()) {
        "NEC" -> nec(button.address and 0xFF, button.command and 0xFF)
        "NECEXT" -> necExtended(button.address, button.command)
        "SAMSUNG32" -> samsung32(button.address and 0xFF, button.command and 0xFF)
        "SIRC" -> sony(button.command and 0x7F, button.address and 0x1F, 12)
        "SIRC15" -> sony(button.command and 0xFF, button.address and 0x7F, 15)
        "SIRC20" -> sony(button.command and 0xFF, button.address and 0xFF, 20)
        "RAW" -> raw(button.raw)
        else -> null
    }

    // ---------------------------------------------------------------- NEC

    fun nec(address: Int, command: Int): IntArray {
        val b = IrPattern()
        b.on(9000)
        b.off(4500)
        necBits(b, address, 8)
        necBits(b, address.inv() and 0xFF, 8)
        necBits(b, command, 8)
        necBits(b, command.inv() and 0xFF, 8)
        b.on(560)
        b.off(20000)
        return b.build()
    }

    /** NECext: address 16 bit + command 8 bit + komplemen command. */
    fun necExtended(address: Int, command: Int): IntArray {
        val b = IrPattern()
        b.on(9000)
        b.off(4500)
        necBits(b, address and 0xFF, 8)
        necBits(b, (address shr 8) and 0xFF, 8)
        necBits(b, command and 0xFF, 8)
        necBits(b, command.inv() and 0xFF, 8)
        b.on(560)
        b.off(20000)
        return b.build()
    }

    private fun necBits(b: IrPattern, value: Int, count: Int) {
        for (i in 0 until count) {
            b.on(560)
            b.off(if ((value shr i) and 1 == 1) 1690 else 560)
        }
    }

    // ------------------------------------------------------------ Samsung

    fun samsung32(address: Int, command: Int): IntArray {
        val b = IrPattern()
        b.on(4500)
        b.off(4500)
        necBits(b, address, 8)
        necBits(b, address, 8)
        necBits(b, command, 8)
        necBits(b, command.inv() and 0xFF, 8)
        b.on(560)
        b.off(20000)
        return b.build()
    }

    // --------------------------------------------------------------- Sony

    /**
     * SIRC: 12 / 15 / 20 bit. Bit dikirim LSB-first:
     * command (7/8) lalu address, lalu extended (4 bit, diisi 0).
     */
    fun sony(command: Int, address: Int, bits: Int): IntArray {
        val b = IrPattern()
        b.on(2400)
        b.off(600)
        val value = (command and 0xFF) or ((address and 0xFF) shl 8)
        for (i in 0 until bits) {
            b.on(600)
            b.off(if ((value shr i) and 1 == 1) 1200 else 600)
        }
        return b.build()
    }

    // ---------------------------------------------------------------- RAW

    /** Replay langsung durasi dari file .ir (positif = mark). */
    fun raw(data: IntArray?): IntArray? {
        if (data == null || data.isEmpty()) return null
        val b = IrPattern()
        for (value in data) {
            if (value > 0) b.on(value) else if (value < 0) b.off(-value)
        }
        val pattern = b.build()
        return if (pattern.size < 2) null else pattern
    }
}
