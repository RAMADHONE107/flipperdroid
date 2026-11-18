package dev.flipperdroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FlipperScreen(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(FlipperBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    text = "<",
                    color = FlipperOrange,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 14.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    color = FlipperOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 15.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = FlipperDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = "● ONLINE",
                color = FlipperDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        HorizontalDivider(color = FlipperLine)

        val scrollState = rememberScrollState()
        val modifier = if (scroll) {
            Modifier.fillMaxSize().verticalScroll(scrollState)
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun Panel(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FlipperPanel)
            .border(1.dp, FlipperLine, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                color = FlipperDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun MenuRow(
    icon: ImageVector,
    label: String,
    sub: String,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FlipperPanel)
            .border(1.dp, FlipperLine, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FlipperOrange, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = FlipperText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = FlipperDim, fontSize = 11.sp)
        }
        if (badge != null) {
            Text(
                text = badge,
                color = FlipperOrange,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun Mono(text: String, size: Int = 12, color: Color = FlipperText) {
    Text(text = text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp)
}

@Composable
fun Gap(dp: Int = 12) {
    Spacer(Modifier.height(dp.dp))
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = FlipperText) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = key,
            color = FlipperDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
