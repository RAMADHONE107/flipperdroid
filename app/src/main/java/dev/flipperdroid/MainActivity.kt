package dev.flipperdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.flipperdroid.ui.AboutScreen
import dev.flipperdroid.ui.CapsScreen
import dev.flipperdroid.ui.FlipperTheme
import dev.flipperdroid.ui.HardwareMissingScreen
import dev.flipperdroid.ui.HceScreen
import dev.flipperdroid.ui.HidScreen
import dev.flipperdroid.ui.HomeScreen
import dev.flipperdroid.ui.IrScreen
import dev.flipperdroid.ui.NfcScreen
import dev.flipperdroid.ui.Screen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlipperTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf(Screen.Home) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(onOpen = { screen = it })
        Screen.Ir -> IrScreen(onBack = { screen = Screen.Home })
        Screen.Nfc -> NfcScreen(onBack = { screen = Screen.Home })
        Screen.Hce -> HceScreen(onBack = { screen = Screen.Home })
        Screen.Hid -> HidScreen(onBack = { screen = Screen.Home })
        Screen.Caps -> CapsScreen(onBack = { screen = Screen.Home })
        Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
        Screen.SubGhz -> HardwareMissingScreen(
            title = "Sub-GHz",
            subtitle = "300-928 MHz",
            what = "Flipper memakai chip CC1101 + antena untuk membaca dan memutar sinyal 433/868/915 MHz (remote gerbang, sensor, weather station). HP tidak punya radio di frekuensi ini.",
            hardware = "CC1101 board via USB-Serial (OTG)",
            note = "Kalau kamu punya CC1101 + Arduino/USB-TTL, modul bisa ditambahkan lewat serial OTG di update berikutnya.",
            onBack = { screen = Screen.Home }
        )
        Screen.Rfid -> HardwareMissingScreen(
            title = "RFID 125 kHz",
            subtitle = "EM4100 / T5577 / HID",
            what = "RFID 125 kHz butuh coil induktif dan reader khusus. Chip NFC HP hanya bekerja di 13.56 MHz, jadi kartu akses 125 kHz tidak bisa dibaca dari HP.",
            hardware = "Reader 125 kHz USB (mis. Proxmark3 / RDM6300)",
            note = "Proxmark3 lewat OTG memungkinkan baca/tulis 125 kHz, tapi butuh driver serial + izin USB.",
            onBack = { screen = Screen.Home }
        )
        Screen.IButton -> HardwareMissingScreen(
            title = "iButton",
            subtitle = "1-Wire key",
            what = "iButton adalah keping logam 1-Wire (DS1990A). Untuk membaca/menulisnya butuh kontak fisik dan jalur GPIO 1-Wire, yang tidak dimiliki HP.",
            hardware = "Reader 1-Wire USB / DS9490R",
            note = "Bisa ditambahkan lewat USB serial, tapi tetap butuh kontak fisik ke keping.",
            onBack = { screen = Screen.Home }
        )
    }
}
