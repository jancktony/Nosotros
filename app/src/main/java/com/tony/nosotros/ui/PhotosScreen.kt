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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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

/** Private shared album: thumbnails, filters and a full-screen paged viewer. */
@Composable
fun PhotosScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val photos by vm.photos.collectAsState()
    val uid by vm.uid.collectAsState()
    val partner by vm.partner.collectAsState()
    val admin by vm.isAdmin.collectAsState()
    val sending by vm.sending.collectAsState()
    val moreBusy by vm.morePhotosBusy.collectAsState()
    val moreAvailable by vm.morePhotosAvailable.collectAsState()
    var filter by remember { mutableStateOf("Todas") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val visible = photos.filter { filter == "Todas" || if (filter == "Mis fotos") it.uid == uid else it.uid != uid }
    val partnerName = partner?.name?.ifBlank { null } ?: "Tu pareja"
    val cameraUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(ctx.cacheDir,"cam").apply { mkdirs() }.resolve("foto.jpg")) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) picked = it }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if (it) picked = cameraUri }
    val month = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("es")) }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    SettingsHero("Sus recuerdos", "${photos.size} momentos compartidos · solo ustedes", "EL ÁLBUM DE USTEDES")
                    PanelTabs(listOf("Todas", "Mis fotos", "De $partnerName"), filter) { filter = it }
                    if (processing || sending) Text("Preparando su recuerdo…", color = C.me, fontSize = 12.sp)
                    error?.let { Text(it, color = C.warn, fontSize = 12.sp) }
                    if (visible.isEmpty()) Text("El primer recuerdo los está esperando. Elige una foto o abre la cámara.", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 25.dp))
                }
            }
            visible.groupBy { month.format(java.util.Date(it.at)) }.forEach { (date, group) ->
                item(key = "month-$date", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(date.replaceFirstChar { it.titlecase() }, color = C.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 17.dp, bottom = 7.dp))
                }
                items(group.size, key = { group[it].id }) { index ->
                    val photo = group[index]
                    val image = remember(photo.id) { BitmapFactory.decodeByteArray(photo.thumb,0,photo.thumb.size) }
                    Box(Modifier.aspectRatio(.88f).clip(RoundedCornerShape(15.dp)).background(C.surface2).clickable { viewing = photo.id }) {
                        image?.let { Image(it.asImageBitmap(),photo.caption.ifBlank { "Foto compartida" }, Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
                        if (photo.caption.isNotBlank()) Text(photo.caption, color=Color.White,fontSize=10.sp,maxLines=2,
                            modifier=Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha=.4f)).padding(9.dp))
                    }
                }
            }
            if (moreAvailable && photos.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TextButton(enabled = !moreBusy, onClick = {vm.loadOlderPhotos()}) { Text(if(moreBusy) "Cargando…" else "Ver recuerdos anteriores", color=C.me) }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(88.dp)) }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            ExtendedFloatingActionButton(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},containerColor=C.surface2,contentColor=C.text,
                icon={Icon(Ic.Layers,null)},text={Text("Elegir foto")})
            ExtendedFloatingActionButton(onClick={camera.launch(cameraUri)},containerColor=C.me,contentColor=C.meInk,icon={Icon(Ic.Plus,null)},text={Text("Cámara")})
        }
    }
    viewing?.let { id ->
        if (visible.isNotEmpty()) AlbumViewer(visible,id,loadFull={vm.fullPhoto(it)},canDelete={it.uid==uid||admin},onDelete={vm.deletePhoto(it)},onClose={viewing=null})
        else viewing=null
    }
    picked?.let { uri -> AlertDialog(onDismissRequest={if(!processing)picked=null},title={Text("Un recuerdo para ustedes")},
        text={Column { OutlinedTextField(caption,{caption=it.take(120)},label={Text("Escribe algo, si quieres")});error?.let{Text(it,color=C.warn)} }},
        confirmButton={TextButton(enabled=!processing,onClick={
            processing=true;error=null
            scope.launch {
                runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    jpeg(load(ctx,uri,640),90_000) to jpeg(load(ctx,uri,1600),850_000)
                } }.onSuccess { (thumb,full) -> vm.sendPhoto(thumb,full,caption.trim());picked=null;caption="" }
                    .onFailure { error="No se pudo preparar esta foto. Prueba con otra." }
                processing=false
            }
        }){Text(if(processing)"Preparando…" else "Compartir recuerdo")}},dismissButton={TextButton(enabled=!processing,onClick={picked=null}){Text("Cancelar")}}) }
}

