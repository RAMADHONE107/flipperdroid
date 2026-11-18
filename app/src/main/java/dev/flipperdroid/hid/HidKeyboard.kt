package dev.flipperdroid.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay

/**
 * Keyboard HID via Bluetooth (BluetoothHidDevice, API 28+).
 * Ini pengganti "BadUSB" Flipper: HP bertindak sebagai keyboard untuk
 * PC/HP lain yang sudah dipasangkan.
 */
class HidKeyboard(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val main = Handler(Looper.getMainLooper())

    private var proxy: BluetoothHidDevice? = null
    private var target: BluetoothDevice? = null

    var onStatus: ((String) -> Unit)? = null
    var onDevices: ((List<BluetoothDevice>) -> Unit)? = null
    var onRegistered: ((Boolean) -> Unit)? = null
    var onConnected: ((String?) -> Unit)? = null

    private fun status(message: String) {
        main.post { onStatus?.invoke(message) }
    }

    val bluetoothAdapter: BluetoothAdapter? get() = adapter

    @SuppressLint("MissingPermission")
    fun start() {
        val bt = adapter
        if (bt == null) {
            status("Bluetooth tidak tersedia di HP ini.")
            return
        }
        if (!bt.isEnabled) {
            status("Bluetooth masih mati.")
            return
        }
        bt.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxyIn: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                val hid = proxyIn as BluetoothHidDevice
                proxy = hid

                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "FlipperDroid Keyboard",
                    "Keyboard HID",
                    "FlipperDroid",
                    BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                    KEYBOARD_DESCRIPTOR
                )

                val callback = object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        main.post {
                            onRegistered?.invoke(registered)
                            onStatus?.invoke(if (registered) "HID aktif. Pilih perangkat." else "HID dilepas.")
                            if (registered) onDevices?.invoke(bondedDevices())
                        }
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                target = device
                                main.post {
                                    onConnected?.invoke(device.name ?: device.address)
                                    onStatus?.invoke("Terhubung ke ${device.name ?: device.address}")
                                }
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (target == device) target = null
                                main.post {
                                    onConnected?.invoke(null)
                                    onStatus?.invoke("Terputus dari ${device.name ?: device.address}")
                                }
                            }
                        }
                    }
                }

                val ok = try {
                    hid.registerApp(sdp, null, null, appContext.mainExecutor, callback)
                } catch (e: Exception) {
                    false
                }
                status(if (ok) "Mendaftarkan HID..." else "registerApp() gagal.")
            }

            override fun onServiceDisconnected(profile: Int) {
                proxy = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val hid = proxy
        if (hid == null) {
            status("HID belum terdaftar.")
            return
        }
        target = device
        val ok = try {
            hid.connect(device)
        } catch (e: Exception) {
            false
        }
        status(if (ok) "Menghubungkan ke ${device.name ?: device.address}..." else "Gagal connect.")
    }

    @SuppressLint("MissingPermission")
    fun send(modifier: Int, key: Int): Boolean {
        val hid = proxy ?: return false
        val device = target ?: return false
        val report = byteArrayOf(
            modifier.toByte(), 0, key.toByte(), 0, 0, 0, 0, 0
        )
        return try {
            hid.sendReport(device, 0, report)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun typeText(text: String) {
        if (target == null) {
            status("Belum terhubung ke perangkat.")
            return
        }
        var sent = 0
        for (c in text) {
            val pair = charToKey(c) ?: continue
            if (!send(pair.first, pair.second)) {
                status("Gagal kirim di karakter ke-${sent + 1}.")
                return
            }
            delay(16)
            send(0, 0)
            delay(16)
            sent++
        }
        status("Selesai. $sent karakter terkirim.")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            proxy?.unregisterApp()
        } catch (e: Exception) {
            // diabaikan
        }
        try {
            val bt = adapter
            val hid = proxy
            if (bt != null && hid != null) bt.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        } catch (e: Exception) {
            // diabaikan
        }
        proxy = null
        target = null
    }

    private fun charToKey(c: Char): Pair<Int, Int>? = when {
        c in 'a'..'z' -> 0 to (0x04 + (c - 'a'))
        c in 'A'..'Z' -> 0x02 to (0x04 + (c - 'A'))
        c in '1'..'9' -> 0 to (0x1E + (c - '1'))
        c == '0' -> 0 to 0x27
        c == '\n' || c == '\r' -> 0 to 0x28
        c == '\t' -> 0 to 0x2B
        c == ' ' -> 0 to 0x2C
        c == '-' -> 0 to 0x2D
        c == '_' -> 0x02 to 0x2D
        c == '=' -> 0 to 0x2E
        c == '+' -> 0x02 to 0x2E
        c == '[' -> 0 to 0x2F
        c == ']' -> 0 to 0x30
        c == '\\' -> 0 to 0x31
        c == ';' -> 0 to 0x33
        c == ':' -> 0x02 to 0x33
        c == '\'' -> 0 to 0x34
        c == '"' -> 0x02 to 0x34
        c == '`' -> 0 to 0x35
        c == ',' -> 0 to 0x36
        c == '<' -> 0x02 to 0x36
        c == '.' -> 0 to 0x37
        c == '>' -> 0x02 to 0x37
        c == '/' -> 0 to 0x38
        c == '?' -> 0x02 to 0x38
        c == '!' -> 0x02 to 0x1E
        c == '@' -> 0x02 to 0x1F
        c == '#' -> 0x02 to 0x20
        c == '$' -> 0x02 to 0x21
        c == '%' -> 0x02 to 0x22
        c == '^' -> 0x02 to 0x23
        c == '&' -> 0x02 to 0x24
        c == '*' -> 0x02 to 0x25
        c == '(' -> 0x02 to 0x26
        c == ')' -> 0x02 to 0x27
        else -> null
    }

    private companion object {
        /**
         * HID report descriptor keyboard standar: 8 byte report
         * (modifier, reserved, 6 keycode).
         */
        val KEYBOARD_DESCRIPTOR: ByteArray = intArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0
        ).map { it.toByte() }.toByteArray()
    }
}
