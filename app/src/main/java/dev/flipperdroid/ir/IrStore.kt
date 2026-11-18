package dev.flipperdroid.ir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class IrButton(
    val name: String,
    val protocol: String = "NEC",
    val address: Int = 0,
    val command: Int = 0,
    val frequency: Int = 38000
) {
    fun addressHex(): String = "0x%02X".format(address and 0xFF)
    fun commandHex(): String = "0x%02X".format(command and 0xFF)
}

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
                        name = o.optString("name", "Remote"),
                        protocol = o.optString("protocol", "NEC"),
                        address = o.optInt("address", 0),
                        command = o.optInt("command", 0),
                        frequency = o.optInt("frequency", 38000)
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
        for (b in list) {
            val o = JSONObject()
            o.put("name", b.name)
            o.put("protocol", b.protocol)
            o.put("address", b.address)
            o.put("command", b.command)
            o.put("frequency", b.frequency)
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun defaults(): MutableList<IrButton> = mutableListOf(
        IrButton("TV Power", address = 0x04, command = 0x08),
        IrButton("TV Volume +", address = 0x04, command = 0x02),
        IrButton("TV Volume -", address = 0x04, command = 0x03),
        IrButton("TV Mute", address = 0x04, command = 0x09)
    )

    private companion object {
        const val KEY = "buttons"
    }
}
