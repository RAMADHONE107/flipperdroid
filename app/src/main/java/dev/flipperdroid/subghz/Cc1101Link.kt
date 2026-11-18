package dev.flipperdroid.subghz

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Jembatan serial USB ke firmware CC1101 (lihat firmware/cc1101_bridge).
 *
 * Protokol baris (ASCII, 115200 8N1):
 *   PING              -> "PONG cc1101-bridge 1"
 *   FREQ <hz>         -> "OK freq <mhz>"
 *   RX                -> "OK rx"   lalu stream "RX <csv durasi mikrodetik>"
 *   IDLE              -> "OK idle"
 *   TX <csv durasi>   -> "OK tx"
 *
 * Durasi memakai konvensi Flipper: positif = carrier ON, negatif = OFF.
 */
class Cc1101Link(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(text: String)
        fun onLine(text: String)
        fun onFrame(raw: String)
        fun onConnected(name: String)
        fun onClosed(reason: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    @Volatile
    private var port: UsbSerialPort? = null
    private var reader: Thread? = null

    @Volatile
    private var running = false

    private var receiver: BroadcastReceiver? = null

    fun hasUsb(): Boolean = usb != null

    fun devices(): List<UsbDevice> = usb?.deviceList?.values?.toList() ?: emptyList()

    fun describe(device: UsbDevice): String {
        val name = try {
            device.productName
        } catch (e: Exception) {
            null
        } ?: device.deviceName
        return "$name • ${device.vendorId.toString(16)}:${device.productId.toString(16)}"
    }

    fun isConnected(): Boolean = port != null

    fun requestPermission(device: UsbDevice) {
        val manager = usb
        if (manager == null) {
            post { listener.onStatus("USB host tidak tersedia di HP ini.") }
            return
        }

        unregisterReceiver()

        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_PERMISSION) return
                unregisterReceiver()
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    open(device)
                } else {
                    post { listener.onStatus("Izin USB ditolak.") }
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                broadcast,
                IntentFilter(ACTION_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiver = broadcast
        } catch (e: Exception) {
            post { listener.onStatus("Gagal daftar receiver USB: ${e.message}") }
            return
        }

        val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        manager.requestPermission(device, pending)
        post { listener.onStatus("Menunggu izin USB…") }
    }

    private fun open(device: UsbDevice) {
        Thread {
            val manager = usb
            if (manager == null) {
                post { listener.onStatus("USB host tidak tersedia.") }
                return@Thread
            }
            try {
                if (!manager.hasPermission(device)) {
                    post { listener.onStatus("Belum ada izin untuk perangkat ini.") }
                    return@Thread
                }

                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                if (driver == null) {
                    post {
                        listener.onStatus(
                            "Bukan USB-Serial yang dikenal. Pakai board CH340 / CP210x / FTDI."
                        )
                    }
                    return@Thread
                }

                val connection = manager.openDevice(device)
                if (connection == null) {
                    post { listener.onStatus("Gagal membuka USB (dipakai proses lain?).") }
                    return@Thread
                }

                val serial = driver.ports.firstOrNull()
                if (serial == null) {
                    post { listener.onStatus("Port serial tidak ditemukan.") }
                    connection.close()
                    return@Thread
                }

                serial.open(connection)
                serial.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                port = serial
                running = true

                post {
                    listener.onConnected(describe(device))
                    listener.onLine("PING dikirim…")
                }
                send("PING")

                readLoop(serial)
            } catch (e: Exception) {
                post { listener.onStatus("Gagal buka port: ${e.message}") }
            }
        }.start()
    }

    private fun readLoop(serial: UsbSerialPort) {
        reader = Thread.currentThread()
        val buffer = ByteArray(8192)
        val pending = StringBuilder()

        try {
            while (running) {
                val count = try {
                    serial.read(buffer, READ_TIMEOUT)
                } catch (e: Exception) {
                    if (running) {
                        post {
                            listener.onStatus("Koneksi serial terputus: ${e.message}")
                            listener.onClosed("Serial terputus.")
                        }
                    }
                    break
                }

                if (count <= 0) continue

                pending.append(String(buffer, 0, count, Charsets.US_ASCII))
                var separator = pending.indexOf("\n")
                while (separator >= 0) {
                    val line = pending.substring(0, separator)
                    pending.delete(0, separator + 1)
                    handleLine(line)
                    separator = pending.indexOf("\n")
                }

                if (pending.length > MAX_LINE) {
                    pending.setLength(0)
                    handleLine("ERR baris terlalu panjang")
                }
            }
        } finally {
            try {
                serial.close()
            } catch (e: Exception) {
                // diabaikan
            }
            if (port === serial) {
                port = null
                running = false
                post { listener.onClosed("Serial ditutup.") }
            }
        }
    }

    private fun handleLine(line: String) {
        val text = line.trim()
        if (text.isEmpty()) return
        if (text.startsWith("RX ")) {
            post { listener.onFrame(text.substring(3).trim()) }
        } else {
            post { listener.onLine(text) }
        }
    }

    /** Kirim satu baris perintah. @return false kalau port belum terbuka. */
    fun send(command: String): Boolean {
        val serial = port ?: return false
        return try {
            serial.write((command + "\n").toByteArray(Charsets.US_ASCII), WRITE_TIMEOUT)
            true
        } catch (e: Exception) {
            post { listener.onStatus("Gagal kirim: ${e.message}") }
            false
        }
    }

    fun close(reason: String? = null) {
        running = false
        reader?.interrupt()
        reader = null

        val serial = port
        port = null
        try {
            serial?.close()
        } catch (e: Exception) {
            // diabaikan
        }

        unregisterReceiver()

        if (reason != null) {
            post { listener.onClosed(reason) }
        }
    }

    fun release() {
        close(null)
        main.removeCallbacksAndMessages(null)
    }

    private fun unregisterReceiver() {
        val current = receiver ?: return
        receiver = null
        try {
            context.unregisterReceiver(current)
        } catch (e: Exception) {
            // sudah terdaftar / sudah dilepas
        }
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    companion object {
        const val ACTION_PERMISSION = "dev.flipperdroid.USB_PERMISSION"
        private const val BAUD = 115200
        private const val READ_TIMEOUT = 150
        private const val WRITE_TIMEOUT = 500
        private const val MAX_LINE = 32 * 1024
    }
}
