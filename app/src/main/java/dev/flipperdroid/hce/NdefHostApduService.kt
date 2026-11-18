package dev.flipperdroid.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Emulasi NFC Forum Type 4 Tag (NDEF) lewat Host Card Emulation.
 *
 * Reader mengirim APDU ISO 7816-4:
 *   SELECT AID D2760000850101
 *   SELECT file E103 (Capability Container) -> READ BINARY
 *   SELECT file E104 (NDEF)                 -> READ BINARY / UPDATE BINARY
 */
class NdefHostApduService : HostApduService() {

    private var selected = SELECT_NONE
    private var ndefFile: ByteArray = NdefEmulator.fileFromMessage(null)

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (!NdefEmulator.isEnabled(this)) return SW_FILE_NOT_FOUND
        val apdu = commandApdu ?: return SW_WRONG_LENGTH
        if (apdu.size < 4) return SW_WRONG_LENGTH
        return try {
            handle(apdu)
        } catch (e: Exception) {
            SW_UNKNOWN
        }
    }

    override fun onDeactivated(reason: Int) {
        selected = SELECT_NONE
    }

    private fun handle(apdu: ByteArray): ByteArray {
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        return when (ins) {
            INS_SELECT -> select(apdu, p1)
            INS_READ_BINARY -> readBinary(p1, p2, apdu)
            INS_UPDATE_BINARY -> updateBinary(p1, p2, apdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int): ByteArray {
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH

        return if (p1 == 0x04) {
            // SELECT by DF name
            val name = apdu.copyOfRange(5, 5 + lc)
            if (name.contentEquals(NdefEmulator.AID)) {
                // Selalu ambil versi terbaru dari cache/prefs.
                ndefFile = NdefEmulator.fileFromMessage(NdefEmulator.message(this))
                selected = SELECT_APP
                SW_OK
            } else {
                SW_FILE_NOT_FOUND
            }
        } else {
            // SELECT by file id
            if (lc < 2 || apdu.size < 7) return SW_WRONG_LENGTH
            val fileId = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
            if (selected == SELECT_NONE) return SW_CONDITIONS_NOT_SATISFIED
            when (fileId) {
                FILE_CC -> {
                    selected = SELECT_CC
                    SW_OK
                }
                FILE_NDEF -> {
                    selected = SELECT_NDEF
                    SW_OK
                }
                else -> SW_FILE_NOT_FOUND
            }
        }
    }

    private fun readBinary(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        val source = when (selected) {
            SELECT_CC -> NdefEmulator.capabilityContainer()
            SELECT_NDEF -> ndefFile
            else -> return SW_CONDITIONS_NOT_SATISFIED
        }

        val offset = (p1 shl 8) or p2
        if (offset > source.size) return SW_WRONG_P1P2

        val le = if (apdu.size > 4) {
            (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it }
        } else {
            256
        }

        val end = minOf(offset + le, source.size)
        val data = source.copyOfRange(offset, end)
        return data + SW_OK
    }

    private fun updateBinary(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        if (selected != SELECT_NDEF) return SW_CONDITIONS_NOT_SATISFIED
        if (apdu.size < 5) return SW_WRONG_LENGTH

        val offset = (p1 shl 8) or p2
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
        if (offset + lc > ndefFile.size) return SW_WRONG_P1P2

        System.arraycopy(apdu, 5, ndefFile, offset, lc)

        val nlen = ((ndefFile[0].toInt() and 0xFF) shl 8) or (ndefFile[1].toInt() and 0xFF)
        if (nlen in 0..(ndefFile.size - 2)) {
            NdefEmulator.setMessage(this, ndefFile.copyOfRange(2, 2 + nlen))
        }
        return SW_OK
    }

    private companion object {
        const val INS_SELECT = 0xA4
        const val INS_READ_BINARY = 0xB0
        const val INS_UPDATE_BINARY = 0xD6

        const val FILE_CC = 0xE103
        const val FILE_NDEF = 0xE104

        const val SELECT_NONE = 0
        const val SELECT_APP = 1
        const val SELECT_CC = 2
        const val SELECT_NDEF = 3

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)
        val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00)
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)
        val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x81.toByte())
        val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00)
    }
}
