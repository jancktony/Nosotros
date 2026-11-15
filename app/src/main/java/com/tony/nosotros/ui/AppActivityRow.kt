package com.tony.nosotros.ui

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
    Column {
        HorizontalDivider(color=C.line)
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { detail=true }
            .padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha=.15f)),
                contentAlignment=Alignment.Center) {
                Text(activity.badge,color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(activity.title,color=if(activity.available)C.text else C.sub,fontSize=14.sp,
                    fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(activity.subtitle,color=C.sub,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Text("›",color=C.sub,fontSize=23.sp)
        }
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
