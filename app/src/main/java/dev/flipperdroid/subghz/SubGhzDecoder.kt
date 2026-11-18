package dev.flipperdroid.subghz

import kotlin.math.abs

/**
 * Decoder protokol Sub-GHz dari durasi pulse mentah hasil capture.
 *
 * Parameter mengikuti implementasi open-source Flipper Zero:
 *  - Princeton : te 390/1170 us, 24 bit, sync ~ te*36
 *  - CAME      : te 320/640 us, 12/18/24/25/42 bit, header ~ te*47..76
 *  - Keeloq    : te 400/800 us, 64 bit (+2 status), preamble 400/400
 *
 * Catatan penting: decoder ini heuristik. Semua frame tetap bisa di-replay
 * sebagai raw, jadi salah label tidak membuat sinyalnya hilang.
 */
object SubGhzDecoder {

    data class Decode(
        val protocol: String,
        val bits: Int,
        val code: Long,
        val serial: Long? = null,
        val button: Int? = null,
        val extra: String? = null,
        val note: String? = null
    ) {
        fun headline(): String = buildString {
            append(protocol)
            append(' ')
            append(bits)
            append("bit")
            if (serial != null) append(" • Sn:").append("%05X".format(serial))
            if (button != null) append(" • Btn:").append(button)
            if (extra != null) append(" • ").append(extra)
        }

        fun detail(): String = buildString {
            appendLine("Protocol : $protocol")
            appendLine("Bits     : $bits")
            append("Code     : 0x")
            append("%0${((bits + 3) / 4)}X".format(code))
            if (serial != null) appendLine().append("Serial   : 0x%05X".format(serial))
            if (button != null) appendLine().append("Button   : $button")
            if (extra != null) appendLine().append(extra)
            if (note != null) appendLine().append("Note     : $note")
        }
    }

    private val CAME_BITS = setOf(12, 18, 24, 25, 42)

    fun decodeCsv(csv: String): Decode? {
        val values = parse(csv)
        if (values.size < 24) return null

        val sync = values.indexOfFirst { it >= 3500 }
        if (sync < 1) return null

        val pre = values.subList(0, sync)
        val post = values.subList(sync + 1, values.size)
        if (post.size < 20) return null

        return keeloq(pre, post) ?: princetonOrCame(post)
    }

    // ------------------------------------------------------------- Keeloq

    private fun keeloq(pre: List<Int>, post: List<Int>): Decode? {
        // Preamble HCS301: deretan pulse ~400 us sebelum header ~4000 us.
        val preambleLike = pre.count { it in 240..620 }
        if (preambleLike < 6) return null
        if (post.size < 128) return null

        val parsed = decodePwm(post, 0, 400, 800, onePatternIsShortLong = true, maxBits = 64)
            ?: return null
        if (parsed.second < 64) return null

        val data = parsed.first
        val reversed = reverseBits(data, 64)
        val fix = reversed ushr 32
        val serial = fix and 0x0FFFFFFF
        val button = ((fix ushr 28) and 0x0F).toInt()
        val hop = reversed and 0xFFFFFFFFL

        return Decode(
            protocol = "Keeloq",
            bits = 64,
            code = data,
            serial = serial,
            button = button,
            extra = "Hop 0x%08X".format(hop),
            note = "Hop code terenkripsi (butuh manufacturer key untuk decrypt)."
        )
    }

    // --------------------------------------------------- Princeton / CAME

    private fun princetonOrCame(post: List<Int>): Decode? {
        val teShort = estimateShort(post) ?: return null
        val teLong = estimateLong(post, teShort)
        val ratio = teLong.toDouble() / teShort

        // CAME punya start bit ekstra sebelum data.
        val came = decodePwm(post, 1, teShort, teLong, onePatternIsShortLong = false, maxBits = 42)
        if (came != null && came.second in CAME_BITS && ratio < 2.5) {
            return Decode(
                protocol = "CAME",
                bits = came.second,
                code = came.first
            )
        }

        val princeton = decodePwm(post, 0, teShort, teLong, onePatternIsShortLong = false, maxBits = 24)
        if (princeton != null && princeton.second == 24) {
            val code = princeton.first
            return Decode(
                protocol = "Princeton",
                bits = 24,
                code = code,
                serial = code ushr 4,
                button = (code and 0x0F).toInt()
            )
        }

        if (came != null && came.second in CAME_BITS) {
            return Decode("CAME", came.second, came.first)
        }
        if (princeton != null && princeton.second in 20..28) {
            return Decode(
                protocol = "Princeton?",
                bits = princeton.second,
                code = princeton.first,
                note = "Jumlah bit tidak persis 24, kemungkinan noise di awal/akhir."
            )
        }
        return null
    }

    // ------------------------------------------------------------- Helper

    /**
     * Baca pasangan (mark, space) menjadi bit. PWM:
     *  - 1 = short mark + long space (Keeloq)
     *  - 0 = short mark + long space (Princeton/CAME)
     * Flag [onePatternIsShortLong] membalik arti tersebut.
     */
    private fun decodePwm(
        values: List<Int>,
        start: Int,
        teShort: Int,
        teLong: Int,
        onePatternIsShortLong: Boolean,
        maxBits: Int
    ): Pair<Long, Int>? {
        var code = 0L
        var bits = 0
        var index = start

        while (index + 1 < values.size && bits < maxBits) {
            val a = level(values[index], teShort, teLong)
            val b = level(values[index + 1], teShort, teLong)
            if (a == null || b == null || a == b) {
                break
            }
            val shortLong = a == 0
            val bit = if (shortLong == onePatternIsShortLong) 1 else 0
            code = (code shl 1) or bit.toLong()
            bits++
            index += 2
        }

        return if (bits > 0) code to bits else null
    }

    /** 0 = short, 1 = long, null = tidak masuk toleransi. */
    private fun level(duration: Int, teShort: Int, teLong: Int): Int? {
        val shortDiff = abs(duration - teShort)
        val longDiff = abs(duration - teLong)
        val shortOk = shortDiff <= maxOf(120, teShort / 2)
        val longOk = longDiff <= maxOf(150, teLong / 4)
        return when {
            shortOk && longOk -> if (shortDiff <= longDiff) 0 else 1
            shortOk -> 0
            longOk -> 1
            else -> null
        }
    }

    private fun estimateShort(values: List<Int>): Int? {
        val shorts = values.filter { it in 150..900 }.sorted()
        if (shorts.isEmpty()) return null
        return shorts[shorts.size / 2]
    }

    private fun estimateLong(values: List<Int>, teShort: Int): Int {
        val longs = values.filter { it >= teShort * 1.5 && it <= teShort * 4 }.sorted()
        if (longs.isEmpty()) return teShort * 2
        return longs[longs.size / 2]
    }

    private fun parse(csv: String): List<Int> {
        if (csv.isBlank()) return emptyList()
        val out = ArrayList<Int>(256)
        for (token in csv.split(',')) {
            if (out.size >= 512) break
            val value = token.trim().toIntOrNull() ?: continue
            if (value == 0) continue
            out.add(abs(value))
        }
        return out
    }

    private fun reverseBits(value: Long, bits: Int): Long {
        var source = value
        var result = 0L
        repeat(bits) {
            result = (result shl 1) or (source and 1L)
            source = source ushr 1
        }
        return result
    }
}