@Composable
internal fun AlbumViewer(photos: List<Photo>, initialId: String, loadFull: suspend (String)->ByteArray?, canDelete:(Photo)->Boolean,
                         onDelete:(String)->Unit,onClose:()->Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage=photos.indexOfFirst{it.id==initialId}.coerceAtLeast(0),pageCount={photos.size})
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val zoomed = remember { androidx.compose.runtime.mutableStateMapOf<String,Boolean>() }
    var confirmDelete by remember { mutableStateOf<Photo?>(null) }
    val current = photos.getOrNull(pager.currentPage)
    androidx.compose.ui.window.Dialog(onDismissRequest=onClose,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick=onClose){Icon(Ic.Close,"Cerrar galería",tint=Color.White)}
                Text("${pager.currentPage+1} / ${photos.size}",Modifier.weight(1f),color=Color.White,fontSize=13.sp)
                if(current!=null&&canDelete(current)) TextButton(onClick={confirmDelete=current}){Text("Borrar",color=Color(0xFFFCA5A5))}
            }
            androidx.compose.foundation.pager.HorizontalPager(state=pager,key={photos[it].id},userScrollEnabled=zoomed[current?.id]!=true,modifier=Modifier.weight(1f)) { index ->
                val photo=photos[index]
                AlbumPage(photo,loadFull,index==pager.currentPage){zoomed[photo.id]=it}
            }
            current?.let { photo -> Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(photo.caption.ifBlank {"Un momento juntos"},color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                Text(listOfNotNull(photo.place,TimeFmt.hour(photo.at)).joinToString(" · "),color=Color.LightGray,fontSize=12.sp)
                Text("Pellizca para ampliar · doble toque para restablecer",color=Color.Gray,fontSize=10.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(enabled=pager.currentPage>0,onClick={scope.launch{pager.animateScrollToPage(pager.currentPage-1)}}){Text("Anterior",color=Color.White)}
                    TextButton(enabled=pager.currentPage<photos.lastIndex,onClick={scope.launch{pager.animateScrollToPage(pager.currentPage+1)}}){Text("Siguiente",color=Color.White)}
                }
            } }
        }
    }
    confirmDelete?.let { photo -> AlertDialog(onDismissRequest={confirmDelete=null},title={Text("¿Borrar este recuerdo?")},text={Text("Se quitará de su galería compartida.")},
        confirmButton={TextButton(onClick={onDelete(photo.id);confirmDelete=null;if(photos.size<=1)onClose()}){Text("Borrar",color=C.danger)}},dismissButton={TextButton(onClick={confirmDelete=null}){Text("Conservar")}}) }
}

@Composable
private fun AlbumPage(photo:Photo,loadFull:suspend(String)->ByteArray?,active:Boolean,onZoom:(Boolean)->Unit) {
    var bitmap by remember(photo.id){mutableStateOf(BitmapFactory.decodeByteArray(photo.thumb,0,photo.thumb.size))}
    var scale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(photo.id,retry){runCatching{loadFull(photo.id)}.onSuccess{bytes->if(bytes!=null)bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) else failed=true}.onFailure{failed=true}}
    LaunchedEffect(active){if(!active){scale=1f;offset=androidx.compose.ui.geometry.Offset.Zero};onZoom(scale>1.01f)}
    LaunchedEffect(scale){onZoom(scale>1.01f)}
    val transform=androidx.compose.foundation.gestures.rememberTransformableState { zoom,pan,_ ->
        scale=(scale*zoom).coerceIn(1f,4f)
        offset=if(scale==1f)androidx.compose.ui.geometry.Offset.Zero else androidx.compose.ui.geometry.Offset(
            (offset.x+pan.x).coerceIn(-size.width*(scale-1)/2,size.width*(scale-1)/2),
            (offset.y+pan.y).coerceIn(-size.height*(scale-1)/2,size.height*(scale-1)/2))
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged{size=it}.transformable(transform)
        .pointerInput(photo.id){detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2f;offset=androidx.compose.ui.geometry.Offset.Zero})},contentAlignment=Alignment.Center) {
        bitmap?.let{Image(it.asImageBitmap(),photo.caption,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y})}
        if(failed)TextButton(onClick={failed=false;retry++},modifier=Modifier.align(Alignment.BottomCenter)){Text("Reintentar foto completa",color=Color.White)}
    }
}
