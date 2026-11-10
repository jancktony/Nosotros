package com.tony.nosotros.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

fun decodeAvatar(encoded: String): Bitmap? = runCatching {
    if (encoded.isBlank() || encoded.length > 90_000) return null
    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private suspend fun encodeAvatar(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val original = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val largest = maxOf(info.size.width, info.size.height)
        if (largest > 384) decoder.setTargetSize(maxOf(1, info.size.width * 384 / largest), maxOf(1, info.size.height * 384 / largest))
    }
    val side = minOf(original.width, original.height)
    val crop = Bitmap.createBitmap(original, (original.width - side) / 2, (original.height - side) / 2, side, side)
    var quality = 85
    var bytes: ByteArray
    do {
        bytes = ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        quality -= 10
    } while (bytes.size > 60_000 && quality >= 25)
    require(bytes.size <= 60_000) { "Elige otra foto más pequeña." }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
fun ProfilePhotoControls(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by vm.me.collectAsState()
    val busy by vm.profileBusy.collectAsState()
    val message by vm.profileMessage.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            vm.profileBusy.value = true
            runCatching { encodeAvatar(ctx, uri) }
                .onSuccess { vm.setAvatar(it) }
                .onFailure { vm.profileBusy.value = false; vm.profileMessage.value = "No se pudo leer la foto. Prueba con otra imagen." }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(initialOf(me?.name ?: vm.prefs.myName, "Yo"), C.me, C.meInk, 64.dp, photo = me?.avatar ?: vm.prefs.myAvatar)
        TextButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (busy) "Guardando…" else "Cambiar foto de perfil", color = C.me)
        }
    }
    if (!me?.avatar.isNullOrBlank()) TextButton(enabled = !busy, onClick = { vm.setAvatar("") }) { Text("Eliminar foto", color = C.danger) }
    message?.let { Text(it, color = C.sub, fontSize = 13.sp) }
}
