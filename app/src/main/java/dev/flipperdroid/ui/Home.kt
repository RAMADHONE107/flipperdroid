package dev.flipperdroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flipperdroid.caps.Caps

enum class Screen {
    Home, Ir, Nfc, Hce, Hid, SubGhz, Rfid, IButton, Caps, About
}

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val hasIr = Caps.hasIr(ctx)
    val hasNfc = Caps.hasNfc(ctx)
    val hasBt = Caps.hasBluetooth(ctx)

    FlipperScreen(
        title = "FlipperDroid",
        subtitle = "portable toolkit • v0.7"
    ) {
        Panel(title = "hardware terdeteksi") {
            CapRow("Infrared emitter", hasIr)
            CapRow("NFC 13.56 MHz", hasNfc)
            CapRow("Bluetooth HID", hasBt)
            Gap(6)
            Mono(
                if (hasIr && hasNfc) "Siap dipakai. Pilih menu di bawah."
                else "Sebagian fitur tidak tersedia di HP ini.",
                10,
                FlipperDim
            )
        }

        Gap()

        MenuRow(
            icon = Icons.Filled.SettingsRemote,
            label = "Infrared",
            sub = "TV / AC / proyektor — 33 remote bawaan",
            badge = if (hasIr) "READY" else "N/A"
        ) { onOpen(Screen.Ir) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.Nfc,
            label = "NFC",
            sub = "Baca UID, dump Ultralight, tulis NDEF",
            badge = if (hasNfc) "READY" else "N/A"
        ) { onOpen(Screen.Nfc) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.Share,
            label = "NFC Emulator",
            sub = "HP jadi tag NDEF multi-record (HCE)",
            badge = if (hasNfc) "READY" else "N/A"
        ) { onOpen(Screen.Hce) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.Keyboard,
            label = "Bad-BT Keyboard",
            sub = "HP jadi keyboard HID untuk PC",
            badge = if (hasBt) "READY" else "N/A"
        ) { onOpen(Screen.Hid) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.WifiTethering,
            label = "Sub-GHz",
            sub = "CC1101 via OTG — raw capture + decode 433/868",
            badge = "OTG"
        ) { onOpen(Screen.SubGhz) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.CreditCard,
            label = "RFID 125 kHz",
            sub = "EM4100 / T5577 — butuh reader",
            badge = "OTG"
        ) { onOpen(Screen.Rfid) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.VpnKey,
            label = "iButton",
            sub = "1-Wire key — butuh reader",
            badge = "OTG"
        ) { onOpen(Screen.IButton) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.PhoneAndroid,
            label = "Kapabilitas HP",
            sub = "Cek sensor & fitur yang benar-benar ada",
            badge = null
        ) { onOpen(Screen.Caps) }
        Gap(8)

        MenuRow(
            icon = Icons.Filled.Info,
            label = "Tentang",
            sub = "Lisensi, batasan, kredit",
            badge = null
        ) { onOpen(Screen.About) }

        Gap(18)
        Text(
            text = "bukan produk resmi Flipper Devices",
            color = FlipperDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun CapRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) FlipperGreen else FlipperRed)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(label, color = FlipperText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            if (ok) "OK" else "—",
            color = if (ok) FlipperGreen else FlipperRed,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}
