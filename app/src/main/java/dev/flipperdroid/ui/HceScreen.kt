package dev.flipperdroid.ui

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flipperdroid.hce.NdefEmulator
import dev.flipperdroid.hce.NdefItem
import dev.flipperdroid.hce.NdefKind
import dev.flipperdroid.nfc.NfcReader

@Composable
fun HceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }

    var items by remember { mutableStateOf(NdefEmulator.records(ctx)) }
    var enabled by remember { mutableStateOf(NdefEmulator.isEnabled(ctx)) }
    var kind by remember { mutableStateOf(NdefKind.Text) }
    var primary by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val bytes = remember(items) { NdefEmulator.buildBytes(items) }
    val size = bytes?.size ?: 0
    val tooBig = size > NdefEmulator.MAX_NDEF_SIZE
    val parsed = remember(bytes) {
        bytes?.let { runCatching { NdefMessage(it) }.getOrNull() }
    }
    val preview = NfcReader.describeMessage(parsed)
    val nfcOn = adapter?.isEnabled == true

    fun persist(enable: Boolean) {
        if (tooBig) {
            status = "Total $size byte, melebihi batas ${NdefEmulator.MAX_NDEF_SIZE}. Kurangi record."
            return
        }
        val written = NdefEmulator.setRecords(ctx, items)
        if (written < 0) {
            status = "Gagal simpan: payload terlalu besar."
            return
        }
        if (enable) {
            NdefEmulator.setEnabled(ctx, true)
            enabled = true
        }
        status = "Tersimpan • $written byte • ${items.size} record"
    }

    FlipperScreen(
        title = "NFC Emulator",
        subtitle = "Host Card Emulation • Type 4 Tag",
        onBack = onBack
    ) {
        Panel(title = "status") {
            KeyValue(
                "NFC",
                if (adapter == null) "TIDAK ADA" else if (nfcOn) "aktif" else "mati",
                if (nfcOn) FlipperGreen else FlipperRed
            )
            KeyValue("Emulasi", if (enabled) "AKTIF" else "nonaktif", if (enabled) FlipperGreen else FlipperDim)
            KeyValue("AID", NdefEmulator.AID_HEX)
            KeyValue("File CC", "E103 (15 byte)")
            KeyValue("File NDEF", "E104 (${NdefEmulator.MAX_NDEF_SIZE} byte max)")
            KeyValue("Record", "${items.size}")
            KeyValue(
                "Ukuran",
                if (size == 0) "kosong" else "$size byte",
                if (tooBig) FlipperRed else FlipperText
            )
            if (status.isNotEmpty()) {
                Gap(8)
                Mono(status, 11, if (tooBig) FlipperRed else FlipperOrange)
            }
        }

        Gap()

        Panel(title = "saklar emulasi") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
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
                    onCheckedChange = { on ->
                        enabled = on
                        NdefEmulator.setEnabled(ctx, on)
                        status = if (on) "Emulasi diaktifkan." else "Emulasi dimatikan."
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

            if (items.isEmpty()) {
                Gap(8)
                Mono("Belum ada record. Tambah di bawah, lalu simpan.", 10, FlipperRed)
            }
        }

        Gap()

        Panel(title = "record di tag virtual") {
            if (items.isEmpty()) {
                Mono("(kosong)", 11, FlipperDim)
            }
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        color = FlipperOrange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(item.label(), color = FlipperOrange, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        Text(item.preview(), color = FlipperText, fontSize = 12.sp)
                    }
                    Text(
                        "hapus",
                        color = FlipperDim,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable {
                                items = items.filterIndexed { i, _ -> i != index }
                            }
                            .padding(start = 8.dp)
                    )
                }
                Gap(6)
            }

            Gap(4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { persist(enable = true) },
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
                        items = emptyList()
                        NdefEmulator.setRecords(ctx, emptyList())
                        NdefEmulator.setEnabled(ctx, false)
                        enabled = false
                        status = "Semua record dihapus."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("KOSONGKAN", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }

        Gap()

        Panel(title = "tambah record") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NdefKind.entries.forEach { option ->
                    KindChip(
                        label = when (option) {
                            NdefKind.Text -> "TEKS"
                            NdefKind.Uri -> "URL"
                            NdefKind.VCard -> "VCARD"
                        },
                        selected = kind == option,
                        onClick = { kind = option }
                    )
                }
            }

            Gap(10)

            when (kind) {
                NdefKind.Text -> {
                    DarkField(
                        value = primary,
                        onChange = { primary = it },
                        label = "Teks",
                        placeholder = "mis. Hai dari FlipperDroid"
                    )
                }
                NdefKind.Uri -> {
                    DarkField(
                        value = primary,
                        onChange = { primary = it },
                        label = "URL",
                        placeholder = "example.com (otomatis https://)"
                    )
                }
                NdefKind.VCard -> {
                    DarkField(
                        value = primary,
                        onChange = { primary = it },
                        label = "Nama",
                        placeholder = "mis. Budi"
                    )
                    Gap(8)
                    DarkField(
                        value = secondary,
                        onChange = { secondary = it },
                        label = "Nomor telepon",
                        placeholder = "+62 812..."
                    )
                }
            }

            Gap(10)
            Button(
                onClick = {
                    val item = when (kind) {
                        NdefKind.Text ->
                            if (primary.isBlank()) null else NdefItem(NdefKind.Text, primary.trim())
                        NdefKind.Uri -> {
                            val uri = NdefItem.normalizeUri(primary)
                            if (uri == null) null else NdefItem(NdefKind.Uri, uri.toString())
                        }
                        NdefKind.VCard ->
                            if (primary.isBlank() && secondary.isBlank()) null
                            else NdefItem(NdefKind.VCard, primary.trim(), secondary.trim())
                    }
                    if (item == null) {
                        status = "Isi datanya dulu."
                    } else {
                        items = items + item
                        primary = ""
                        secondary = ""
                        status = "${item.label()} ditambahkan. Tekan SIMPAN & AKTIFKAN."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
            ) {
                Text("TAMBAH RECORD", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        if (preview.isNotEmpty()) {
            Gap()
            Panel(title = "isi tag (hasil encode)") {
                preview.forEachIndexed { index, line ->
                    Mono("${index + 1}. $line", 10, FlipperText)
                    Gap(4)
                }
                if (tooBig) {
                    Mono("Payload terlalu besar untuk tag virtual.", 10, FlipperRed)
                }
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
            Mono("2. Tambah record (teks / URL / vCard), lalu SIMPAN & AKTIFKAN.", 10, FlipperDim)
            Gap(4)
            Mono("3. Tempel punggung HP ke reader NFC (HP lain, Flipper, ACR122U).", 10, FlipperDim)
            Gap(4)
            Mono("4. Reader harus mendukung NFC Forum Type 4 Tag / NDEF.", 10, FlipperDim)
        }

        Gap()

        Panel(title = "batasan") {
            Mono("• Hanya NDEF (teks/URL/vCard). Bukan emulasi kartu akses atau kartu bank.", 10, FlipperDim)
            Gap(4)
            Mono("• UID kartu tetap milik chip NFC HP, tidak bisa dipalsukan.", 10, FlipperDim)
            Gap(4)
            Mono("• Maksimal ${NdefEmulator.MAX_NDEF_SIZE} byte per tag virtual.", 10, FlipperDim)
            Gap(4)
            Mono("• Beberapa HP mematikan HCE saat layar mati atau app di background.", 10, FlipperDim)
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else FlipperOrange,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) FlipperOrange else FlipperPanel)
            .border(1.dp, if (selected) FlipperOrange else FlipperLine, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 11.sp, color = FlipperDim) },
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
}
