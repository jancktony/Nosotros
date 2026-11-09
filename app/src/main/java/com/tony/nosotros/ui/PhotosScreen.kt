package com.tony.nosotros.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tony.nosotros.data.Photo
import com.tony.nosotros.util.TimeFmt
import java.io.ByteArrayOutputStream
import java.io.File

private fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val big = maxOf(info.size.width, info.size.height)
        if (big > maxSide) d.setTargetSize(info.size.width * maxSide / big, info.size.height * maxSide / big)
    }

private fun jpeg(b: Bitmap, maxBytes: Int): ByteArray {
    var q = 80
    while (true) {
        val out = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        if (out.size <= maxBytes || q <= 30) return out
        q -= 10
    }
}

/** Fotos que se mandan entre los dos, con el lugar donde se tomaron. */
@Composable
fun PhotosScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val photos by vm.photos.collectAsState()
    val uid by vm.uid.collectAsState()
    val partner by vm.partner.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val sending by vm.sending.collectAsState()
    var picked by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<Photo?>(null) }
    val camUri = remember {
        val f = File(ctx.cacheDir, "cam").apply { mkdirs() }.resolve("foto.jpg")
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) picked = it }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) picked = camUri }

    viewing?.let { ph ->
        BackHandler { viewing = null }
        Viewer(vm, ph, canDelete = ph.uid == uid || isAdmin, onClose = { viewing = null })
        return
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Fotos", style = MaterialTheme.typography.headlineMedium) }
            item { Text("Mándense fotos de donde están o de lo que ven. Cada foto guarda el lugar y la hora.", color = C.sub, fontSize = 14.sp) }
            if (sending) item { Text("Enviando foto…", color = C.me, fontWeight = FontWeight.SemiBold) }
            if (photos.isEmpty()) item { Text("Aún no hay fotos. ¡Manda la primera!", color = C.sub, fontSize = 14.sp) }
            items(photos, key = { it.id }) { ph ->
                val mine = ph.uid == uid
                val bmp = remember(ph.id) { BitmapFactory.decodeByteArray(ph.thumb, 0, ph.thumb.size) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).clickable { viewing = ph }) {
                    if (bmp != null) Image(bmp.asImageBitmap(), ph.caption, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (ph.caption.isNotBlank()) Text(ph.caption, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(if (mine) "Tú" else partner?.name?.ifBlank { null } ?: "Tu pareja",
                            ph.place?.let { "en $it" }, TimeFmt.ago(ph.at)).joinToString(" · "),
                            color = if (mine) C.me else C.partner, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExtendedFloatingActionButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                containerColor = C.surface2, contentColor = C.text, icon = { Icon(Ic.Layers, null) }, text = { Text("Galería") })
            ExtendedFloatingActionButton(onClick = { camera.launch(camUri) },
                containerColor = C.me, contentColor = C.meInk, icon = { Icon(Ic.Plus, null) }, text = { Text("Cámara", fontWeight = FontWeight.Bold) })
        }
    }

    picked?.let { uri ->
        AlertDialog(
            onDismissRequest = { picked = null }, containerColor = C.surface,
            title = { Text("Enviar foto") },
            text = { OutlinedTextField(caption, { caption = it.take(120) }, label = { Text("Escribe algo (opcional)") }) },
            confirmButton = { TextButton(onClick = {
                runCatching { jpeg(load(ctx, uri, 640), 90_000) to jpeg(load(ctx, uri, 1600), 850_000) }
                    .onSuccess { (thumb, full) -> vm.sendPhoto(thumb, full, caption.trim()) }
                picked = null; caption = ""
            }) { Text("Enviar", color = C.me) } },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Cancelar", color = C.sub) } },
        )
    }
}

@Composable
private fun Viewer(vm: MainViewModel, ph: Photo, canDelete: Boolean, onClose: () -> Unit) {
    var bmp by remember { mutableStateOf(BitmapFactory.decodeByteArray(ph.thumb, 0, ph.thumb.size)) }
    LaunchedEffect(ph.id) { vm.fullPhoto(ph.id)?.let { bmp = BitmapFactory.decodeByteArray(it, 0, it.size) } }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose)) {
        bmp?.let { Image(it.asImageBitmap(), ph.caption, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (ph.caption.isNotBlank()) Text(ph.caption, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(ph.place, TimeFmt.hour(ph.at)).joinToString(" · "), color = Color(0xFFD1D5DB), fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (canDelete) TextButton(onClick = { vm.deletePhoto(ph.id); onClose() }) { Text("Borrar", color = Color(0xFFFCA5A5)) }
        }
    }
}
