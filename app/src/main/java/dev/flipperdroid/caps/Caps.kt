package dev.flipperdroid.caps

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.usb.UsbManager

object Caps {

    fun hasIr(context: Context): Boolean = try {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        ir?.hasIrEmitter() == true
    } catch (e: Exception) {
        false
    }

    fun hasNfc(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

    fun hasBluetooth(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    fun hasUsbHost(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    fun usbDeviceCount(context: Context): Int = try {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        usb?.deviceList?.size ?: 0
    } catch (e: Exception) {
        0
    }
}
