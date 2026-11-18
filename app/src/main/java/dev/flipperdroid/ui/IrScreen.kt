package dev.flipperdroid.ui

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.flipperdroid.ir.IrButton
import dev.flipperdroid.ir.IrStore
import dev.flipperdroid.ir.Nec

@Composable
fun IrScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ir = remember {
        ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val store = remember { IrStore(ctx) }

    var buttons by remember { mutableStateOf(store.load()) }
    var status by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    val emitter = remember {
        try {
            ir?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }
    }
    val has38k = remember {
        try {
            ir?.hasIrFrequency(38000) == true
        } catch (e: Exception) {
            false
        }
    }

    fun fire(button: IrButton) {
        val service = ir
        if (service == null || !emitter) {
            status = "HP ini tidak punya IR emitter."
            return
        }
        status = try {
            service.transmit(button.frequency, Nec.pattern(button.address, button.command))
            "→ ${button.name} (NEC ${button.addressHex()} ${button.commandHex()})"
        } catch (e: Exception) {
            "Gagal kirim: ${e.message}"
        }
    }

    FlipperScreen(
        title = "Infrared",
        subtitle = "NEC 38 kHz • transmitter",
        onBack = onBack
    ) {
        Panel(title = "status hardware") {
            KeyValue("IR emitter", if (emitter) "ADA" else "TIDAK ADA", if (emitter) FlipperGreen else FlipperRed)
            KeyValue("Carrier 38 kHz", if (has38k) "didukung" else "cek manual")
            KeyValue("Modulasi", "NEC (lainnya: import nanti)")
        }

        Gap()

        Panel(title = "tombol tersimpan") {
            buttons.forEach { button ->
                IrButtonRow(
                    button = button,
                    onFire = { fire(button) },
                    onDelete = {
                        buttons = buttons.filter { it != button }.toMutableList()
                        store.save(buttons)
                    }
                )
                Gap(8)
            }
            if (status.isNotEmpty()) {
                Gap(4)
                Mono(status, 11, FlipperOrange)
            }
        }

        Gap()

        Panel(title = "tambah kode manual (NEC)") {
            FlipperField(name, { name = it }, "Nama", "mis. AC Living Room")
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address hex", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = darkFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command hex", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = darkFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val a = parseHex(address)
                        val c = parseHex(command)
                        if (a == null || c == null) {
                            status = "Address/command harus hex, mis. 04 atau 0x04"
                        } else {
                            val label = name.ifBlank { "Remote ${buttons.size + 1}" }
                            val newButton = IrButton(label, "NEC", a, c)
                            buttons = (buttons + newButton).toMutableList()
                            store.save(buttons)
                            name = ""
                            address = ""
                            command = ""
                            fire(newButton)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlipperOrange, contentColor = Color.Black)
                ) {
                    Text("SIMPAN & TES", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Gap()
        Mono("Tips: kode NEC sering ditulis di forum remote. Format 0xADDR / 0xCMD. Kalau TV nggak merespons, coba address 0x00.", 10, FlipperDim)
    }
}

private fun parseHex(raw: String): Int? {
    val clean = raw.trim().removePrefix("0x").removePrefix("0X")
    if (clean.isEmpty()) return null
    return clean.toIntOrNull(16)
}

@Composable
private fun FlipperField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 11.sp, color = FlipperDim) },
        singleLine = true,
        colors = darkFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FlipperOrange,
    unfocusedBorderColor = FlipperLine,
    focusedLabelColor = FlipperOrange,
    unfocusedLabelColor = FlipperDim,
    cursorColor = FlipperOrange,
    focusedTextColor = FlipperText,
    unfocusedTextColor = FlipperText,
    focusedContainerColor = Color.Black,
    unfocusedContainerColor = Color.Black
)

@Composable
private fun IrButtonRow(button: IrButton, onFire: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, FlipperLine, RoundedCornerShape(8.dp))
            .clickable { onFire() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▶", color = FlipperOrange, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(button.name, color = FlipperText, fontSize = 14.sp)
            Text(
                "${button.protocol} ${button.addressHex()} ${button.commandHex()} @ ${button.frequency / 1000} kHz",
                color = FlipperDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Text(
            "hapus",
            color = FlipperDim,
            fontSize = 10.sp,
            modifier = Modifier.clickable { onDelete() }.padding(start = 8.dp)
        )
    }
}
