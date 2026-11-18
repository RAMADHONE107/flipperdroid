package dev.flipperdroid.ui

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flipperdroid.hce.NdefEmulator
import dev.flipperdroid.nfc.NfcReader

@Composable
fun HceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }

    var enabled by remember { mutableStateOf(NdefEmulator.isEnabled(ctx)) }
    var message by remember { mutableStateOf(NdefEmulator.message(ctx)) }
    var text by remember {
        mutableStateOf(
            message?.let { bytes ->
                NfcReader.textOf(runCatching { NdefMessage(bytes) }.getOrNull())
            } ?: "https://flipperdroid.local"
        )
    }
    var status by remember { mutableStateOf("") }

    val parsed = message?.let { runCatching { NdefMessage(it) }.getOrNull() }
    val preview = NfcReader.describeMessage(parsed)
    val nfcOn = adapter?.isEnabled == true

    FlipperScreen(
        title = "NFC Emulator",
        subtitle = "Host Card Emulation • Type 4 Tag",
        onBack = onBack
    ) {
        Panel(title = "status") {
            KeyValue("NFC", if (adapter == null) "TIDAK ADA" else if (nfcOn) "aktif" else "mati",
                if (nfcOn) FlipperGreen else FlipperRed)
            KeyValue("Emulasi", if (enabled) "AKTIF" else "nonaktif",
                if (enabled) FlipperGreen else FlipperDim)
            KeyValue("AID", NdefEmulator.AID_HEX)
            KeyValue("File CC", "E103 (15 byte)")
            KeyValue("File NDEF", "E104 (${NdefEmulator.MAX_NDEF_SIZE} byte max)")
            KeyValue("Isi NDEF", if (message == null) "kosong" else "${message!!.size} byte")
            if (status.isNotEmpty()) {
                Gap(8)
                Mono(status, 11, FlipperOrange)
            }
        }

        Gap()

        Panel(title = "saklar emulasi") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "HP sedang jadi kartu NDEF" else "Emulasi dimatikan",
                        color = FlipperText,
                        fontSize = 14.sp
                    )
                    Text(
                        if (enabled) "Tempelkan punggung HP ke reader" else "Aktifkan sebelum tap ke reader",
                        color = FlipperDim,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        NdefEmulator.setEnabled(ctx, it)
                        status = if (it) "Emulasi diaktifkan." else "Emulasi dimatikan."
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = FlipperOrange,
                        uncheckedThumbColor = FlipperDim,
                        uncheckedTrackColor = FlipperPanel,
                        uncheckedBorderColor = FlipperLine
                    )
                )
            }

            if (message == null) {
                Gap(8)
                Mono("Belum ada payload. Simpan teks di bawah dulu.", 10, FlipperRed)
            }
        }

        Gap()

        Panel(title = "payload (NDEF text record)") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Teks / URL", fontSize = 11.sp) },
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
                        if (text.isBlank()) {
                            status = "Teks masih kosong."
                        } else {
                            val bytes = NdefEmulator.textMessage(text)
                            NdefEmulator.setMessage(ctx, bytes)
                            NdefEmulator.setEnabled(ctx, true)
                            message = bytes
                            enabled = true
                            status = "Payload disimpan & emulasi aktif."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text(
                        "SIMPAN & AKTIFKAN",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        NdefEmulator.setMessage(ctx, null)
                        NdefEmulator.setEnabled(ctx, false)
                        message = null
                        enabled = false
                        status = "Payload dihapus."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("HAPUS", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }

            if (preview.isNotEmpty()) {
                Gap(10)
                preview.forEach { line -> Mono("• $line", 10, FlipperText) }
            }
        }

        if (!nfcOn) {
            Gap()
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
                Text("BUKA SETELAN NFC", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        Gap()

        Panel(title = "cara pakai") {
            Mono("1. Nyalakan NFC dan buka kunci layar.", 10, FlipperDim)
            Gap(4)
            Mono("2. Simpan payload, lalu pastikan saklar emulasi AKTIF.", 10, FlipperDim)
            Gap(4)
            Mono("3. Tempel punggung HP ke reader NFC (HP lain, Flipper, ACR122U, dll).", 10, FlipperDim)
            Gap(4)
            Mono("4. Reader harus mendukung NFC Forum Type 4 Tag / NDEF.", 10, FlipperDim)
        }

        Gap()

        Panel(title = "batasan") {
            Mono("• Hanya NDEF (teks/URL). Bukan emulasi kartu akses atau kartu bank.", 10, FlipperDim)
            Gap(4)
            Mono("• UID kartu tetap milik chip NFC HP, tidak bisa dipalsukan.", 10, FlipperDim)
            Gap(4)
            Mono("• Beberapa HP mematikan HCE saat layar mati atau aplikasi di background.", 10, FlipperDim)
            Gap(4)
            Mono("• Pembayaran NFC (tap-to-pay) tetap memakai aplikasi wallet masing-masing.", 10, FlipperDim)
        }
    }
}
