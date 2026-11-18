package dev.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

/**
 * Dump halaman MIFARE Ultralight / NTAG lewat NFC.
 *
 * Android melaporkan NTAG213/215/216 sebagai [MifareUltralight.TYPE_ULTRALIGHT_C],
 * jadi jumlah halaman dideteksi dengan membaca sampai gagal (maksimum 232).
 */
object UltralightDump {

    data class Result(
        val uid: String,
        val type: String,
        val pages: Int,
        val lines: List<String>,
        val error: String?
    ) {
        fun asText(): String = buildString {
            appendLine("FlipperDroid • dump MIFARE Ultralight")
            appendLine("UID  : $uid")
            appendLine("Tipe : $type")
            appendLine("Pages: $pages")
            appendLine()
            if (error != null) {
                appendLine("ERROR: $error")
            } else {
                lines.forEach { appendLine(it) }
            }
        }
    }

    fun dump(tag: Tag): Result {
        val uid = (tag.id ?: byteArrayOf()).joinToString(":") { "%02X".format(it) }
        val tech = try {
            MifareUltralight.get(tag)
        } catch (e: Exception) {
            null
        }
        if (tech == null) {
            return Result(uid, "-", 0, emptyList(), "Tag ini bukan MIFARE Ultralight / NTAG.")
        }

        return try {
            tech.connect()
            val typeName = when (tech.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight (MF0ICU1)"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C / NTAG"
                else -> "tipe ${tech.type}"
            }
            val lines = mutableListOf<String>()
            var page = 0
            var pagesRead = 0
            while (page < MAX_PAGES) {
                val chunk = try {
                    tech.readPages(page)
                } catch (e: Exception) {
                    break
                }
                if (chunk == null || chunk.isEmpty()) break
                chunk.toList().chunked(4).forEachIndexed { index, bytes ->
                    val current = page + index
                    if (current < MAX_PAGES) {
                        lines.add(
                            "P%03d  %s".format(
                                current,
                                bytes.joinToString(" ") { "%02X".format(it) }
                            )
                        )
                        pagesRead = current + 1
                    }
                }
                page += 4
            }

            if (lines.isEmpty()) {
                Result(uid, typeName, 0, emptyList(), "Tidak ada halaman yang bisa dibaca (tag diproteksi?).")
            } else {
                Result(uid, typeName, pagesRead, lines, null)
            }
        } catch (e: Exception) {
            Result(uid, "-", 0, emptyList(), "Gagal baca: ${e.message}")
        } finally {
            try {
                tech.close()
            } catch (e: Exception) {
                // diabaikan
            }
        }
    }

    private const val MAX_PAGES = 232
}
