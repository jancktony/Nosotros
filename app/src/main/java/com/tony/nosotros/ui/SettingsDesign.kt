package com.tony.nosotros.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WebPanel {
    const val URL = "https://nosotros-1e43a.web.app/"
    fun open(context: Context) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL))) }
}

@Composable
fun SettingsHero(title: String, subtitle: String, badge: String = "NOSOTROS", trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(C.me.copy(alpha = .16f), C.surface, C.partner.copy(alpha = .09f))))
        .padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(badge, color = C.me, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(title, color = C.text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = C.sub, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsLink(icon: ImageVector, title: String, subtitle: String, accent: Color = C.me, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).clickable(onClick = onClick)
        .padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = C.sub, fontSize = 12.sp)
        }
        Text("›", color = C.sub, fontSize = 26.sp)
    }
}

@Composable
fun PanelTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { title ->
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected == title) C.me else C.surface)
                .clickable { onSelect(title) }.padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text(title, color = if (selected == title) C.meInk else C.sub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun FeatureModes(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface2).padding(4.dp)) {
        listOf("off" to "Apagada", "admin" to "Solo yo", "all" to "Ambos").forEach { (mode, title) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                .background(if (selected == mode) C.me else Color.Transparent)
                .clickable(enabled = enabled && selected != mode) { onSelect(mode) }
                .padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(title, color = if (selected == mode) C.meInk else C.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun HelpDisclosure(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
        Text("${if (open) "−" else "+"} $title", color = C.sub, fontSize = 12.sp)
    }
    if (open) Text(text, color = C.sub, fontSize = 12.sp)
}

@Composable
fun WidgetPreview(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(color.copy(alpha = .17f), C.surface2)))
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NOSOTROS", color = color, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(title, color = C.sub, fontSize = 12.sp)
    }
}

@Composable
fun PrimaryAction(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(14.dp), colors = ButtonDefaults.buttonColors(containerColor = C.me, contentColor = C.meInk)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
