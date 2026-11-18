package dev.flipperdroid.hce

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.net.Uri

enum class NdefKind { Text, Uri, VCard }

/**
 * Satu record NDEF yang bisa diedit dari UI.
 *
 * - [NdefKind.Text]  -> primary = teks
 * - [NdefKind.Uri]   -> primary = URL/URI
 * - [NdefKind.VCard] -> primary = nama, secondary = nomor telepon
 */
data class NdefItem(
    val kind: NdefKind,
    val primary: String,
    val secondary: String = ""
) {

    fun label(): String = when (kind) {
        NdefKind.Text -> "TEXT"
        NdefKind.Uri -> "URI"
        NdefKind.VCard -> "VCARD"
    }

    fun preview(): String = when (kind) {
        NdefKind.VCard -> buildString {
            append(primary.ifBlank { "(tanpa nama)" })
            if (secondary.isNotBlank()) append(" • $secondary")
        }
        else -> primary.ifBlank { "(kosong)" }
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("kind", kind.name)
        .put("primary", primary)
        .put("secondary", secondary)

    fun toRecord(): NdefRecord? = when (kind) {
        NdefKind.Text ->
            if (primary.isBlank()) null
            else NdefRecord.createTextRecord("en", primary)

        NdefKind.Uri -> {
            val uri = normalizeUri(primary)
            if (uri == null) null else NdefRecord.createUri(uri)
        }

        NdefKind.VCard ->
            if (primary.isBlank() && secondary.isBlank()) null
            else NdefRecord.createMime("text/vcard", vcard().toByteArray(Charsets.UTF_8))
    }

    fun vcard(): String = buildString {
        append("BEGIN:VCARD\r\n")
        append("VERSION:3.0\r\n")
        append("FN:").append(primary.trim()).append("\r\n")
        append("N:").append(primary.trim()).append(";;;;\r\n")
        if (secondary.isNotBlank()) {
            append("TEL;TYPE=CELL:").append(secondary.trim()).append("\r\n")
        }
        append("END:VCARD\r\n")
    }

    companion object {

        fun normalizeUri(raw: String): Uri? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val withScheme = if (text.contains("://") || text.startsWith("mailto:") ||
                text.startsWith("tel:") || text.startsWith("sms:")
            ) {
                text
            } else {
                "https://$text"
            }
            return try {
                Uri.parse(withScheme)
            } catch (e: Exception) {
                null
            }
        }

        fun fromRecord(record: NdefRecord): NdefItem? {
            val type = try {
                String(record.type, Charsets.UTF_8)
            } catch (e: Exception) {
                return null
            }
            return when {
                type == "T" -> {
                    val raw = record.payload
                    if (raw.isEmpty()) return null
                    val langLength = raw[0].toInt() and 0x3F
                    val start = 1 + langLength
                    if (start >= raw.size) return null
                    NdefItem(NdefKind.Text, String(raw, start, raw.size - start, Charsets.UTF_8))
                }
                type == "U" -> {
                    val text = try {
                        record.toUri()?.toString().orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    if (text.isBlank()) null else NdefItem(NdefKind.Uri, text)
                }
                type.startsWith("text/vcard") -> {
                    val body = String(record.payload, Charsets.UTF_8)
                    val name = body.lineSequence()
                        .firstOrNull { it.startsWith("FN:", ignoreCase = true) }
                        ?.substring(3)?.trim().orEmpty()
                    val tel = body.lineSequence()
                        .firstOrNull { it.startsWith("TEL", ignoreCase = true) }
                        ?.substringAfter(':')?.trim().orEmpty()
                    if (name.isBlank() && tel.isBlank()) null
                    else NdefItem(NdefKind.VCard, name, tel)
                }
                else -> null
            }
        }

        fun fromMessage(message: NdefMessage?): List<NdefItem> =
            message?.records?.mapNotNull { fromRecord(it) } ?: emptyList()
    }
}
