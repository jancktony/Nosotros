import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
const $=id=>document.getElementById(id), auth=getAuth(initializeApp(window.FB));
const accents={mint:['Menta','#a1e4cf'],lilac:['Lavanda','#c7b7f3'],sky:['Cielo','#9fcff5'],coral:['Coral','#f5b8a6']};
let currentAccent=null,currentTheme='pink';
const themes={pink:['Rosa original · predeterminado','#f9f0f3','#a95476'],redblack:['Rojo y negro','#0b0b0d','#e6002e'],blackpink:['Negro y rosa','#120e13','#efc4d3'],fullpink:['Rosa completo','#fce4ef','#a95476']};
function applyTheme(id,remember=false){
 currentTheme=Object.hasOwn(themes,id)?id:'pink';
 document.documentElement.dataset.theme=currentTheme;
 document.documentElement.style.removeProperty('--accent');currentAccent=null;
 document.querySelectorAll('[data-accent]').forEach(b=>b.setAttribute('aria-pressed','false'));
 if(mapReady&&map.getLayer('sample-route'))map.setPaintProperty('sample-route','line-color',getComputedStyle(document.documentElement).getPropertyValue('--accent').trim());
 document.querySelectorAll('[data-theme-choice]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.themeChoice===currentTheme)));
 if(mapReady)tintExistingMap();
 if(remember){try{localStorage.setItem('nosotros-preview-theme',currentTheme)}catch{}toast('Tema guardado solo en este navegador. La apariencia de Kelin no cambia.')}
}
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
Object.assign(paths,{home:'<path d="m3 10 9-7 9 7v11H3ZM9 21v-7h6v7"/>',work:'<rect x="3" y="7" width="18" height="14" rx="3"/><path d="M8 7V4h8v3M3 13h18m-9 0v3"/>',turn:'<path d="M5 21V10a5 5 0 0 1 5-5h10m-5-4 5 4-5 4"/>',share:'<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m9 10 6-3M9 14l6 3"/>',volume:'<path d="M3 9h4l5-5v16l-5-5H3ZM16 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',check:'<path d="m5 12 5 5L20 7"/>'});
const icon=n=>'<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">'+(paths[n]||paths.heart)+'</svg>';
function icons(root=document){root.querySelectorAll('[data-icon]').forEach(e=>{if(!e.querySelector('svg'))e.insertAdjacentHTML('afterbegin',icon(e.dataset.icon))})}
const demoFeatures={walkie:true,together:true,map3d:true};
let current='map',map=null,mapReady=false,routeTimer=null,tourTimer=null,toastTimer=null,releaseStage=0;
const sampleCenter=[-74.823,11.004], sampleRoute=[sampleCenter,[-74.8217,11.0047],[-74.8209,11.0035],[-74.8195,11.0041]];
const pages=[['map','Mapa','map'],['find','Encontrar','locate'],['history','Historial','history'],['places','Lugares','pin'],['photos','Momentos','photo'],['voice','Voz','mic'],['settings','Ajustes','settings'],['admin','Administración','shield']];
$('desktop-nav').innerHTML=pages.map(([id,label,i])=>'<button data-go="'+id+'">'+icon(i)+'<span>'+label+'</span></button>').join('');
$('bottom-nav').innerHTML=[['map','Mapa','map'],['find','Encontrar','locate'],['voice','Voz','mic'],['more','Más','more']].map(([id,label,i])=>'<button data-go="'+id+'">'+icon(i)+'<span>'+label+'</span></button>').join('');
const navStyles={crystal:'Cristal',floating:'Flotante',raised:'Relieve'};
const navMotions={slide:'Deslizamiento',spring:'Rebote suave',fade:'Fundido',none:'Sin animación'};
function navPreference(key,choices,fallback){try{const value=localStorage.getItem(key);return Object.hasOwn(choices,value)?value:fallback}catch{return fallback}}
let navFade=null,navEffects=[],previousNavIndex=-1;
let navStyle=navPreference('nosotros-preview-nav-style',navStyles,'crystal'),navMotion=navPreference('nosotros-preview-nav-motion',navMotions,'slide');
const navSelection=document.createElement('span');navSelection.id='nav-selection';navSelection.setAttribute('aria-hidden','true');navSelection.innerHTML='<i class="nav-liquid-core"><i class="nav-liquid-sheen"></i></i>';$('bottom-nav').prepend(navSelection);
function updateNav(animate=false){
 const nav=$('bottom-nav'),buttons=[...nav.querySelectorAll('button')],index=Math.max(0,buttons.findIndex(b=>b.classList.contains('active')));
 document.documentElement.dataset.navStyle=navStyle;document.documentElement.dataset.navMotion=navMotion;nav.style.setProperty('--nav-index',index);
 buttons.forEach((b,i)=>{if(i===index)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current')});
 document.querySelectorAll('[data-nav-style-choice]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.navStyleChoice===navStyle)));
 navFade?.cancel();navFade=null;navEffects.forEach(a=>a?.cancel());navEffects=[];
 const changed=previousNavIndex!==index;previousNavIndex=index;
 if(animate&&changed&&navMotion!=='none'&&!(typeof matchMedia==='function'&&matchMedia('(prefers-reduced-motion: reduce)').matches)){
  const core=navSelection.querySelector('.nav-liquid-core'),sheen=navSelection.querySelector('.nav-liquid-sheen'),glyph=buttons[index].querySelector('.icon');
  if(navMotion!=='fade')navEffects.push(core.animate?.([{transform:'scale(1)'},{transform:'scale(1.13,.91)',offset:.32},{transform:'scale(1)'}],{duration:navMotion==='spring'?480:360,easing:'cubic-bezier(.22,1,.36,1)'}));
  navEffects.push(sheen.animate?.([{transform:'translateX(-120%)',opacity:0},{opacity:.65,offset:.35},{transform:'translateX(130%)',opacity:0}],{duration:420,easing:'ease-out'}));
  navEffects.push(glyph?.animate?.([{transform:'translateY(0) scale(1)'},{transform:'translateY(-3px) scale(1.12)',offset:.4},{transform:'translateY(0) scale(1)'}],{duration:360,easing:'cubic-bezier(.22,1,.36,1)'}));
 }
 if(animate&&navMotion==='fade'&&!matchMedia('(prefers-reduced-motion: reduce)').matches)navFade=navSelection.animate?.([{opacity:.15},{opacity:1}],{duration:200,easing:'ease-out'});
}
updateNav();
if(typeof matchMedia==='function')matchMedia('(prefers-reduced-motion: reduce)').addEventListener?.('change',()=>updateNav());
const titles={improvements:'Mejoras en prueba',map:'Más cerca, siempre.',find:'Encontrar',voice:'Walkie-talkie',history:'Su historia',places:'Sus lugares',photos:'Momentos',more:'Su espacio',settings:'Ajustes',admin:'Administración'};
function page(id){if(!titles[id])return;if(id!=='map'&&current==='map')endRoute();stopMic();current=id;document.body.dataset.screen=id;document.querySelectorAll('.screen').forEach(e=>e.hidden=e.id!=='screen-'+id);$('page-title').textContent=titles[id];document.querySelectorAll('[data-go]').forEach(e=>e.classList.toggle('active',e.dataset.go===id||(e.closest('.bottom-nav')&&e.dataset.go==='more'&&!['map','find','voice'].includes(id))));updateNav(true);if(id==='map'&&!$('preview').hidden)requestAnimationFrame(()=>{initMap();map?.resize();syncMapInsets()})}
function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').hidden=false;toastTimer=setTimeout(()=>$('toast').hidden=true,4000)}
function modal(html){$('dialog-body').innerHTML=html;if(!$('detail-dialog').open)$('detail-dialog').showModal()}
$('close-dialog').onclick=()=>$('detail-dialog').close();
$('detail-dialog').onclick=e=>{if(e.target===$('detail-dialog'))$('detail-dialog').close()};
function syncMapInsets(){
 if(current!=='map')return;
 const app=document.querySelector('.app'),mobile=document.body.classList.contains('compact')||innerWidth<=700;
 const navigating=app.classList.contains('navigating'),panel=navigating?$('route-panel'):$('person-sheet');
 const hidden=panel.hidden||panel.classList.contains('minimized');
 const height=document.querySelector('.stage').getBoundingClientRect().height;
 const bottom=hidden?100:panel.getBoundingClientRect().height+(mobile?114:40);
 const top=navigating?160:145,tools=document.querySelector('.map-tools');
 tools.hidden=navigating?!$('route-actions').hidden:panel.dataset.size==='expanded';
 if(!tools.hidden){
  const space=height-bottom-top;
  tools.classList.toggle('tools-horizontal',space<180);
  const size=tools.getBoundingClientRect().height;
  tools.style.setProperty('top',Math.max(top,height-bottom-size-4)+'px','important');
 }
 if(mapReady&&map?.setPadding)map.setPadding({top,bottom:Math.max(70,Math.min(bottom,height-top-110)),left:25,right:50});
}
function sheet(mode){
 const panel=$('person-sheet');panel.dataset.size=mode;
 panel.classList.toggle('minimized',mode==='hidden');
 $('restore-sheet').hidden=mode!=='hidden';
 panel.querySelector('.sheet-details').hidden=mode!=='expanded';
 $('sheet-toggle').setAttribute('aria-expanded',String(mode==='expanded'));
 $('sheet-toggle').setAttribute('aria-label',mode==='expanded'?'Reducir ficha':'Expandir ficha');
 requestAnimationFrame(syncMapInsets);
}
let sheetDragY=null,sheetDragged=false;
$('sheet-toggle').addEventListener('pointerdown',e=>{sheetDragY=e.clientY;sheetDragged=false;$('sheet-toggle').setPointerCapture?.(e.pointerId)});
$('sheet-toggle').addEventListener('pointerup',e=>{if(sheetDragY!==null&&Math.abs(e.clientY-sheetDragY)>30){sheetDragged=true;sheet(e.clientY>sheetDragY?'compact':'expanded');setTimeout(()=>sheetDragged=false,0)}sheetDragY=null});
$('sheet-toggle').addEventListener('pointercancel',()=>{sheetDragY=null});
$('sheet-toggle').onclick=()=>{if(sheetDragged){sheetDragged=false;return}sheet($('sheet-toggle').getAttribute('aria-expanded')==='true'?'compact':'expanded')};
$('restore-sheet').onclick=()=>sheet('collapsed');
$('select-me').onclick=()=>selectPerson(true);$('select-kelin').onclick=()=>selectPerson(false);
function batteryIcon(level){
 const value=Math.max(0,Math.min(100,Math.round(level)));
 return '<svg viewBox="0 0 40 22" aria-hidden="true"><defs><clipPath id="battery-level"><rect x="2" y="4" width="'+(30*value/100)+'" height="14" rx="3"/></clipPath></defs><rect x="1" y="3" width="32" height="16" rx="4" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M35 8v6" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/><rect x="2" y="4" width="'+(30*value/100)+'" height="14" rx="3" fill="currentColor"/><text x="17" y="15" text-anchor="middle" fill="var(--ink)">'+value+'</text><text x="17" y="15" text-anchor="middle" fill="var(--on-ui)" clip-path="url(#battery-level)">'+value+'</text></svg>';
}
function showBattery(level,name){const el=$('person-sheet').querySelector('.battery');el.innerHTML=batteryIcon(level);el.setAttribute('role','img');el.setAttribute('aria-label','Batería de '+name+': '+level+' % · ejemplo');el.title=level+' % · dato de ejemplo'}
showBattery(82,'Kelin');
function selectPerson(me){$('person-name').textContent=me?'Jancktony':'Kelin';$('person-avatar').textContent=me?'J':'K';$('person-avatar').className='avatar '+(me?'jan':'kel');showBattery(me?78:82,me?'Jancktony':'Kelin');$('person-sheet').querySelector('.activity-line b').textContent=me?'Inicio de One UI':'Instagram';const badge=$('person-sheet').querySelector('.app-badge');badge.innerHTML=me?icon('home'):'<img src="icons/instagram.svg" width="22" height="22" alt="Icono de Instagram">';badge.title=me?'Icono genérico de pantalla de inicio':'Icono de Instagram';$('select-me').classList.toggle('selected',me);$('select-kelin').classList.toggle('selected',!me);sheet('collapsed')}
let originalFillColors=null;
function tintExistingMap(){
 if(!originalFillColors)originalFillColors=new Map(map.getStyle().layers.filter(l=>l.type==='fill').map(l=>[l.id,l.paint?.['fill-color']??'#000000']));
 const light=['pink','fullpink'].includes(currentTheme);
 for(const layer of map.getStyle().layers){
  const id=layer.id.toLowerCase();
  if(layer.type==='background')map.setPaintProperty(layer.id,'background-color',light?'#f9f0f3':'#0e171e');
  if(layer.type==='line'&&/road|highway|street|bridge|tunnel/.test(id))map.setPaintProperty(layer.id,'line-color',light?'#cbaaba':/major|motorway|trunk|primary/.test(id)?'#45546A':'#313C4B');
  if(layer.type==='line'&&id.includes('building'))map.setPaintProperty(layer.id,'line-color',light?'#d4bec8':'#313c4b');
  if(layer.type==='fill'){
   if(id.includes('water'))map.setPaintProperty(layer.id,'fill-color',light?'#cee4eb':'#0F2B40');
   else if(/park|green|wood|grass/.test(id))map.setPaintProperty(layer.id,'fill-color',light?'#dce8d8':'#11291E');
   else if(id.includes('building'))map.setPaintProperty(layer.id,'fill-color',light?'#e4ced7':'#1A212B');
   else map.setPaintProperty(layer.id,'fill-color',light?'#f1e5eb':originalFillColors.get(layer.id));
  }
  if(layer.type==='symbol'&&layer.layout?.['text-field']){map.setPaintProperty(layer.id,'text-color',light?'#3b0a24':'#C7D2E2');map.setPaintProperty(layer.id,'text-halo-color',light?'#f9f0f3':'#070E17');map.setPaintProperty(layer.id,'text-halo-width',1.4)}
 }
}
function initMap(){
 if(map||!window.maplibregl){if(!window.maplibregl){$('fallback-pair').hidden=false;$('map-hint').textContent='Fondo de ejemplo · no se pudo cargar el mapa interactivo.';$('map-hint').hidden=false}return}
 try{
  map=new window.maplibregl.Map({container:'real-map',style:'https://tiles.openfreemap.org/styles/dark',center:sampleCenter,zoom:15.7,pitch:0,bearing:-18,attributionControl:true});
  map.on('dragstart',()=>sheet('hidden'));map.on('zoomstart',e=>{if(e.originalEvent)sheet('hidden')});
  map.on('load',()=>{
   mapReady=true;tintExistingMap();$('fallback-pair').hidden=true;$('map-fallback').hidden=true;$('map-hint').hidden=true;requestAnimationFrame(syncMapInsets);
   const el=document.createElement('div');el.className='pair-marker';el.innerHTML='<div class="hearts" aria-hidden="true">♥ ♡ ♥</div><div class="pair-avatars"><span class="avatar jan">J</span><span class="avatar kel">K</span></div><span class="joined-label">Juntos · ejemplo</span>';
   new window.maplibregl.Marker({element:el,anchor:'center'}).setLngLat(sampleCenter).addTo(map);applyFeatures();
   const layer=map.getStyle().layers.find(l=>l.type==='fill'&&l['source-layer']==='building');
   if(layer&&!map.getLayer('preview-buildings'))map.addLayer({id:'preview-buildings',type:'fill-extrusion',source:layer.source,'source-layer':'building',minzoom:15,layout:{visibility:'none'},paint:{'fill-extrusion-color':'#6b818a','fill-extrusion-height':['coalesce',['get','render_height'],['get','height'],8],'fill-extrusion-base':['coalesce',['get','render_min_height'],0],'fill-extrusion-opacity':1}},map.getStyle().layers.find(l=>l.type==='symbol')?.id);
  });
  map.on('error',()=>{if(!mapReady){$('fallback-pair').hidden=false;$('map-hint').textContent='El mapa de ejemplo está tardando en cargar.';$('map-hint').hidden=false}});
 }catch{map=null;$('fallback-pair').hidden=false}
}
$('center-map').onclick=()=>{if(document.querySelector('.app').classList.contains('navigating'))showRouteStep();else{map?.easeTo({center:sampleCenter,zoom:15.7,duration:500});sheet('collapsed')}};
$('toggle-3d').onclick=()=>{if(!mapReady){toast('El mapa interactivo todavía está cargando.');return}const on=$('toggle-3d').getAttribute('aria-pressed')!=='true';$('toggle-3d').setAttribute('aria-pressed',String(on));$('toggle-3d').textContent=on?'2D':'3D';if(map.getLayer('preview-buildings'))map.setLayoutProperty('preview-buildings','visibility',on?'visible':'none');map.easeTo({pitch:on?45:0,duration:500});toast('Vista web de ejemplo. La fluidez de Android se prueba en el teléfono.')};
let routeStep=0,demoPath=sampleRoute.slice(),stopAdded=false;
function remainingKm(){
 const points=demoPath.slice(routeStep);let km=0;
 for(let i=1;i<points.length;i++){
  const [x1,y1]=points[i-1],[x2,y2]=points[i],r=Math.PI/180;
  const a=Math.sin((y2-y1)*r/2)**2+Math.cos(y1*r)*Math.cos(y2*r)*Math.sin((x2-x1)*r/2)**2;
  km+=6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
 }
 return km;
}
function drawSampleRoute(){if(mapReady){
 const data={type:'Feature',properties:{},geometry:{type:'LineString',coordinates:demoPath}};
 if(map.getSource('sample-route'))map.getSource('sample-route').setData(data);
 else{map.addSource('sample-route',{type:'geojson',data});map.addLayer({id:'sample-route',type:'line',source:'sample-route',layout:{'line-join':'round','line-cap':'round'},paint:{'line-color':getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),'line-width':7}})}
}}
function showRouteStep(){
 const arrived=routeStep>=demoPath.length-1,km=remainingKm(),minutes=arrived?0:Math.max(1,Math.ceil(km/15*60));
 $('route-instruction').textContent=arrived?'Llegaste a tu destino':routeStep===0?'Continúa por la ruta':routeStep===1?'Gira a la derecha':'Sigue hasta tu destino';
 $('route-eta').textContent=arrived?'Recorrido de ejemplo completado':'Carrera 54 · indicación de ejemplo';
 const clockParts=new Intl.DateTimeFormat('es-CO',{hour:'numeric',minute:'2-digit',hour12:true}).formatToParts(new Date(Date.now()+minutes*60000));
 $('arrival-time').textContent=clockParts.filter(p=>p.type==='hour'||p.type==='minute'||(p.type==='literal'&&p.value.includes(':'))).map(p=>p.value).join('');
 $('arrival-period').textContent=clockParts.find(p=>p.type==='dayPeriod')?.value||'';
 $('remaining-minutes').textContent=minutes;
 $('remaining-distance').textContent=km.toLocaleString('es-CO',{minimumFractionDigits:1,maximumFractionDigits:1});
 if(mapReady)map.easeTo({center:demoPath[Math.min(routeStep,demoPath.length-1)],zoom:17,pitch:demoFeatures.map3d?40:0,duration:450});
 if(arrived){clearInterval(routeTimer);routeTimer=null;$('add-demo-stop').disabled=true}
}
function endRoute(){
 const wasNavigating=document.querySelector('.app').classList.contains('navigating');
 clearInterval(routeTimer);routeTimer=null;document.querySelector('.app').classList.remove('navigating');$('center-map').setAttribute('aria-label','Centrar a ambos');
 $('route-card').hidden=true;$('route-panel').hidden=true;$('fallback-route').hidden=true;
 $('route-actions').hidden=true;$('route-summary').setAttribute('aria-expanded','false');$('route-panel').classList.remove('expanded');
 if(mapReady){if(map.getLayer('sample-route'))map.removeLayer('sample-route');if(map.getSource('sample-route'))map.removeSource('sample-route');if(wasNavigating)map.easeTo({center:sampleCenter,zoom:15.7,pitch:$('toggle-3d').getAttribute('aria-pressed')==='true'?45:0,duration:450})}
 sheet('collapsed');
}
function startRoute(destination='Parque de ejemplo'){
 endRoute();sheet('hidden');document.querySelector('.app').classList.add('navigating');
 $('center-map').setAttribute('aria-label','Centrar ruta de ejemplo');
 $('route-card').hidden=false;$('route-panel').hidden=false;$('fallback-route').hidden=mapReady;
 $('route-destination').textContent=destination;routeStep=0;demoPath=sampleRoute.slice();stopAdded=false;
 $('add-demo-stop').disabled=false;$('add-demo-stop').innerHTML=icon('plus')+'Agregar parada';
 drawSampleRoute();showRouteStep();requestAnimationFrame(syncMapInsets);
 routeTimer=setInterval(()=>{routeStep++;showRouteStep()},4200);
}
$('route').onclick=()=>startRoute();$('end-route').onclick=endRoute;$('finish-route').onclick=endRoute;
$('route-summary').onclick=()=>{const open=$('route-summary').getAttribute('aria-expanded')!=='true';$('route-summary').setAttribute('aria-expanded',String(open));$('route-actions').hidden=!open;$('route-panel').classList.toggle('expanded',open);requestAnimationFrame(syncMapInsets)};
$('add-demo-stop').onclick=()=>{if(stopAdded)return;stopAdded=true;demoPath.splice(demoPath.length-1,0,[-74.8201,11.0031]);$('add-demo-stop').disabled=true;$('add-demo-stop').innerHTML=icon('check')+'Parada añadida · ejemplo';drawSampleRoute();showRouteStep();toast('Parada añadida solo a la ruta de demostración.')};
function destinationPreview(name){modal('<h2 id="destination-title"></h2><p>Simulación de búsqueda · no consulta direcciones reales.</p><button id="start-destination" class="primary">Ver recorrido de ejemplo</button>');$('destination-title').textContent=name;$('start-destination').onclick=()=>{$('detail-dialog').close();startRoute(name)}}
$('sheet-search').onsubmit=e=>{e.preventDefault();const q=$('map-search').value.trim();if(q)destinationPreview(q);else toast('Escribe un lugar para probar el recorrido.')};
document.querySelectorAll('[data-quick]').forEach(b=>b.onclick=()=>destinationPreview(b.dataset.quick));
if(window.ResizeObserver){const observer=new ResizeObserver(()=>{if(current==='map')requestAnimationFrame(syncMapInsets)});observer.observe(document.querySelector('.stage'));observer.observe($('person-sheet'));observer.observe($('route-panel'))}
$('live-demo').onclick=()=>toast('Demostración: no se solicitó la ubicación real de nadie.');
function stopMic(){$('mic').setAttribute('aria-pressed','false');$('voice-status').innerHTML='<span class="live-dot"></span> '+(!demoFeatures.walkie?'Voz desactivada en esta propuesta':$('reception').getAttribute('aria-checked')==='true'?'Canal de ejemplo listo':'Recepción de ejemplo apagada')}
function startMic(){if($('mic').disabled)return;$('mic').setAttribute('aria-pressed','true');$('voice-status').innerHTML='<span class="live-dot"></span> Simulación · no se graba audio'}
$('mic').onpointerdown=e=>{if($('mic').disabled)return;$('mic').setPointerCapture?.(e.pointerId);startMic()};
['pointerup','pointercancel','lostpointercapture','blur'].forEach(n=>$('mic').addEventListener(n,stopMic));
$('mic').onkeydown=e=>{if([' ','Enter'].includes(e.key)){e.preventDefault();startMic()}};
$('mic').onkeyup=e=>{if([' ','Enter'].includes(e.key))stopMic()};
$('reception').onclick=()=>{const on=$('reception').getAttribute('aria-checked')!=='true';$('reception').setAttribute('aria-checked',String(on));$('reception').classList.toggle('on',on);$('mic').disabled=!on;stopMic()};
$('voice-options').onclick=()=>{$('voice-options-panel').hidden=!$('voice-options-panel').hidden};
document.addEventListener('click',e=>{const theme=e.target.closest('[data-theme-choice]');if(theme)applyTheme(theme.dataset.themeChoice,true);const accent=e.target.closest('[data-accent]');if(accent){currentAccent=accent.dataset.accent;document.documentElement.style.setProperty('--accent',accents[currentAccent][1]);document.querySelectorAll('[data-accent]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.accent===currentAccent)));if(mapReady&&map.getLayer('sample-route'))map.setPaintProperty('sample-route','line-color',accents[currentAccent][1]);toast('Acento aplicado a la ruta. Tu tema se conserva.')}const go=e.target.closest('[data-go]');if(go){stopTour();page(go.dataset.go)}const mock=e.target.closest('.demo-action');if(mock)toast(mock.dataset.message);const sw=e.target.closest('.switch:not(#reception)');if(sw){const on=sw.getAttribute('aria-checked')!=='true';sw.setAttribute('aria-checked',String(on));sw.classList.toggle('on',on);const f=sw.closest('[data-feature]')?.dataset.feature;if(f){demoFeatures[f]=on;applyFeatures()}toast('Preferencia modificada solo en esta propuesta.')}});
function timeline(yesterday=false){const items=yesterday?[['Llegó al parque','Parque de ejemplo · Barranquilla','7:20 p. m.'],['Salió de la universidad','Universidad de ejemplo · Barranquilla','5:10 p. m.']]:[['Llegó a Casa','Lugar de ejemplo · Barranquilla','6:45 p. m.'],['Salió de la universidad','Universidad de ejemplo · Barranquilla','5:30 p. m.'],['Llegó a la universidad','Universidad de ejemplo · Barranquilla','8:10 a. m.']];$('timeline').innerHTML=items.map(([title,address,time])=>'<article class="timeline-item"><span class="timeline-icon">'+icon('pin')+'</span><div class="timeline-copy"><h3>'+title+'</h3><p>'+address+'</p><small>Registro ficticio</small></div><span class="timeline-time">'+time+'</span></article>').join('')}
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===b));timeline(b.dataset.filter==='yesterday')});timeline();
const places=[['Calle 56 #43-20','Barranquilla · dirección ficticia'],['Universidad','Lugar anterior sin dirección · ejemplo'],['Parque','Punto de encuentro · ejemplo']];
const placeText=s=>{const e=document.createElement('span');e.textContent=s;return e.innerHTML};
let addressMarker=null;
function renderPlaces(q=''){const results=places.map((p,i)=>({p,i})).filter(({p})=>p.join(' ').toLowerCase().includes(q.toLowerCase()));$('place-list').innerHTML=results.length?results.map(({p:[name,copy],i})=>'<article class="glass place-item">'+icon('pin')+'<div><h3>'+placeText(name)+'</h3><p>'+placeText(copy)+'</p></div><button data-place-view="'+i+'" aria-label="Ver dirección en el mapa">'+icon('route')+'</button></article>').join(''):'<p>No hay lugares de ejemplo que coincidan.</p>'}
$('place-search').oninput=e=>renderPlaces(e.target.value);renderPlaces();
function showAddressExample(i){
 const [address,city]=places[i];page('map');sheet('hidden');
 let label=$('address-map-label');if(!label){label=document.createElement('div');label.id='address-map-label';label.className='glass address-map-label';$('screen-map').append(label)}
 label.replaceChildren();const title=document.createElement('b'),detail=document.createElement('small'),close=document.createElement('button');
 title.textContent=address;detail.textContent=city+' · punto ilustrativo';close.textContent='×';close.setAttribute('aria-label','Cerrar dirección');
 close.onclick=()=>{label.hidden=true;addressMarker?.remove();addressMarker=null;sheet('collapsed')};label.append(title,detail,close);label.hidden=false;
 addressMarker?.remove();addressMarker=null;
 if(mapReady){const el=document.createElement('div');el.className='address-pin';el.textContent='●';addressMarker=new window.maplibregl.Marker({element:el}).setLngLat(sampleCenter).addTo(map);map.easeTo({center:sampleCenter,zoom:16,duration:300})}
}
$('place-list').onclick=e=>{const b=e.target.closest('[data-place-view]');if(b)showAddressExample(Number(b.dataset.placeView))};
$('add-place').onclick=()=>{
 modal('<form id="address-form" class="address-form"><p class="eyebrow">NUEVO LUGAR · PROPUESTA</p><h2>La dirección, tal cual.</h2><p>Escríbela y revisa el punto antes de guardar.</p><label>Ciudad<input id="address-city" required maxlength="80" placeholder="Barranquilla" autocomplete="off"></label><label>Dirección exacta<input id="address-street" required maxlength="160" placeholder="Calle 56 #43-20" autocomplete="off"></label><small>El número de apartamento puede ir en la dirección. El marcador señala el edificio.</small><button id="address-locate" type="button">Revisar punto de ejemplo</button><div id="address-review" hidden><div class="address-pin-preview" aria-hidden="true"><span>●</span></div><b id="address-summary"></b><p id="address-city-summary"></p><small>Mapa esquemático: esta propuesta no busca direcciones reales ni verifica la posición. En la app podrás ajustar el pin si la búsqueda no es exacta.</small></div><p id="address-status" role="status">La dirección será el título del lugar; no «Casa» ni «Trabajo».</p><button id="address-save" class="primary" disabled>Guardar en la propuesta</button></form>');
 const form=$('address-form');let reviewed='';const key=()=>[$('address-city').value.trim(),$('address-street').value.trim()].join('\n');
 form.oninput=()=>{reviewed='';$('address-save').disabled=true;$('address-review').hidden=true;$('address-status').textContent='Revisa el punto después de cambiar la dirección.'};
 $('address-locate').onclick=()=>{if(!form.reportValidity())return;reviewed=key();$('address-summary').textContent=$('address-street').value.trim();$('address-city-summary').textContent=$('address-city').value.trim();$('address-review').hidden=false;$('address-save').disabled=false;$('address-status').textContent='Punto ilustrativo revisado · sin ubicación real confirmada.'};
 form.onsubmit=e=>{e.preventDefault();if(!reviewed||reviewed!==key()||!form.reportValidity())return;places.push([$('address-street').value.trim(),$('address-city').value.trim()+' · ejemplo']);$('place-search').value='';renderPlaces();$('detail-dialog').close();showAddressExample(places.length-1);toast('Dirección añadida solo a la propuesta. No se modificaron sus lugares reales.')};
};
const memories=['Un día juntos','Nuestro rincón','Una escapada','Ese atardecer','Lo cotidiano','Tú y yo'];$('gallery').innerHTML=memories.map((name,i)=>'<button class="memory-tile" data-memory="'+i+'"><span aria-hidden="true">'+['♡','✧','☼','☾','❋','∞'][i]+'</span><div><b>'+name+'</b><small>Tarjeta de ejemplo</small></div></button>').join('');
$('gallery').onclick=e=>{const b=e.target.closest('[data-memory]');if(b)modal('<div class="dialog-art">♡</div><h2>'+memories[Number(b.dataset.memory)]+'</h2><p>Esta tarjeta muestra cómo se abriría un recuerdo. No se han cargado fotos personales.</p>')};
$('more-grid').innerHTML=[['photos','Momentos','Su galería','photo'],['history','Historial','Sus recorridos','history'],['places','Lugares','Sus puntos favoritos','pin'],['settings','Ajustes','Todo a su manera','settings'],['admin','Administración','Solo el creador','shield']].map(([id,title,copy,i])=>'<button class="glass more-card" data-go="'+id+'"><span>'+icon(i)+'</span><span><b>'+title+'</b><small>'+copy+'</small></span></button>').join('');
const settings={
General:[['Barra de navegación','Tres estilos de cristal · elección personal','navigation'],['Tu tema','Rosa original por defecto · elección individual en este navegador','themes'],['Acentos opcionales','Detalles de la ruta · no cambian el tema completo','palette'],['Transparencia ligera','Paneles de cristal de la propuesta','on'],['Movimiento reducido','Se respeta también la preferencia del sistema','off']],
Perfil:[['Nombre visible','Jancktony Murgas','edit'],['Foto de perfil','Propuesta de avatar; sin fotos personales cargadas','info']],
Actividad:[['Compartir actividad','Siempre con consentimiento de cada persona','on'],['Estado de descanso','Estado de ejemplo, sin lecturas del teléfono','off']],
Ubicación:[['Compartir ubicación','Estado de demostración','on'],['3D ligero','Edificios cercanos y menos efectos durante gestos','on']],
Widgets:[['Días juntos','Vista previa del contador','widget'],['Cercanía','Juntos · datos de ejemplo','widget']],
Avisos:[['Llegadas','Solo lugar y acción · ejemplo','on'],['Salidas','Hora de ejemplo en formato de 12 horas','on'],['Aviso de segundo plano','Android controla la visibilidad real de esta categoría','info']]};
function row(title,copy,type){if(type==='navigation')return '<div class="setting-row nav-preferences"><div><b>'+title+'</b><small>'+copy+'</small></div><div class="nav-style-options">'+Object.entries(navStyles).map(([id,name])=>'<button data-nav-style-choice="'+id+'" aria-pressed="'+(navStyle===id)+'"><i class="nav-sample sample-'+id+'" aria-hidden="true"><span></span><span></span><span></span><span></span></i><b>'+name+'</b></button>').join('')+'</div><label class="nav-motion-label">Animación<select id="nav-motion">'+Object.entries(navMotions).map(([id,name])=>'<option value="'+id+'" '+(navMotion===id?'selected':'')+'>'+name+'</option>').join('')+'</select></label><small>Se guarda en este navegador. Si el sistema reduce el movimiento, se muestra sin animación.</small><button class="nav-try" data-go="map">Probar en el mapa</button></div>';if(type==='themes')return '<div class="setting-row theme-row"><div><b>'+title+'</b><small>'+copy+'</small></div><div class="theme-choices">'+Object.entries(themes).map(([key,[name,bg,accent]])=>'<button data-theme-choice="'+key+'" aria-pressed="'+(key===currentTheme)+'" style="--sample-bg:'+bg+';--sample-accent:'+accent+'"><i aria-hidden="true"><span></span></i><b>'+name+'</b></button>').join('')+'</div></div>';if(type==='palette')return '<div class="setting-row"><div><b>'+title+'</b><small>'+copy+'</small></div><div class="accent-swatches">'+Object.entries(accents).map(([key,[name,color]])=>'<button data-accent="'+key+'" aria-label="Acento '+name+'" aria-pressed="'+(currentAccent===key)+'" style="--swatch:'+color+'"><i aria-hidden="true"></i>'+name+'</button>').join('')+'</div></div>';return '<div class="setting-row"><div><b>'+title+'</b><small>'+copy+'</small></div>'+(type==='on'||type==='off'?'<button class="switch '+(type==='on'?'on':'')+'" role="switch" aria-checked="'+(type==='on')+'" aria-label="'+title+' de ejemplo"></button>':type==='select'?'<select id="appearance" aria-label="Apariencia"><option>Automática</option><option>Verde noche</option><option>Rosa suave</option></select>':'<button class="setting-action" data-action="'+type+'">'+(type==='edit'?'Editar':type==='widget'?'Ver':'Detalle')+'</button>')+'</div>'}
function settingCategory(name){document.querySelectorAll('[data-setting]').forEach(b=>b.classList.toggle('active',b.dataset.setting===name));$('settings-detail').innerHTML=settings[name].map(v=>row(...v)).join('')}
$('settings-categories').innerHTML=Object.keys(settings).map(n=>'<button data-setting="'+n+'">'+n+'</button>').join('');
$('settings-categories').onclick=e=>{const b=e.target.closest('[data-setting]');if(b)settingCategory(b.dataset.setting)};settingCategory('General');

$('settings-detail').addEventListener('click',e=>{const b=e.target.closest('[data-nav-style-choice]');if(!b)return;navStyle=b.dataset.navStyleChoice;try{localStorage.setItem('nosotros-preview-nav-style',navStyle)}catch{}updateNav(true)});
$('settings-detail').addEventListener('change',e=>{if(e.target.id!=='nav-motion'||!Object.hasOwn(navMotions,e.target.value))return;navMotion=e.target.value;try{localStorage.setItem('nosotros-preview-nav-motion',navMotion)}catch{}updateNav(true)});
function editProfile(){modal('<h2>Tu nombre</h2><p>Solo cambia el nombre en esta demostración.</p><label>Nombre de ejemplo<input id="demo-name" maxlength="40" autocomplete="off"></label><button id="save-demo-name" class="primary" style="margin-top:15px">Guardar en la propuesta</button>');$('demo-name').value=$('profile-name').textContent;$('save-demo-name').onclick=()=>{const v=$('demo-name').value.trim();if(v)$('profile-name').textContent=v;$('detail-dialog').close();toast('Nombre de ejemplo actualizado.')}};
$('edit-profile').onclick=editProfile;
$('settings-detail').onclick=e=>{const b=e.target.closest('.setting-action');if(!b)return;if(b.dataset.action==='edit')editProfile();else if(b.dataset.action==='widget')modal('<div class="glass days"><small>Widget de ejemplo</small><b>1343 <span>días juntos</span></b><span>Juntos · no es una lectura real</span></div>');else modal('<h2>Tu control, siempre.</h2><p>Las preferencias reales se cambian en la aplicación instalada. Esta propuesta no solicita permisos ni modifica notificaciones del teléfono.</p>')};
function applyFeatures(){
 $('reception').disabled=!demoFeatures.walkie;
 $('mic').disabled=!demoFeatures.walkie||$('reception').getAttribute('aria-checked')!=='true';
 stopMic();
 $('toggle-3d').disabled=!demoFeatures.map3d;
 if(!demoFeatures.map3d){$('toggle-3d').setAttribute('aria-pressed','false');$('toggle-3d').textContent='3D';if(mapReady){if(map.getLayer('preview-buildings'))map.setLayoutProperty('preview-buildings','visibility','none');map.easeTo({pitch:0,duration:300})}}
 document.querySelectorAll('.hearts').forEach(e=>e.hidden=!demoFeatures.together);
}
$('feature-toggles').innerHTML=[['walkie','Walkie-talkie','Acceso de ejemplo'],['together','Juntos animado','Corazones y perfiles unidos'],['map3d','Mapa 3D','Vista de ejemplo']].map(([f,a,b])=>'<div data-feature="'+f+'">'+row(a,b,'on')+'</div>').join('');
$('release-next').onclick=()=>{releaseStage=(releaseStage+1)%4;const labels=['Simular instalar solo para mí','Simular aprobar prueba','Simular liberar a Kelin','Reiniciar demostración'];const states=['Propuesta · candidata de demostración','Prueba de ejemplo instalada solo para el dueño','Prueba de ejemplo aprobada · lista para liberar','Liberación simulada completada'];$('release-next').textContent=labels[releaseStage];$('release-status').textContent=states[releaseStage];['test','review','release'].forEach((s,i)=>$('step-'+s).classList.toggle('active',i===Math.min(releaseStage,2)));$('release-help').textContent='No se instaló, aprobó ni liberó ninguna versión real.';toast('Paso simulado. La Administración real no se modificó.')};
function stopTour(){clearInterval(tourTimer);tourTimer=null;$('tour').textContent='▷ Recorrido automático';stopMic()}
$('tour').onclick=()=>{if(tourTimer){stopTour();return}let step=0;const actions=[()=>{page('map');selectPerson(false);sheet('expanded')},()=>{sheet('hidden');toast('La ficha se recoge al mover el mapa.')},()=>{page('voice');startMic()},()=>{stopMic();page('photos')},()=>{page('settings');settingCategory('Ubicación')},()=>{page('admin')},()=>{page('map');sheet('collapsed');stopTour()}];actions[0]();$('tour').textContent='Ⅱ Detener recorrido';tourTimer=setInterval(()=>{step++;actions[Math.min(step,actions.length-1)]()},3500)};
$('mobile').onclick=()=>{document.body.classList.add('compact');setTimeout(()=>map?.resize(),50)};
$('wide').onclick=()=>{document.body.classList.remove('compact');setTimeout(()=>map?.resize(),50)};
document.addEventListener('visibilitychange',()=>{if(document.hidden){stopMic();stopTour();endRoute()}});
$('signin').onsubmit=async e=>{e.preventDefault();$('error').textContent='';try{await setPersistence(auth,browserSessionPersistence);await signInWithEmailAndPassword(auth,$('email').value.trim(),$('password').value)}catch{$('error').textContent='No se pudo entrar con esa cuenta de Nosotros.'}finally{$('password').value=''}};
onAuthStateChanged(auth,async u=>{const ok=!!(u?.emailVerified&&u.email?.toLowerCase()==='jancktoni@gmail.com');$('login').hidden=ok;$('preview').hidden=!ok;if(ok)requestAnimationFrame(initMap);else{stopTour();endRoute()}if(u&&!ok){$('error').textContent='Propuesta privada del creador.';await signOut(auth)}});
$('logout').onclick=()=>signOut(auth);
let savedTheme='pink';try{savedTheme=localStorage.getItem('nosotros-preview-theme')||'pink'}catch{}
applyTheme(savedTheme);icons();sheet('collapsed');page('map');
