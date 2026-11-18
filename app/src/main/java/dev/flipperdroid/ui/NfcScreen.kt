package dev.flipperdroid.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.flipperdroid.nfc.NfcReader
import dev.flipperdroid.nfc.TagInfo
import dev.flipperdroid.nfc.UltralightDump

@Composable
fun NfcScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }

    var info by remember { mutableStateOf<TagInfo?>(null) }
    var lastTag by remember { mutableStateOf<Tag?>(null) }
    var dump by remember { mutableStateOf<UltralightDump.Result?>(null) }
    var status by remember {
        mutableStateOf(
            when {
                adapter == null -> "HP ini tidak punya NFC."
                !adapter.isEnabled -> "NFC mati. Nyalakan dulu di Setelan."
                else -> "Tempelkan tag NFC ke punggung HP."
            }
        )
    }
    var text by remember { mutableStateOf("https://example.com") }
    var nfcOn by remember { mutableStateOf(adapter?.isEnabled == true) }
    var readerActive by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(adapter, activity, lifecycleOwner, retry) {
        val nfc = adapter
        val act = activity
        if (nfc == null || act == null) {
            return@DisposableEffect onDispose { }
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            val read = try {
                NfcReader.read(tag)
            } catch (e: Exception) {
                null
            }
            // Callback ini jalan di binder thread, jadi aman untuk I/O NFC.
            val ultralight = try {
                if (tag.techList.any { it.endsWith("MifareUltralight") }) {
                    UltralightDump.dump(tag)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            act.runOnUiThread {
                if (read != null) {
                    lastTag = tag
                    info = read
                    dump = ultralight
                    status = "Tag terbaca."
                } else {
                    status = "Tag terdeteksi, tapi gagal dibaca. Coba tempel ulang."
                }
            }
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        fun enable() {
            nfcOn = try {
                nfc.isEnabled
            } catch (e: Exception) {
                false
            }
            if (!nfcOn) {
                readerActive = false
                status = "NFC mati. Nyalakan dulu di Setelan, lalu kembali ke sini."
                return
            }
            // Reader mode hanya efektif kalau activity sudah di foreground, jadi
            // dipanggil saat ON_RESUME, bukan saat komposisi awal.
            var ok = false
            try {
                // Di Android API 34 signature-nya void: tidak ada return value
                // yang bisa dicek, jadi keberhasilan dideteksi dari exception.
                nfc.enableReaderMode(act, callback, flags, null)
                ok = true
            } catch (e: Exception) {
                status = "Gagal aktifkan reader: ${e.message}"
            }
            readerActive = ok
            status = if (ok) {
                "Reader aktif — tempelkan tag ke punggung HP."
            } else {
                "Reader mode gagal aktif. Tutup-buka layar NFC atau tekan COBA LAGI."
            }
        }

        fun disable() {
            readerActive = false
            try {
                nfc.disableReaderMode(act)
            } catch (e: Exception) {
                // diabaikan
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            enable()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disable()
        }
    }

    FlipperScreen(
        title = "NFC",
        subtitle = "13.56 MHz • baca & tulis NDEF",
        onBack = onBack
    ) {
        Panel(title = "status") {
            KeyValue("Adapter", if (adapter != null) "ADA" else "TIDAK ADA", if (adapter != null) FlipperGreen else FlipperRed)
            KeyValue("NFC", if (nfcOn) "menyala" else "mati", if (nfcOn) FlipperGreen else FlipperRed)
            KeyValue("Reader mode", if (readerActive) "aktif" else "tidak aktif", if (readerActive) FlipperGreen else FlipperRed)
            Gap(8)
            Mono(status, 11, FlipperOrange)
            if (!nfcOn || !readerActive) {
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { retry++ },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                    ) {
                        Text("COBA LAGI", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                            } catch (e: Exception) {
                                status = "Gagal buka setelan: ${e.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                    ) {
                        Text("SETELAN NFC", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }

        Gap()

        val current = info
        if (current != null) {
            Panel(title = "tag terbaca") {
                KeyValue("UID", current.uid)
                KeyValue("Teknologi", current.techs.joinToString(", ").ifBlank { "-" })
                KeyValue("Kapasitas", if (current.maxSize > 0) "${current.maxSize} byte" else "-")
                KeyValue("NDEF", if (current.readableNdef) "ada" else "kosong / bukan NDEF")
                if (current.records.isNotEmpty()) {
                    Gap(8)
                    current.records.forEach { record ->
                        Mono("• $record", 10, FlipperText)
                    }
                }
            }
            Gap()
        }

        val dumpResult = dump
        if (dumpResult != null) {
            Panel(title = "dump MIFARE Ultralight") {
                KeyValue("Tipe", dumpResult.type)
                KeyValue("Halaman", if (dumpResult.pages > 0) "${dumpResult.pages}" else "-")
                KeyValue("Baris data", "${dumpResult.lines.size}")

                if (dumpResult.error != null) {
                    Gap(8)
                    Mono(dumpResult.error, 10, FlipperRed)
                }

                if (dumpResult.lines.isNotEmpty()) {
                    Gap(8)
                    dumpResult.lines.take(48).forEach { line ->
                        Mono(line, 9, FlipperText)
                    }
                    if (dumpResult.lines.size > 48) {
                        Gap(6)
                        Mono("… ${dumpResult.lines.size - 48} halaman lagi. Salin untuk lihat semua.", 9, FlipperDim)
                    }
                }

                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("FlipperDroid dump", dumpResult.asText())
                                )
                                status = "Dump disalin ke clipboard (${dumpResult.lines.size} halaman)."
                            } catch (e: Exception) {
                                status = "Gagal salin: ${e.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                    ) {
                        Text("SALIN DUMP", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { dump = null },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                    ) {
                        Text("TUTUP", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
            Gap()
        }

        Panel(title = "tulis NDEF (teks/link)") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Isi NDEF", fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FlipperOrange,
                    unfocusedBorderColor = FlipperLine,
                    focusedLabelColor = FlipperOrange,
                    unfocusedLabelColor = FlipperDim,
                    cursorColor = FlipperOrange,
                    focusedTextColor = FlipperText,
                    unfocusedTextColor = FlipperText,
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val tag = lastTag
                        if (tag == null) {
                            status = "Tempelkan tag dulu, baru tulis."
                        } else {
                            status = NfcReader.writeText(tag, text)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text(
                        "TULIS KE TAG TERAKHIR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Gap()
        Mono(
            "Catatan: HP tidak bisa mengkloning kartu akses (MIFARE Classic UID) tanpa root + firmware NFC yang dipatch. Yang didukung: baca UID/teknologi, dump halaman MIFARE Ultralight/NTAG, baca & tulis NDEF.",
            10,
            FlipperDim
        )
    }
}
