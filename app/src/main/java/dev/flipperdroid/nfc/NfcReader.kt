package dev.flipperdroid.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

data class TagInfo(
    val uid: String,
    val techs: List<String>,
    val maxSize: Int,
    val records: List<String>,
    val readableNdef: Boolean
)

object NfcReader {

    fun read(tag: Tag): TagInfo {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val ndef = try {
            Ndef.get(tag)
        } catch (e: Exception) {
            null
        }
        var message = try {
            ndef?.cachedNdefMessage
        } catch (e: Exception) {
            null
        }
        // Sebagian reader mode / tag tidak mengisi cache, jadi baca langsung.
        if (message == null && ndef != null) {
            message = try {
                ndef.connect()
                val direct = ndef.ndefMessage
                ndef.close()
                direct
            } catch (e: Exception) {
                try {
                    ndef.close()
                } catch (ignored: Exception) {
                    // diabaikan
                }
                null
            }
        }
        val records = message?.records?.map { describe(it) } ?: emptyList()
        return TagInfo(
            uid = tag.id?.joinToString(":") { byte -> "%02X".format(byte) } ?: "-",
            techs = techs,
            maxSize = try {
                ndef?.maxSize ?: 0
            } catch (e: Exception) {
                0
            },
            records = records,
            readableNdef = message != null
        )
    }

    fun writeText(tag: Tag, text: String): String {
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                ndef.writeNdefMessage(message)
                ndef.close()
                "Tertulis ke tag (NDEF)."
            } else {
                val formatter = NdefFormatable.get(tag)
                if (formatter != null) {
                    formatter.connect()
                    formatter.format(message)
                    formatter.close()
                    "Tag diformat & ditulis."
                } else {
                    "Tag ini tidak mendukung NDEF."
                }
            }
        } catch (e: Exception) {
            "Gagal tulis: ${e.message}"
        }
    }

    private fun describe(record: NdefRecord): String {
        return describeRecord(record)
    }

    fun describeMessage(message: NdefMessage?): List<String> =
        message?.records?.map { describeRecord(it) } ?: emptyList()

    /** Ambil teks dari record TEXT pertama, kalau ada. */
    fun textOf(message: NdefMessage?): String? {
        val record = message?.records?.firstOrNull() ?: return null
        if (String(record.type, Charsets.UTF_8) != "T") return null
        val raw = record.payload
        if (raw.isEmpty()) return null
        val langLength = raw[0].toInt() and 0x3F
        val start = 1 + langLength
        return if (start < raw.size) String(raw, start, raw.size - start, Charsets.UTF_8) else null
    }

    fun describeRecord(record: NdefRecord): String {
        val type = try {
            String(record.type, Charsets.UTF_8)
        } catch (e: Exception) {
            "?"
        }
        val payload = try {
            val raw = record.payload
            if (raw.size > 2 && (raw[0].toInt() and 0xFF) in 0..0x7F) {
                val langLen = raw[0].toInt() and 0x3F
                val start = 1 + langLen
                if (start < raw.size) {
                    String(raw, start, raw.size - start, Charsets.UTF_8)
                } else {
                    "(${raw.size} byte)"
                }
            } else {
                String(raw, Charsets.UTF_8)
                    .filter { it.isLetterOrDigit() || it in ":/.-_?=&% " }
                    .take(120)
            }
        } catch (e: Exception) {
            "?"
        }
        return "${type.ifBlank { "data" }}: $payload"
    }
}
