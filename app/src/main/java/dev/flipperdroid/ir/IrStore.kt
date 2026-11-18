package dev.flipperdroid.ir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class IrStore(context: Context) {

    private val prefs = context.getSharedPreferences("flipperdroid_ir", Context.MODE_PRIVATE)

    fun load(): MutableList<IrButton> {
        val raw = prefs.getString(KEY, null) ?: return defaults()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<IrButton>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    IrButton(
                        id = o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
                        name = o.optString("name", "Remote"),
                        protocol = o.optString("protocol", "NEC"),
                        address = o.optInt("address", 0),
                        command = o.optInt("command", 0),
                        frequency = o.optInt("frequency", 38000),
                        raw = o.optString("raw", "").let { parseRaw(it) },
                        source = o.optString("source", "")
                    )
                )
            }
            if (list.isEmpty()) defaults() else list
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(list: List<IrButton>) {
        val array = JSONArray()
        for (button in list) {
            val o = JSONObject()
            o.put("id", button.id)
            o.put("name", button.name)
            o.put("protocol", button.protocol)
            o.put("address", button.address)
            o.put("command", button.command)
            o.put("frequency", button.frequency)
            o.put("raw", button.raw?.joinToString(",") ?: "")
            o.put("source", button.source)
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun parseRaw(raw: String): IntArray? {
        if (raw.isBlank()) return null
        val values = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (values.isEmpty()) null else values.toIntArray()
    }

    private fun defaults(): MutableList<IrButton> = mutableListOf(
        IrButton(name = "TV Power", address = 0x04, command = 0x08),
        IrButton(name = "TV Volume +", address = 0x04, command = 0x02),
        IrButton(name = "TV Volume -", address = 0x04, command = 0x03),
        IrButton(name = "TV Mute", address = 0x04, command = 0x09)
    )

    private companion object {
        const val KEY = "buttons"
    }
}
