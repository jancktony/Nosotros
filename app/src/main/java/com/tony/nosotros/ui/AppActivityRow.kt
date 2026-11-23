package com.tony.nosotros.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.tony.nosotros.presence.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.presence.AppActivity

@Composable
internal fun AppActivityRow(subject: String, activity: AppActivity, accent: Color = C.partner) {
    var detail by remember { mutableStateOf(false) }
    val bitmap = remember(activity.icon) { AppIcons.decode(activity.icon) }
    Row(Modifier.fillMaxWidth().height(44.dp).clickable { detail = true }
        .padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(C.surface2.copy(alpha = .8f))
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (bitmap != null && activity.available) Image(bitmap.asImageBitmap(), "Icono de ${activity.title}", Modifier.size(22.dp))
        else Icon(if (activity.title == "Pantalla de inicio") Ic.Pin else Ic.Sparkle, null, tint = accent, modifier = Modifier.size(20.dp))
        Text(activity.title, color = if (activity.available) C.text else C.sub, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (activity.available) Text(activity.subtitle.removePrefix("Última detección · "), color = C.sub, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 115.dp))
    }
    if (detail) AlertDialog(onDismissRequest={detail=false},containerColor=C.surface,
        title={Text(if(subject=="Yo")"Mi actividad" else "Actividad de $subject")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(activity.title,color=C.text,fontWeight=FontWeight.SemiBold)
            Text(activity.subtitle,color=C.sub)
            Text(if(activity.available) "Es la última aplicación detectada. Puede haber cambiado desde esa lectura."
                else if(activity.title=="Actividad no compartida")"Cada persona decide si comparte su actividad."
                else "No se muestra una aplicación antigua como si siguiera abierta.",color=C.sub)
        }},
        confirmButton={TextButton(onClick={detail=false}) {Text("Cerrar")}})
}
