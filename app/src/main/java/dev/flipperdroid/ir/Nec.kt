package dev.flipperdroid.ir

/**
 * Encoder NEC 38 kHz. Pattern dikirim ke ConsumerIrManager dalam mikrodetik
 * (ON, OFF, ON, OFF, ...).
 */
object Nec {

    private const val HDR_ON = 9000
    private const val HDR_OFF = 4500
    private const val BIT_ON = 560
    private const val ZERO_OFF = 560
    private const val ONE_OFF = 1690
    private const val STOP = 560

    fun pattern(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(70)
        out.add(HDR_ON)
        out.add(HDR_OFF)

        // NEC mengirim LSB dulu, lalu komplemen tiap byte.
        addByte(out, address and 0xFF)
        addByte(out, (address shr 8) and 0xFF)
        addByte(out, command and 0xFF)
        addByte(out, command.inv() and 0xFF)

        out.add(STOP)
        out.add(STOP)
        return out.toIntArray()
    }

    private fun addByte(out: MutableList<Int>, value: Int) {
        for (i in 0 until 8) {
            val bit = (value shr i) and 1
            out.add(BIT_ON)
            out.add(if (bit == 1) ONE_OFF else ZERO_OFF)
        }
    }
}
