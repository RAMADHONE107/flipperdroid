package dev.flipperdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.flipperdroid.caps.Caps

@Composable
fun HardwareMissingScreen(
    title: String,
    subtitle: String,
    what: String,
    hardware: String,
    note: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val usbCount = Caps.usbDeviceCount(ctx)
    val hasHost = Caps.hasUsbHost(ctx)

    FlipperScreen(title = title, subtitle = subtitle, onBack = onBack) {
        Panel(title = "status") {
            KeyValue("Di HP ini", "butuh hardware eksternal", FlipperRed)
            KeyValue("Modul", hardware)
            KeyValue("USB OTG", if (hasHost) "didukung" else "tidak didukung", if (hasHost) FlipperGreen else FlipperRed)
            KeyValue("Terpasang", "$usbCount perangkat USB")
        }

        Gap()

        Panel(title = "kenapa") {
            Mono(what, 11, FlipperText)
        }

        Gap()

        Panel(title = "catatan") {
            Mono(note, 10, FlipperDim)
        }

        Gap()
        Mono(
            "Halaman ini sengaja tidak dipalsukan. Flipper asli punya chip fisik untuk fungsi ini; aplikasi yang mengklaim bisa tanpa hardware biasanya scam.",
            10,
            FlipperDim
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    FlipperScreen(title = "Tentang", subtitle = "FlipperDroid 0.1.0", onBack = onBack) {
        Panel(title = "apa ini") {
            Mono(
                "FlipperDroid adalah aplikasi Android open-source yang meniru antarmuka dan sebagian fungsi Flipper Zero memakai hardware yang memang ada di HP (IR blaster, NFC, Bluetooth HID).",
                11,
                FlipperText
            )
        }

        Gap()

        Panel(title = "yang bukan") {
            Mono("• Bukan produk Flipper Devices.", 10, FlipperDim)
            Gap(4)
            Mono("• Tidak berisi firmware Flipper.", 10, FlipperDim)
            Gap(4)
            Mono("• Tidak bisa Sub-GHz / 125 kHz / iButton tanpa hardware tambahan.", 10, FlipperDim)
            Gap(4)
            Mono("• Tidak untuk membuka akses yang bukan milikmu.", 10, FlipperDim)
        }

        Gap()

        Panel(title = "lisensi") {
            Mono("Kode aplikasi ini: GPL-3.0 (sama seperti ekosistem Flipper).", 10, FlipperText)
            Gap(6)
            Mono("Protokol NEC diimplementasikan ulang dari spesifikasi publik, bukan salinan kode Flipper.", 10, FlipperDim)
            Gap(6)
            Mono("Nama Flipper Zero adalah milik Flipper Devices Inc. Proyek ini tidak berafiliasi.", 10, FlipperDim)
        }

        Gap()

        Panel(title = "build") {
            KeyValue("Versi", "0.1.0")
            KeyValue("Target", "Android 9+ (API 28)")
            KeyValue("Bahasa", "Kotlin + Jetpack Compose")
        }
    }
}
