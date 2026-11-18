package dev.flipperdroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import dev.flipperdroid.hid.HidKeyboard
import kotlinx.coroutines.launch

@Composable
fun HidScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = remember { HidKeyboard(ctx) }

    var status by remember { mutableStateOf("Belum aktif.") }
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var registered by remember { mutableStateOf(false) }
    var connectedName by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf("echo halo dari FlipperDroid") }
    var granted by remember { mutableStateOf(hasBtPermission(ctx)) }
    var sending by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        status = if (ok) "Izin Bluetooth diberikan." else "Izin Bluetooth ditolak."
    }

    DisposableEffect(Unit) {
        keyboard.onStatus = { status = it }
        keyboard.onDevices = { devices = it }
        keyboard.onRegistered = { registered = it }
        keyboard.onConnected = { connectedName = it }
        onDispose { keyboard.stop() }
    }

    LaunchedEffect(granted) {
        if (granted) keyboard.start()
    }

    FlipperScreen(
        title = "Bad-BT Keyboard",
        subtitle = "HID injection via Bluetooth",
        onBack = onBack
    ) {
        Panel(title = "status") {
            KeyValue(
                "Izin BT",
                if (granted) "diberikan" else "belum",
                if (granted) FlipperGreen else FlipperRed
            )
            KeyValue(
                "HID",
                when {
                    connectedName != null -> "terhubung"
                    registered -> "terdaftar"
                    else -> "belum"
                },
                if (registered) FlipperGreen else FlipperDim
            )
            KeyValue("Target", connectedName ?: "-")
            Gap(8)
            Mono(status, 11, FlipperOrange)
        }

        Gap()

        if (!granted) {
            Button(
                onClick = { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
            ) {
                Text("BERI IZIN BLUETOOTH", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Gap()
        }

        Panel(title = "langkah") {
            Mono("1. Pasangkan HP ini dengan PC/HP target lewat Setelan Bluetooth (atau tekan tombol di bawah, lalu pair dari sisi PC).", 10, FlipperDim)
            Gap(6)
            Mono("2. Daftarkan HID, lalu pilih perangkat target.", 10, FlipperDim)
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { keyboard.start() },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("DAFTARKAN HID", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        try {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            status = "Gagal buka mode terlihat: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperPanel, contentColor = FlipperOrange)
                ) {
                    Text("TERLIHAT 120s", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }

        Gap()

        Panel(title = "perangkat terpasang") {
            if (devices.isEmpty()) {
                Mono("Belum ada perangkat paired. Pasangkan dulu di Setelan Bluetooth.", 10, FlipperDim)
            } else {
                devices.forEach { device ->
                    DeviceRow(device) { keyboard.connect(device) }
                }
            }
        }

        Gap()

        Panel(title = "payload") {
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text("Teks yang akan diketik", fontSize = 11.sp) },
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
            Button(
                onClick = {
                    scope.launch {
                        sending = true
                        keyboard.typeText(payload)
                        sending = false
                    }
                },
                enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
            ) {
                Text(
                    if (sending) "MENGIRIM..." else "KIRIM KE TARGET",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Gap()
        Mono(
            "Ini bukan USB HID. Untuk mode kabel (BadUSB asli) butuh dukungan gadget HID di kernel HP — biasanya hanya jalan di ROM tertentu. Versi Bluetooth ini jalan di Poco X6 tanpa root.",
            10,
            FlipperDim
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name ?: "Tanpa nama", color = FlipperText, fontSize = 13.sp)
            Text(device.address, color = FlipperDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        Text("connect", color = FlipperOrange, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

private fun hasBtPermission(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        ctx,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
}
