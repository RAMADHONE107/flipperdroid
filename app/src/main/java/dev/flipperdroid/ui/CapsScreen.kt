package dev.flipperdroid.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.flipperdroid.caps.Caps

@Composable
fun CapsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    val hasIr = Caps.hasIr(ctx)
    val hasNfc = Caps.hasNfc(ctx)
    val hasBt = Caps.hasBluetooth(ctx)
    val hasHost = Caps.hasUsbHost(ctx)
    val usbCount = Caps.usbDeviceCount(ctx)

    FlipperScreen(
        title = "Kapabilitas HP",
        subtitle = "deteksi runtime",
        onBack = onBack
    ) {
        Panel(title = "perangkat") {
            KeyValue("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            KeyValue("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            KeyValue("Board", Build.BOARD)
            KeyValue("Device", Build.DEVICE)
        }

        Gap()

        Panel(title = "fitur yang bisa dipakai") {
            KeyValue("IR emitter", if (hasIr) "ADA" else "TIDAK ADA", if (hasIr) FlipperGreen else FlipperRed)
            KeyValue("NFC", if (hasNfc) "ADA" else "TIDAK ADA", if (hasNfc) FlipperGreen else FlipperRed)
            KeyValue("Bluetooth", if (hasBt) "ADA" else "TIDAK ADA", if (hasBt) FlipperGreen else FlipperRed)
            KeyValue("USB host (OTG)", if (hasHost) "didukung" else "tidak", if (hasHost) FlipperGreen else FlipperRed)
            KeyValue("Perangkat USB", "$usbCount terpasang")
        }

        Gap()

        Panel(title = "yang tidak bisa dilakukan HP") {
            Mono("• Sub-GHz 300-928 MHz (butuh CC1101)", 10, FlipperDim)
            Gap(4)
            Mono("• RFID 125 kHz (butuh coil + reader)", 10, FlipperDim)
            Gap(4)
            Mono("• iButton / 1-Wire (butuh GPIO)", 10, FlipperDim)
            Gap(4)
            Mono("• IR learning (butuh receiver IR)", 10, FlipperDim)
            Gap(4)
            Mono("• Clone UID kartu akses (butuh root + patch NFC)", 10, FlipperDim)
        }

        Gap()
        Mono(
            "Kalau punya hardware eksternal (CC1101 board, Proxmark3, RTL-SDR), colok lewat OTG dan fungsi di atas bisa ditambahkan di versi berikutnya.",
            10,
            FlipperDim
        )
    }
}
