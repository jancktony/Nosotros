package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RequestsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val admin by vm.isAdmin.collectAsState();val pair by vm.pairId.collectAsState();val uid by vm.uid.collectAsState()
    var text by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) };var items by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    DisposableEffect(pair,admin){
        val listener=if(admin&&pair!=null)Repo.db.collection("pairs").document(pair!!).collection("improvementRequests")
            .orderBy("createdAt",Query.Direction.DESCENDING).limit(40).addSnapshotListener{s,e->
                if(e!=null)message="No se pudieron cargar las solicitudes" else items=s?.documents.orEmpty().map{it.getString("text").orEmpty() to it.getString("state").orEmpty()}
            } else null
        onDispose{listener?.remove()}
    }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=onBack){Text("Volver a Administración")}
        if(!admin){Text("Solo el administrador puede acceder");return@Column}
        SettingsHero("Tus solicitudes","Describe qué pasó y qué esperabas. Las correcciones no liberan versiones por sí solas.","SOLO PARA TI")
        OutlinedTextField(text,{if(it.length<=2000)text=it},label={Text("¿Qué quieres mejorar?")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Button(enabled=text.isNotBlank()&&!busy,onClick={val p=pair;val u=uid;if(p!=null&&u!=null)scope.launch{
            busy=true;message=runCatching{
                Repo.db.collection("pairs").document(p).collection("improvementRequests").add(mapOf("uid" to u,"text" to text.trim(),"state" to "received","version" to BuildConfig.VERSION_NAME,"createdAt" to FieldValue.serverTimestamp())).await()
                text="";"Solicitud guardada. Pendiente de revisión; todavía no activa un agente automáticamente."
            }.getOrElse{"No se pudo guardar. Revisa tu conexión."};busy=false
        }}){Text("Guardar solicitud")}
        message?.let{Text(it,color=C.me)}
        SectionLabel("Asistente IA")
        Text("Todavía no conectado. Falta configurar un proveedor y un servidor seguro; no se ha activado ningún servicio de pago.",color=C.sub)
        OutlinedButton(enabled=text.isNotBlank(),onClick={clipboard.setText(AnnotatedString("Nosotros ${BuildConfig.VERSION_NAME}: ${text.trim()}"));message="Copiado. Puedes pegarlo en tu chat de Codex para revisarlo."}){Text("Copiar para revisar con Codex")}
        SectionLabel("Solicitudes guardadas")
        if(items.isEmpty())Text("Todavía no hay solicitudes",color=C.sub)
        items.forEach{(body,state)->Card{Column(Modifier.fillMaxWidth().padding(14.dp)){
            Text(if(state=="received")"Recibida · pendiente de revisión" else state,color=C.me)
            Text(body)
        }}}
    }
}
