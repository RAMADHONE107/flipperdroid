package dev.flipperdroid.ir

/**
 * Parser file .ir (format Flipper Zero / IRDB).
 *
 * ```text
 * Filetype: IR signals file
 * Version: 1
 * #
 * name: Power
 * type: parsed
 * protocol: NEC
 * address: 04 00 00 00
 * command: 08 00 00 00
 * #
 * name: Ac_on
 * type: raw
 * frequency: 38000
 * duty_cycle: 0.330000
 * data: 3400 -1700 425 -425 ...
 * ```
 */
object IrFileParser {

    data class Result(
        val buttons: List<IrButton>,
        val skipped: List<String>
    )

    fun parse(text: String, source: String): Result {
        val buttons = mutableListOf<IrButton>()
        val skipped = mutableListOf<String>()
        var block = LinkedHashMap<String, String>()
        var index = 0

        fun flush() {
            if (block.isEmpty()) return
            index++
            val name = block["name"]?.takeIf { it.isNotBlank() } ?: "Signal $index"
            when (block["type"]?.lowercase()) {
                "parsed" -> {
                    val protocol = block["protocol"]?.trim().orEmpty()
                    if (!IrEncoder.supports(protocol)) {
                        skipped.add("$name ($protocol)")
                    } else {
                        buttons.add(
                            IrButton(
                                name = name,
                                protocol = protocol.uppercase(),
                                address = bytesToInt(block["address"]),
                                command = bytesToInt(block["command"]),
                                source = source
                            )
                        )
                    }
                }
                "raw" -> {
                    val frequency = block["frequency"]?.trim()?.toIntOrNull() ?: 38000
                    val data = block["data"]
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.filter { it != 0 }
                        ?: emptyList()
                    if (data.size < 2) {
                        skipped.add("$name (raw kosong)")
                    } else {
                        buttons.add(
                            IrButton(
                                name = name,
                                protocol = "RAW",
                                frequency = frequency,
                                raw = data.toIntArray(),
                                source = source
                            )
                        )
                    }
                }
                else -> skipped.add("$name (type tidak dikenal)")
            }
            block = LinkedHashMap()
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                flush()
                continue
            }
            if (line.startsWith("Filetype", ignoreCase = true) ||
                line.startsWith("Version", ignoreCase = true)
            ) {
                continue
            }
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            block[key] = value
        }
        flush()

        return Result(buttons, skipped)
    }

    /** "04 00 00 00" -> little-endian 0x00000004. */
    private fun bytesToInt(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val bytes = raw.trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull(16) }
        var value = 0
        for (i in 0 until minOf(4, bytes.size)) {
            value = value or ((bytes[i] and 0xFF) shl (8 * i))
        }
        return value
    }
}
