import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
const $=id=>document.getElementById(id), auth=getAuth(initializeApp(window.FB));
const paths={
map:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2ZM9 3v16M15 5v16"/>',
locate:'<circle cx="12" cy="12" r="6"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3"/>',
mic:'<rect x="8" y="2" width="8" height="13" rx="4"/><path d="M5 10v2a7 7 0 0 0 14 0v-2m-7 9v3m-4 0h8"/>',
more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
settings:'<path d="m9 3 1-1h4l1 3 3 1 3-1 2 4-2 2v3l2 2-2 4-3-1-3 1-1 3h-4l-1-3-3-1-3 1-2-4 2-2v-3L2 9l2-4 3 1Z"/><circle cx="12" cy="12" r="3"/>',
sliders:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="10" cy="18" r="2"/>',
shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/>',
photo:'<rect x="3" y="4" width="18" height="16" rx="4"/><circle cx="8" cy="9" r="1.5"/><path d="m4 18 5-5 3 3 4-6 5 7"/>',
history:'<path d="M3 10a9 9 0 1 1 0 4M3 4v6h6m3-4v6l4 2"/>',
pin:'<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2"/>',
search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',plus:'<path d="M12 4v16M4 12h16"/>',
phone:'<rect x="6" y="2" width="12" height="20" rx="3"/><path d="M10 5h4m-3 14h2"/>',
bell:'<path d="M5 17h14l-2-4V8a5 5 0 0 0-10 0v5ZM10 21h4"/>',
edit:'<path d="m4 16 12-12 4 4L8 20H4ZM14 6l4 4"/>',
route:'<path d="m3 10 18-7-7 18-3-8Z"/>',heart:'<path d="M20 5c-3-3-6-1-8 1-2-2-5-4-8-1-5 5 8 15 8 15S25 10 20 5Z"/>'};
const icon=n=>'<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">'+(paths[n]||paths.heart)+'</svg>';
function icons(root=document){root.querySelectorAll('[data-icon]').forEach(e=>{if(!e.querySelector('svg'))e.insertAdjacentHTML('afterbegin',icon(e.dataset.icon))})}
let current='map',map=null,mapReady=false,routeTimer=null,tourTimer=null,toastTimer=null,releaseStage=0;
const sampleCenter=[-74.823,11.004], sampleRoute=[sampleCenter,[-74.8217,11.0047],[-74.8209,11.0035],[-74.8195,11.0041]];
const pages=[['map','Mapa','map'],['find','Encontrar','locate'],['history','Historial','history'],['places','Lugares','pin'],['photos','Momentos','photo'],['voice','Voz','mic'],['settings','Ajustes','settings'],['admin','Administración','shield']];
$('desktop-nav').innerHTML=pages.map(([id,label,i])=>'<button data-go="'+id+'">'+icon(i)+'<span>'+label+'</span></button>').join('');
$('bottom-nav').innerHTML=[['map','Mapa','map'],['find','Encontrar','locate'],['voice','Voz','mic'],['more','Más','more']].map(([id,label,i])=>'<button data-go="'+id+'">'+icon(i)+'<span>'+label+'</span></button>').join('');
const titles={map:'Más cerca, siempre.',find:'Encontrar',voice:'Walkie-talkie',history:'Su historia',places:'Sus lugares',photos:'Momentos',more:'Su espacio',settings:'Ajustes',admin:'Administración'};
function page(id){if(!titles[id])return;stopMic();current=id;document.querySelectorAll('.screen').forEach(e=>e.hidden=e.id!=='screen-'+id);$('page-title').textContent=titles[id];document.querySelectorAll('[data-go]').forEach(e=>e.classList.toggle('active',e.dataset.go===id||(e.closest('.bottom-nav')&&e.dataset.go==='more'&&!['map','find','voice'].includes(id))));if(id==='map'&&!$('preview').hidden)requestAnimationFrame(()=>{initMap();map?.resize()})}
function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').hidden=false;toastTimer=setTimeout(()=>$('toast').hidden=true,4000)}
function modal(html){$('dialog-body').innerHTML=html;if(!$('detail-dialog').open)$('detail-dialog').showModal()}
$('close-dialog').onclick=()=>$('detail-dialog').close();
$('detail-dialog').onclick=e=>{if(e.target===$('detail-dialog'))$('detail-dialog').close()};
function sheet(mode){$('person-sheet').classList.toggle('minimized',mode==='hidden');$('restore-sheet').hidden=mode!=='hidden';$('person-sheet').querySelector('.sheet-details').hidden=mode!=='expanded';$('sheet-toggle').setAttribute('aria-expanded',String(mode==='expanded'))}
$('sheet-toggle').onclick=()=>sheet($('sheet-toggle').getAttribute('aria-expanded')==='true'?'collapsed':'expanded');
$('restore-sheet').onclick=()=>sheet('collapsed');
$('select-me').onclick=()=>selectPerson(true);$('select-kelin').onclick=()=>selectPerson(false);
function selectPerson(me){$('person-name').textContent=me?'Jancktony':'Kelin';$('person-avatar').textContent=me?'J':'K';$('person-avatar').className='avatar '+(me?'jan':'kel');$('select-me').classList.toggle('selected',me);$('select-kelin').classList.toggle('selected',!me);sheet('collapsed')}
function initMap(){
 if(map||!window.maplibregl){if(!window.maplibregl){$('fallback-pair').hidden=false;$('map-hint').textContent='Fondo de ejemplo · no se pudo cargar el mapa interactivo.';$('map-hint').hidden=false}return}
 try{
  map=new window.maplibregl.Map({container:'real-map',style:'https://tiles.openfreemap.org/styles/dark',center:sampleCenter,zoom:15.7,pitch:0,bearing:-18,attributionControl:true});
  map.on('dragstart',()=>sheet('hidden'));map.on('zoomstart',e=>{if(e.originalEvent)sheet('hidden')});
  map.on('load',()=>{
   mapReady=true;$('fallback-pair').hidden=true;
   const el=document.createElement('div');el.className='pair-marker';el.innerHTML='<div class="hearts" aria-hidden="true">♥ ♡ ♥</div><div class="pair-avatars"><span class="avatar jan">J</span><span class="avatar kel">K</span></div><span class="joined-label">Juntos · ejemplo</span>';
   new window.maplibregl.Marker({element:el,anchor:'center'}).setLngLat(sampleCenter).addTo(map);
   const layer=map.getStyle().layers.find(l=>l.type==='fill'&&l['source-layer']==='building');
   if(layer&&!map.getLayer('preview-buildings'))map.addLayer({id:'preview-buildings',type:'fill-extrusion',source:layer.source,'source-layer':'building',minzoom:15,layout:{visibility:'none'},paint:{'fill-extrusion-color':'#6b818a','fill-extrusion-height':['coalesce',['get','render_height'],['get','height'],8],'fill-extrusion-base':['coalesce',['get','render_min_height'],0],'fill-extrusion-opacity':1}},map.getStyle().layers.find(l=>l.type==='symbol')?.id);
  });
  map.on('error',()=>{if(!mapReady){$('fallback-pair').hidden=false;$('map-hint').textContent='El mapa de ejemplo está tardando en cargar.';$('map-hint').hidden=false}});
 }catch{map=null;$('fallback-pair').hidden=false}
}
$('center-map').onclick=()=>{map?.easeTo({center:sampleCenter,zoom:15.7,duration:500});sheet('collapsed')};
$('toggle-3d').onclick=()=>{if(!mapReady){toast('El mapa interactivo todavía está cargando.');return}const on=$('toggle-3d').getAttribute('aria-pressed')!=='true';$('toggle-3d').setAttribute('aria-pressed',String(on));$('toggle-3d').textContent=on?'2D':'3D';if(map.getLayer('preview-buildings'))map.setLayoutProperty('preview-buildings','visibility',on?'visible':'none');map.easeTo({pitch:on?45:0,duration:500});toast('Vista web de ejemplo. La fluidez de Android se prueba en el teléfono.')};
function endRoute(){clearInterval(routeTimer);routeTimer=null;$('route-card').hidden=true;if(mapReady){if(map.getLayer('sample-route'))map.removeLayer('sample-route');if(map.getSource('sample-route'))map.removeSource('sample-route')}sheet('collapsed')}
$('route').onclick=()=>{if(!mapReady){toast('Espera a que cargue el mapa de ejemplo.');return}endRoute();sheet('hidden');$('route-card').hidden=false;let step=0;const lines=['Continúa por la vía marcada','Gira a la derecha','Sigue hasta el punto de ejemplo','Llegaste · simulación completada'];map.addSource('sample-route',{type:'geojson',data:{type:'Feature',properties:{},geometry:{type:'LineString',coordinates:sampleRoute}}});map.addLayer({id:'sample-route',type:'line',source:'sample-route',layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':'#8bbcf3','line-width':5}});
map.fitBounds([[-74.824,11.0028],[-74.8187,11.0052]],{padding:85,duration:600});
$('route-instruction').textContent=lines[0];$('route-eta').textContent='Recorrido simulado · 3 min';routeTimer=setInterval(()=>{step++;$('route-instruction').textContent=lines[Math.min(step,3)];$('route-eta').textContent=step>=3?'Sin navegación real':'Recorrido simulado · '+(3-step)+' min';if(step>=3){clearInterval(routeTimer);routeTimer=null}},3000)};
$('end-route').onclick=endRoute;
$('live-demo').onclick=()=>toast('Demostración: no se solicitó la ubicación real de nadie.');
function stopMic(){$('mic').setAttribute('aria-pressed','false');$('voice-status').innerHTML='<span class="live-dot"></span> '+($('reception').getAttribute('aria-checked')==='true'?'Canal de ejemplo listo':'Recepción de ejemplo apagada')}
function startMic(){if($('mic').disabled)return;$('mic').setAttribute('aria-pressed','true');$('voice-status').innerHTML='<span class="live-dot"></span> Simulación · no se graba audio'}
$('mic').onpointerdown=e=>{if($('mic').disabled)return;$('mic').setPointerCapture?.(e.pointerId);startMic()};
['pointerup','pointercancel','lostpointercapture','blur'].forEach(n=>$('mic').addEventListener(n,stopMic));
$('mic').onkeydown=e=>{if([' ','Enter'].includes(e.key)){e.preventDefault();startMic()}};
$('mic').onkeyup=e=>{if([' ','Enter'].includes(e.key))stopMic()};
$('reception').onclick=()=>{const on=$('reception').getAttribute('aria-checked')!=='true';$('reception').setAttribute('aria-checked',String(on));$('reception').classList.toggle('on',on);$('mic').disabled=!on;stopMic()};
$('voice-options').onclick=()=>{$('voice-options-panel').hidden=!$('voice-options-panel').hidden};
document.addEventListener('click',e=>{const go=e.target.closest('[data-go]');if(go){stopTour();page(go.dataset.go)}const mock=e.target.closest('.demo-action');if(mock)toast(mock.dataset.message);const sw=e.target.closest('.switch:not(#reception)');if(sw){const on=sw.getAttribute('aria-checked')!=='true';sw.setAttribute('aria-checked',String(on));sw.classList.toggle('on',on);toast('Preferencia modificada solo en esta propuesta.')}});
function timeline(yesterday=false){const items=yesterday?[['Llegó al parque','Parque de ejemplo · Barranquilla','7:20 p. m.'],['Salió de la universidad','Universidad de ejemplo · Barranquilla','5:10 p. m.']]:[['Llegó a Casa','Lugar de ejemplo · Barranquilla','6:45 p. m.'],['Salió de la universidad','Universidad de ejemplo · Barranquilla','5:30 p. m.'],['Llegó a la universidad','Universidad de ejemplo · Barranquilla','8:10 a. m.']];$('timeline').innerHTML=items.map(([title,address,time])=>'<article class="timeline-item"><span class="timeline-icon">'+icon('pin')+'</span><div class="timeline-copy"><h3>'+title+'</h3><p>'+address+'</p><small>Registro ficticio</small></div><span class="timeline-time">'+time+'</span></article>').join('')}
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===b));timeline(b.dataset.filter==='yesterday')});timeline();
const places=[['Casa','Avisos de llegada y salida · ejemplo'],['Universidad','Lugar compartido · ejemplo'],['Parque','Punto de encuentro · ejemplo']];
function renderPlaces(q=''){const results=places.filter(p=>p[0].toLowerCase().includes(q.toLowerCase()));$('place-list').innerHTML=results.length?results.map(([name,copy])=>'<article class="glass place-item">'+icon('pin')+'<div><h3>'+name+'</h3><p>'+copy+'</p></div><button data-go="map" aria-label="Ver '+name+' en el mapa">'+icon('route')+'</button></article>').join(''):'<p>No hay lugares de ejemplo que coincidan.</p>'}
$('place-search').oninput=e=>renderPlaces(e.target.value);renderPlaces();
$('add-place').onclick=()=>{if(places.some(p=>p[0]==='Nuevo lugar')){toast('El lugar de ejemplo ya está añadido.');return}places.push(['Nuevo lugar','Añadido solo a la demostración']);$('place-search').value='';renderPlaces();toast('Lugar de ejemplo añadido; no se guardó en la app real.')};
const memories=['Un día juntos','Nuestro rincón','Una escapada','Ese atardecer','Lo cotidiano','Tú y yo'];$('gallery').innerHTML=memories.map((name,i)=>'<button class="memory-tile" data-memory="'+i+'"><span aria-hidden="true">'+['♡','✧','☼','☾','❋','∞'][i]+'</span><div><b>'+name+'</b><small>Tarjeta de ejemplo</small></div></button>').join('');
$('gallery').onclick=e=>{const b=e.target.closest('[data-memory]');if(b)modal('<div class="dialog-art">♡</div><h2>'+memories[Number(b.dataset.memory)]+'</h2><p>Esta tarjeta muestra cómo se abriría un recuerdo. No se han cargado fotos personales.</p>')};
$('more-grid').innerHTML=[['photos','Momentos','Su galería','photo'],['history','Historial','Sus recorridos','history'],['places','Lugares','Sus puntos favoritos','pin'],['settings','Ajustes','Todo a su manera','settings'],['admin','Administración','Solo el creador','shield']].map(([id,title,copy,i])=>'<button class="glass more-card" data-go="'+id+'"><span>'+icon(i)+'</span><span><b>'+title+'</b><small>'+copy+'</small></span></button>').join('');
const settings={
General:[['Apariencia','Colores del sistema','select'],['Transparencia ligera','Paneles de cristal de la propuesta','on'],['Movimiento reducido','Se respeta también la preferencia del sistema','off']],
Perfil:[['Nombre visible','Jancktony Murgas','edit'],['Foto de perfil','Propuesta de avatar; sin fotos personales cargadas','info']],
Actividad:[['Compartir actividad','Siempre con consentimiento de cada persona','on'],['Estado de descanso','Estado de ejemplo, sin lecturas del teléfono','off']],
Ubicación:[['Compartir ubicación','Estado de demostración','on'],['3D ligero','Edificios cercanos y menos efectos durante gestos','on']],
Widgets:[['Días juntos','Vista previa del contador','widget'],['Cercanía','Juntos · datos de ejemplo','widget']],
Avisos:[['Llegadas','Solo lugar y acción · ejemplo','on'],['Salidas','Hora de ejemplo en formato de 12 horas','on'],['Aviso de segundo plano','Android controla la visibilidad real de esta categoría','info']]};
function row(title,copy,type){return '<div class="setting-row"><div><b>'+title+'</b><small>'+copy+'</small></div>'+(type==='on'||type==='off'?'<button class="switch '+(type==='on'?'on':'')+'" role="switch" aria-checked="'+(type==='on')+'" aria-label="'+title+' de ejemplo"></button>':type==='select'?'<select id="appearance" aria-label="Apariencia"><option>Automática</option><option>Verde noche</option><option>Rosa suave</option></select>':'<button class="setting-action" data-action="'+type+'">'+(type==='edit'?'Editar':type==='widget'?'Ver':'Detalle')+'</button>')+'</div>'}
function settingCategory(name){document.querySelectorAll('[data-setting]').forEach(b=>b.classList.toggle('active',b.dataset.setting===name));$('settings-detail').innerHTML=settings[name].map(v=>row(...v)).join('')}
$('settings-categories').innerHTML=Object.keys(settings).map(n=>'<button data-setting="'+n+'">'+n+'</button>').join('');
$('settings-categories').onclick=e=>{const b=e.target.closest('[data-setting]');if(b)settingCategory(b.dataset.setting)};settingCategory('General');
$('settings-detail').onchange=e=>{if(e.target.id==='appearance'){document.documentElement.style.setProperty('--glass',e.target.value==='Rosa suave'?'#42304482':'#1c303b82');toast('Apariencia aplicada solo a esta propuesta.')}};
function editProfile(){modal('<h2>Tu nombre</h2><p>Solo cambia el nombre en esta demostración.</p><label>Nombre de ejemplo<input id="demo-name" maxlength="40" autocomplete="off"></label><button id="save-demo-name" class="primary" style="margin-top:15px">Guardar en la propuesta</button>');$('demo-name').value=$('profile-name').textContent;$('save-demo-name').onclick=()=>{const v=$('demo-name').value.trim();if(v)$('profile-name').textContent=v;$('detail-dialog').close();toast('Nombre de ejemplo actualizado.')}};
$('edit-profile').onclick=editProfile;
$('settings-detail').onclick=e=>{const b=e.target.closest('.setting-action');if(!b)return;if(b.dataset.action==='edit')editProfile();else if(b.dataset.action==='widget')modal('<div class="glass days"><small>Widget de ejemplo</small><b>1343 <span>días juntos</span></b><span>Juntos · no es una lectura real</span></div>');else modal('<h2>Tu control, siempre.</h2><p>Las preferencias reales se cambian en la aplicación instalada. Esta propuesta no solicita permisos ni modifica notificaciones del teléfono.</p>')};
$('feature-toggles').innerHTML=[['Walkie-talkie','Acceso de ejemplo'],['Juntos animado','Corazones y perfiles unidos'],['Mapa 3D','Vista de ejemplo']].map(([a,b])=>row(a,b,'on')).join('');
$('release-next').onclick=()=>{releaseStage=(releaseStage+1)%4;const labels=['Simular instalar solo para mí','Simular aprobar prueba','Simular liberar a Kelin','Reiniciar demostración'];const states=['1.15.6 · candidata de demostración','Prueba de ejemplo instalada solo para el dueño','Prueba de ejemplo aprobada · lista para liberar','Liberación simulada completada'];$('release-next').textContent=labels[releaseStage];$('release-status').textContent=states[releaseStage];['test','review','release'].forEach((s,i)=>$('step-'+s).classList.toggle('active',i===Math.min(releaseStage,2)));$('release-help').textContent='No se instaló, aprobó ni liberó ninguna versión real.';toast('Paso simulado. La Administración real no se modificó.')};
function stopTour(){clearInterval(tourTimer);tourTimer=null;$('tour').textContent='▷ Recorrido automático';stopMic()}
$('tour').onclick=()=>{if(tourTimer){stopTour();return}let step=0;const actions=[()=>{page('map');selectPerson(false);sheet('expanded')},()=>{sheet('hidden');toast('La ficha se recoge al mover el mapa.')},()=>{page('voice');startMic()},()=>{stopMic();page('photos')},()=>{page('settings');settingCategory('Ubicación')},()=>{page('admin')},()=>{page('map');sheet('collapsed');stopTour()}];actions[0]();$('tour').textContent='Ⅱ Detener recorrido';tourTimer=setInterval(()=>{step++;actions[Math.min(step,actions.length-1)]()},3500)};
$('mobile').onclick=()=>{document.body.classList.add('compact');setTimeout(()=>map?.resize(),50)};
$('wide').onclick=()=>{document.body.classList.remove('compact');setTimeout(()=>map?.resize(),50)};
document.addEventListener('visibilitychange',()=>{if(document.hidden){stopMic();stopTour();endRoute()}});
$('signin').onsubmit=async e=>{e.preventDefault();$('error').textContent='';try{await setPersistence(auth,browserSessionPersistence);await signInWithEmailAndPassword(auth,$('email').value.trim(),$('password').value)}catch{$('error').textContent='No se pudo entrar con esa cuenta de Nosotros.'}finally{$('password').value=''}};
onAuthStateChanged(auth,async u=>{const ok=!!(u?.emailVerified&&u.email?.toLowerCase()==='jancktoni@gmail.com');$('login').hidden=ok;$('preview').hidden=!ok;if(ok)requestAnimationFrame(initMap);else{stopTour();endRoute()}if(u&&!ok){$('error').textContent='Propuesta privada del creador.';await signOut(auth)}});
$('logout').onclick=()=>signOut(auth);
icons();page('map');
