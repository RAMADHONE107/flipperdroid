package dev.flipperdroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.widget.RemoteViews
import dev.flipperdroid.MainActivity
import dev.flipperdroid.R
import dev.flipperdroid.ir.IrButton
import dev.flipperdroid.ir.IrEncoder
import dev.flipperdroid.ir.IrStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Widget "TV Power" di home screen.
 *
 * Tombol yang dipakai: tombol IR pertama yang namanya mengandung "power"
 * (case-insensitive), kalau tidak ada maka tombol pertama di daftar.
 * Widget tidak menyimpan kode sendiri — selalu membaca [IrStore], jadi kalau
 * kamu ganti/import kode di aplikasi, widget ikut berubah tanpa setting ulang.
 */
class TvPowerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, READY))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TV_POWER) return

        val pending = goAsync()
        val result = transmit(context)
        updateAll(context, result)

        // Kembalikan label ke status siap setelah beberapa detik.
        Thread {
            try {
                Thread.sleep(2500)
            } catch (e: InterruptedException) {
                // diabaikan
            }
            updateAll(context, READY)
            pending.finish()
        }.start()
    }

    private fun transmit(context: Context): String {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (ir == null) return "Tidak ada IR service"

        val hasEmitter = try {
            ir.hasIrEmitter()
        } catch (e: Exception) {
            false
        }
        if (!hasEmitter) return "HP ini tidak punya IR"

        val button = selectButton(context) ?: return "Belum ada kode IR"
        val pattern = IrEncoder.build(button) ?: return "${button.protocol} belum didukung"

        return try {
            ir.transmit(IrEncoder.frequencyFor(button), pattern)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "${button.name} • $time"
        } catch (e: Exception) {
            "Gagal: ${e.message}"
        }
    }

    private fun selectButton(context: Context): IrButton? {
        val buttons = try {
            IrStore(context).load()
        } catch (e: Exception) {
            mutableListOf()
        }
        return buttons.firstOrNull { it.name.contains("power", ignoreCase = true) }
            ?: buttons.firstOrNull()
    }

    private fun buildViews(context: Context, status: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tv_power)
        views.setTextViewText(R.id.widget_status, status)
        views.setOnClickPendingIntent(R.id.widget_action, clickIntent(context))
        views.setOnClickPendingIntent(R.id.widget_title, openIntent(context))
        return views
    }

    private fun updateAll(context: Context, status: String) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TvPowerWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val views = buildViews(context, status)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    private fun clickIntent(context: Context): PendingIntent {
        val intent = Intent(context, TvPowerWidgetProvider::class.java)
            .setAction(ACTION_TV_POWER)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ACTION_TV_POWER = "dev.flipperdroid.widget.TV_POWER"
        const val READY = "siap • tap untuk kirim"
    }
}
