package dev.flipperdroid.ui

import android.content.Context
import android.hardware.ConsumerIrManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flipperdroid.ir.IrButton
import dev.flipperdroid.ir.IrEncoder
import dev.flipperdroid.ir.IrFileParser
import dev.flipperdroid.ir.IrLibrary
import dev.flipperdroid.ir.IrStore
import java.util.UUID

@Composable
fun IrScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ir = remember {
        ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val store = remember { IrStore(ctx) }

    var buttons by remember { mutableStateOf(store.load()) }
    var status by remember { mutableStateOf("") }
    var skippedInfo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var libraryCategory by remember { mutableStateOf(IrLibrary.categories.first()) }

    val emitter = remember {
        try {
            ir?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }
    }
    val has38k = remember {
        try {
            ir?.carrierFrequencies?.any { 38000 in it.minFrequency..it.maxFrequency } == true
        } catch (e: Exception) {
            false
        }
    }

    fun fire(button: IrButton) {
        val service = ir
        if (service == null || !emitter) {
            status = "HP ini tidak punya IR emitter."
            return
        }
        val pattern = IrEncoder.build(button)
        if (pattern == null) {
            status = "Protokol ${button.protocol} belum didukung. Pakai file raw."
            return
        }
        status = try {
            service.transmit(IrEncoder.frequencyFor(button), pattern)
            "→ ${button.name} • ${button.describe()}"
        } catch (e: Exception) {
            "Gagal kirim: ${e.message}"
        }
    }

    fun importLibrary(device: IrLibrary.Device) {
        val text = try {
            ctx.assets.open(device.asset).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            status = "Gagal membaca ${device.asset} dari asset."
            return
        }

        val parsed = IrFileParser.parse(text, "${device.brand} ${device.model}")
        val known = buttons.map { it.signature() }.toMutableSet()
        val fresh = parsed.buttons.filter { known.add(it.signature()) }

        if (fresh.isEmpty()) {
            status = "${device.brand} ${device.model}: semua tombol sudah ada."
            skippedInfo = ""
            return
        }

        buttons = (buttons + fresh).toMutableList()
        store.save(buttons)
        status = buildString {
            append("${device.brand} ${device.model}: ${fresh.size} sinyal diimpor")
            if (parsed.skipped.isNotEmpty()) append(" • ${parsed.skipped.size} dilewati")
        }
        skippedInfo = if (parsed.skipped.isEmpty()) "" else parsed.skipped.take(8).joinToString("\n")
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val known = buttons.map { it.signature() }.toMutableSet()
        val imported = mutableListOf<IrButton>()
        val skipped = mutableListOf<String>()
        var failedFiles = 0

        for (uri in uris) {
            val fileName = displayName(ctx, uri) ?: "import.ir"
            val text = readText(ctx, uri)
            if (text == null) {
                failedFiles++
                continue
            }
            val source = fileName.removeSuffix(".ir").ifBlank { fileName }
            val parsed = IrFileParser.parse(text, source)
            for (button in parsed.buttons) {
                if (known.add(button.signature())) imported.add(button)
            }
            skipped.addAll(parsed.skipped)
        }

        if (imported.isNotEmpty()) {
            buttons = (buttons + imported).toMutableList()
            store.save(buttons)
        }

        status = buildString {
            append("${imported.size} sinyal diimpor")
            if (skipped.isNotEmpty()) append(" • ${skipped.size} dilewati")
            if (failedFiles > 0) append(" • $failedFiles file gagal dibaca")
        }
        skippedInfo = if (skipped.isEmpty()) "" else skipped.take(8).joinToString("\n")
    }

    FlipperScreen(
        title = "Infrared",
        subtitle = "NEC • NECext • Samsung • Sony • RAW",
        onBack = onBack
    ) {
        Panel(title = "status hardware") {
            KeyValue("IR emitter", if (emitter) "ADA" else "TIDAK ADA", if (emitter) FlipperGreen else FlipperRed)
            KeyValue("Carrier 38 kHz", if (has38k) "didukung" else "cek manual")
            KeyValue("Tombol tersimpan", "${buttons.size}")
        }

        Gap()

        Panel(title = "library remote (offline)") {
            Mono(
                "33 remote siap pakai dari Flipper-IRDB (lisensi CC0). Tap satu untuk impor semua tombolnya — nggak perlu cari file .ir.",
                10,
                FlipperDim
            )
            Gap(10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IrLibrary.categories.forEach { category ->
                    LibraryChip(category, category == libraryCategory) {
                        libraryCategory = category
                    }
                }
            }
            Gap(10)
            IrLibrary.byCategory(libraryCategory).forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
                        .clickable { importLibrary(device) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(
                            "${device.brand} • ${device.model}",
                            color = FlipperText,
                            fontSize = 13.sp
                        )
                        Text(
                            "${device.signals} sinyal • ${device.category}",
                            color = FlipperDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        "IMPOR →",
                        color = FlipperOrange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Gap(8)
            }
        }

        Gap()

        Panel(title = "impor file .ir") {
            Mono(
                "Format Flipper Zero / IRDB. Sinyal \"parsed\" di-encode ulang, sinyal \"raw\" direplay apa adanya (cocok untuk remote AC).",
                10,
                FlipperDim
            )
            Gap(4)
            Mono("Protokol: NEC, NECext, Samsung32, SIRC/15/20, RAW. RC5 dilewati (butuh pola mulai dari space).", 10, FlipperDim)
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text(
                        "PILIH FILE .IR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        buttons = mutableListOf()
                        store.save(buttons)
                        status = "Semua tombol dihapus."
                        skippedInfo = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("HAPUS SEMUA", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            if (status.isNotEmpty()) {
                Gap(8)
                Mono(status, 11, FlipperOrange)
            }
            if (skippedInfo.isNotEmpty()) {
                Gap(6)
                Mono(skippedInfo, 9, FlipperDim)
            }
        }

        Gap()

        Panel(title = "tombol tersimpan") {
            if (buttons.isEmpty()) {
                Mono("Belum ada tombol. Impor .ir atau tambah manual di bawah.", 10, FlipperDim)
            }
            buttons.forEach { button ->
                IrButtonRow(
                    button = button,
                    onFire = { fire(button) },
                    onDelete = {
                        buttons = buttons.filter { it.id != button.id }.toMutableList()
                        store.save(buttons)
                    }
                )
                Gap(8)
            }
        }

        Gap()

        Panel(title = "widget tv power") {
            Mono(
                "Ada widget home screen \"TV Power\". Tambahkan lewat long-press layar utama → Widget → FlipperDroid.",
                10,
                FlipperDim
            )
            Gap(6)
            Mono(
                "Widget mengirim tombol pertama yang namanya mengandung \"power\" (default: TV Power NEC 0x04/0x08). Kalau belum ada yang cocok, dipakai tombol paling atas.",
                10,
                FlipperDim
            )
            Gap(6)
            Mono("Tap judul widget untuk buka aplikasi. Tap tombol ▶ untuk kirim IR.", 10, FlipperDim)
        }

        Gap()

        Panel(title = "tambah kode manual (NEC)") {
            FlipperField(name, { name = it }, "Nama", "mis. AC Living Room")
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address hex", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = darkFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command hex", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = darkFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val a = parseHex(address)
                        val c = parseHex(command)
                        if (a == null || c == null) {
                            status = "Address/command harus hex, mis. 04 atau 0x04"
                        } else {
                            val label = name.ifBlank { "Remote ${buttons.size + 1}" }
                            val newButton = IrButton(
                                id = UUID.randomUUID().toString(),
                                name = label,
                                protocol = "NEC",
                                address = a,
                                command = c
                            )
                            buttons = (buttons + newButton).toMutableList()
                            store.save(buttons)
                            name = ""
                            address = ""
                            command = ""
                            fire(newButton)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text("SIMPAN & TES", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Gap()
        Mono(
            "Tips: cari \"kode remote [merek] NEC hex\" atau download file .ir dari repo Flipper IRDB, lalu impor di sini. Kalau TV nggak merespons, coba address 0x00 atau protokol lain.",
            10,
            FlipperDim
        )
    }
}

private fun parseHex(raw: String): Int? {
    val clean = raw.trim().removePrefix("0x").removePrefix("0X")
    if (clean.isEmpty()) return null
    return clean.toIntOrNull(16)
}

private fun readText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
} catch (e: Exception) {
    null
}

private fun displayName(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
} catch (e: Exception) {
    null
}

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun FlipperField(
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
        singleLine = true,
        colors = darkFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FlipperOrange,
    unfocusedBorderColor = FlipperLine,
    focusedLabelColor = FlipperOrange,
    unfocusedLabelColor = FlipperDim,
    cursorColor = FlipperOrange,
    focusedTextColor = FlipperText,
    unfocusedTextColor = FlipperText,
    focusedContainerColor = Color.Black,
    unfocusedContainerColor = Color.Black
)

@Composable
private fun IrButtonRow(button: IrButton, onFire: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
            .clickable { onFire() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▶", color = FlipperOrange, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(button.name, color = FlipperText, fontSize = 14.sp)
            Text(
                buildString {
                    append(button.describe())
                    if (button.source.isNotBlank()) append(" • ${button.source}")
                },
                color = FlipperDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Text(
            "hapus",
            color = FlipperDim,
            fontSize = 10.sp,
            modifier = Modifier.clickable { onDelete() }.padding(start = 8.dp)
        )
    }
}
