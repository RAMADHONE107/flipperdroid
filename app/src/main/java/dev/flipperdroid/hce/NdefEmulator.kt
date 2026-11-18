package dev.flipperdroid.hce

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.util.Base64
import org.json.JSONArray

/**
 * Menyimpan NDEF message yang diemulasikan + membangun file
 * Capability Container (CC) dan NDEF file sesuai NFC Forum
 * "Type 4 Tag Operation" v2.0.
 *
 * Sumber kebenaran sekarang adalah daftar [NdefItem] (bisa banyak record).
 * Byte message lama tetap disimpan untuk kompatibilitas dan untuk jalur
 * tulis-balik dari reader (UPDATE BINARY).
 */
object NdefEmulator {

    const val AID_HEX = "D2760000850101"

    val AID: ByteArray = byteArrayOf(
        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
    )

    /** Ukuran maksimum isi NDEF (tanpa 2 byte NLEN). */
    const val MAX_NDEF_SIZE = 0x0FFE

    private const val PREFS = "flipperdroid_hce"
    private const val KEY_MESSAGE = "message"
    private const val KEY_RECORDS = "records"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var cachedMessage: ByteArray? = null

    @Volatile
    private var cachedEnabled: Boolean? = null

    /**
     * Capability Container (15 byte, file E103).
     * CCLEN | mapping version | MLe | MLc | TLV NDEF File Control
     */
    fun capabilityContainer(): ByteArray = byteArrayOf(
        0x00, 0x0F,
        0x20,
        0x00, 0xFF.toByte(),
        0x00, 0xFF.toByte(),
        0x04, 0x06,
        0xE1.toByte(), 0x04,
        ((MAX_NDEF_SIZE shr 8) and 0xFF).toByte(),
        (MAX_NDEF_SIZE and 0xFF).toByte(),
        0x00,
        0x00
    )

    /** File E104: [NLEN 2 byte][NDEF message][padding 0]. */
    fun fileFromMessage(message: ByteArray?): ByteArray {
        val file = ByteArray(MAX_NDEF_SIZE + 2)
        val length = message?.size ?: 0
        val safeLength = length.coerceIn(0, MAX_NDEF_SIZE)
        file[0] = ((safeLength shr 8) and 0xFF).toByte()
        file[1] = (safeLength and 0xFF).toByte()
        if (message != null && safeLength > 0) {
            System.arraycopy(message, 0, file, 2, safeLength)
        }
        return file
    }

    fun textMessage(text: String): ByteArray =
        NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text))).toByteArray()

    /** Gabungkan beberapa item jadi satu NDEF message. null kalau kosong. */
    fun buildBytes(items: List<NdefItem>): ByteArray? {
        val records = items.mapNotNull { it.toRecord() }
        if (records.isEmpty()) return null
        return try {
            NdefMessage(records.toTypedArray()).toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    fun records(context: Context): List<NdefItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECORDS, null)
        if (raw != null) return parseItems(raw)

        // Migrasi dari versi 0.3 (satu message byte).
        val legacy = message(context) ?: return emptyList()
        val msg = try {
            NdefMessage(legacy)
        } catch (e: Exception) {
            null
        }
        return NdefItem.fromMessage(msg)
    }

    /**
     * Simpan daftar item dan langsung generate byte message-nya.
     * @return ukuran message dalam byte, atau -1 kalau melebihi [MAX_NDEF_SIZE].
     */
    fun setRecords(context: Context, items: List<NdefItem>): Int {
        val bytes = buildBytes(items)
        if (bytes != null && bytes.size > MAX_NDEF_SIZE) return -1

        cachedMessage = bytes
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_RECORDS, items.toJson())
        editor.remove(KEY_MESSAGE)
        editor.apply()
        return bytes?.size ?: 0
    }

    fun message(context: Context): ByteArray? {
        cachedMessage?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.getString(KEY_RECORDS, null)?.let { raw ->
            val bytes = buildBytes(parseItems(raw))
            cachedMessage = bytes
            return bytes
        }

        val raw = prefs.getString(KEY_MESSAGE, null) ?: return null
        val bytes = try {
            Base64.decode(raw, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
        cachedMessage = bytes
        return bytes
    }

    /**
     * Jalur lama: simpan byte message mentah (dipakai saat reader menulis
     * ke tag lewat UPDATE BINARY).
     */
    fun setMessage(context: Context, message: ByteArray?) {
        cachedMessage = message
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (message == null) {
            editor.remove(KEY_MESSAGE)
            editor.remove(KEY_RECORDS)
        } else {
            editor.remove(KEY_RECORDS)
            editor.putString(KEY_MESSAGE, Base64.encodeToString(message, Base64.NO_WRAP))
        }
        editor.apply()
    }

    fun isEnabled(context: Context): Boolean {
        cachedEnabled?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getBoolean(KEY_ENABLED, false)
        cachedEnabled = value
        return value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        cachedEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private fun List<NdefItem>.toJson(): String {
        val array = JSONArray()
        forEach { array.put(it.toJson()) }
        return array.toString()
    }

    private fun parseItems(raw: String): List<NdefItem> = try {
        val array = JSONArray(raw)
        val list = mutableListOf<NdefItem>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val kind = try {
                NdefKind.valueOf(o.optString("kind", NdefKind.Text.name))
            } catch (e: Exception) {
                NdefKind.Text
            }
            list.add(
                NdefItem(
                    kind = kind,
                    primary = o.optString("primary", ""),
                    secondary = o.optString("secondary", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}
