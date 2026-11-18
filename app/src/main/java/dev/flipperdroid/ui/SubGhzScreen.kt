package dev.flipperdroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.flipperdroid.subghz.Cc1101Link
import dev.flipperdroid.subghz.SubGhzDecoder

@Composable
fun SubGhzScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasUsbHost = remember {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    }

    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (hasUsbHost) "Colok board CC1101 lewat OTG, lalu SCAN USB." else "HP ini tidak punya USB host.") }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var frames by remember { mutableStateOf(listOf<CapturedFrame>()) }
    var selected by remember { mutableStateOf<CapturedFrame?>(null) }
    var frequency by remember { mutableStateOf(433_920_000L) }
    var lastRaw by remember { mutableStateOf("") }
    var receiving by remember { mutableStateOf(false) }

    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun copy(text: String, label: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        status = "Disalin ke clipboard."
    }

    val link = remember {
        Cc1101Link(ctx, object : Cc1101Link.Listener {
            override fun onStatus(text: String) {
                status = text
            }

            override fun onLine(text: String) {
                logLines = (logLines + text).takeLast(40)
            }

            override fun onFrame(raw: String) {
                val frame = CapturedFrame(raw, SubGhzDecoder.decodeCsv(raw))
                frames = (listOf(frame) + frames).distinctBy { it.raw }.take(20)
                selected = frame
                lastRaw = raw
                receiving = true
            }

            override fun onConnected(name: String) {
                connected = true
                receiving = false
                status = "Tersambung: $name @ 115200"
            }

            override fun onClosed(reason: String) {
                connected = false
                receiving = false
                status = reason
            }
        })
    }

    DisposableEffect(link) {
        onDispose { link.release() }
    }

    fun applyFrequency() {
        if (!connected) {
            status = "Sambungkan board dulu."
            return
        }
        val mhz = String.format("%.3f", frequency / 1_000_000.0)
        if (link.send("FREQ $frequency")) {
            status = "Set frekuensi $mhz MHz"
        }
    }

    FlipperScreen(
        title = "Sub-GHz",
        subtitle = "CC1101 via USB serial • raw capture & replay",
        onBack = onBack
    ) {
        Panel(title = "status") {
            KeyValue("USB host", if (hasUsbHost) "ADA" else "TIDAK ADA", if (hasUsbHost) FlipperGreen else FlipperRed)
            KeyValue("Board", if (connected) "tersambung" else "belum", if (connected) FlipperGreen else FlipperDim)
            KeyValue("Frekuensi", String.format("%.3f MHz", frequency / 1_000_000.0))
            KeyValue("Mode", if (receiving) "RX (menangkap)" else "idle", if (receiving) FlipperGreen else FlipperDim)
            KeyValue("Frame tertangkap", "${frames.size}")
            Gap(8)
            Mono(status, 11, FlipperOrange)
        }

        Gap()

        Panel(title = "perangkat usb") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        devices = link.devices()
                        status = if (devices.isEmpty()) {
                            "Tidak ada USB terdeteksi. Cek kabel OTG / board."
                        } else {
                            "${devices.size} perangkat USB ditemukan. Tap untuk minta izin."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text("SCAN USB", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (connected) {
                    Button(
                        onClick = {
                            link.close("Diputus manual.")
                            connected = false
                            receiving = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                    ) {
                        Text("PUTUSKAN", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }

            if (devices.isEmpty()) {
                Gap(8)
                Mono("Belum ada perangkat. Colok board lalu tekan SCAN USB.", 10, FlipperDim)
            }

            devices.forEach { device ->
                Gap(8)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
                        .clickable {
                            link.requestPermission(device)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(link.describe(device), color = FlipperText, fontSize = 12.sp)
                        Text(
                            if (connected) "tap untuk sambung ulang" else "tap untuk minta izin & sambung",
                            color = FlipperDim,
                            fontSize = 10.sp
                        )
                    }
                    Text("→", color = FlipperOrange, fontSize = 14.sp)
                }
            }
        }

        Gap()

        Panel(title = "frekuensi") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FreqChip("315.00", frequency == 315_000_000L) { frequency = 315_000_000L; applyFrequency() }
                FreqChip("433.92", frequency == 433_920_000L) { frequency = 433_920_000L; applyFrequency() }
                FreqChip("868.35", frequency == 868_350_000L) { frequency = 868_350_000L; applyFrequency() }
            }
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FreqChip("915.00", frequency == 915_000_000L) { frequency = 915_000_000L; applyFrequency() }
                FreqChip("SET ULANG", false) { applyFrequency() }
            }
            Gap(8)
            Mono("Frekuensi dikirim ke firmware: FREQ $frequency", 9, FlipperDim)
        }

        Gap()

        Panel(title = "capture & replay") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!connected) {
                            status = "Sambungkan board dulu."
                        } else {
                            link.send("FREQ $frequency")
                            if (link.send("RX")) {
                                receiving = true
                                status = "Mulai menangkap. Tekan remote di dekat antena."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text("MULAI RX", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        link.send("IDLE")
                        receiving = false
                        status = "Capture dihentikan."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("STOP", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        if (lastRaw.isBlank()) {
                            status = "Belum ada frame untuk dikirim ulang."
                        } else if (!connected) {
                            status = "Sambungkan board dulu."
                        } else {
                            link.send("FREQ $frequency")
                            link.send("TX $lastRaw")
                            status = "Replay dikirim (${lastRaw.split(',').size} step)."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("REPLAY", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }

            if (frames.isEmpty()) {
                Gap(10)
                Mono("Belum ada frame. Tekan MULAI RX lalu pencet remote 433 MHz.", 10, FlipperDim)
            }

            frames.forEach { frame ->
                Gap(8)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (frame == selected) Color(0xFF221400) else Color.Black)
                        .border(
                            1.dp,
                            if (frame == selected) FlipperOrange else FlipperLine,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selected = frame }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            frame.headline(),
                            color = if (frame.decode != null) FlipperOrange else FlipperText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Gap(3)
                        Text(
                            "${frame.steps()} step • tap untuk detail",
                            color = FlipperDim,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        Gap()

        Panel(title = "detail frame") {
            val frame = selected
            if (frame == null) {
                Mono("Tap salah satu frame di atas untuk lihat detail & replay.", 10, FlipperDim)
            } else {
                Mono(
                    frame.decode?.detail() ?: buildString {
                        appendLine("Protocol : RAW (tidak dikenali)")
                        appendLine("Steps    : ${frame.steps()}")
                        appendLine("Catatan  : decoder hanya mengenali Princeton, CAME, Keeloq.")
                        append("Frame tetap bisa di-replay sebagai raw.")
                    },
                    10,
                    FlipperText
                )
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!connected) {
                                status = "Sambungkan board dulu."
                            } else {
                                link.send("FREQ $frequency")
                                link.send("TX ${frame.raw}")
                                status = "Replay dikirim (${frame.steps()} step)."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                    ) {
                        Text("KIRIM ULANG", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val text = frame.decode?.detail()
                                ?: "RAW\n${frame.raw}"
                            copy(text, "Sub-GHz")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                    ) {
                        Text("SALIN", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { copy(frame.raw, "Sub-GHz raw") },
                        colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                    ) {
                        Text("SALIN RAW", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }

        Gap()

        Panel(title = "log firmware") {
            if (logLines.isEmpty()) {
                Mono("(kosong)", 10, FlipperDim)
            }
            logLines.asReversed().forEach { line ->
                Mono(line, 9, FlipperDim)
            }
        }

        Gap()

        Panel(title = "butuh firmware") {
            Mono(
                "CC1101 tidak punya USB. Rangkai CC1101 ke ESP32 / Arduino, lalu flash firmware/cc1101_bridge (ada di repo) dan colok board ke HP pakai OTG.",
                10,
                FlipperDim
            )
            Gap(6)
            Mono("Board yang didukung app: CH340, CP210x, FTDI (USB-Serial).", 10, FlipperDim)
            Gap(6)
            Mono(
                "Firmware cuma kirim durasi pulse mentah. Decoder Princeton / CAME / Keeloq jalan di HP (parameter dari source Flipper), jadi tidak perlu flash ulang kalau decoder diperbaiki.",
                10,
                FlipperDim
            )
            Gap(6)
            Mono("Decoder heuristik: label bisa salah, tapi replay tetap memakai frame raw aslinya.", 10, FlipperDim)
        }
    }
}

private data class CapturedFrame(
    val raw: String,
    val decode: SubGhzDecoder.Decode?
) {
    fun steps(): Int = raw.count { it == ',' } + 1

    fun headline(): String = decode?.headline() ?: "RAW • ${steps()} step"
}

@Composable
private fun FreqChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
