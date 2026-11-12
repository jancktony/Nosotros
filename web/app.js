import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence,sendPasswordResetEmail} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import {getFirestore,doc,setDoc,updateDoc,deleteDoc,onSnapshot,collection,query,orderBy,limit,where,getDocs,getDoc,serverTimestamp,writeBatch,deleteField} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';

const app=document.getElementById('app');
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ago=t=>{if(!t)return 'Sin señal';const m=Math.max(0,Math.floor((Date.now()-t)/60000));return m<1?'Ahora':m<60?`Hace ${m} min`:m<1440?`Hace ${Math.floor(m/60)} h`:`Hace ${Math.floor(m/1440)} días`};
const FEATURES=[['findPhone','Encontrar y hacer sonar teléfonos','off'],['appPresence','Compartir aplicación en uso (voluntario)','off'],['sleepStatus','Descanso estimado (voluntario)','off'],['mapPlaces','Nombres de universidades y lugares','off'],['mapGlobe','Globo terrestre experimental','off'],['walkieRelay','Walkie en redes difíciles (opcional)','off'],['walkie','Walkie-talkie en directo','off'],['voiceNotes','Notas de voz','off'],['map3d','Mapa inclinado y edificios','all'],['profilePhoto','Fotos de perfil','all'],['compactWidgets','Widgets compactos','all'],['placeAlerts','Avisos de llegada y salida','all'],['manualUpdates','Buscar actualizaciones','all'],['autoUpdates','Instalación de versiones aprobadas','all'],['lockDetails','Detalles en pantalla de bloqueo','all']];
let webTab='phones',commandUnsub=[],commandKey='',drafts={},photoViewer=null;
let db,auth,uid=null,pairId=null,map,unsub=[],activityUnsub=[],activityKey='',selected=null,renderedSelection=null,notice='';
const S={pair:null,members:[],places:[],photos:[],releases:[],reviews:{},activity:{},commands:{}};
const isAdmin=()=>uid&&S.pair?.admin===uid;
const canLocate=m=>m&&Number.isFinite(m.lat)&&Number.isFinite(m.lng)&&(m.lat!==0||m.lng!==0)&&m.lat>=-90&&m.lat<=90&&m.lng>=-180&&m.lng<=180;
const paused=m=>(m?.pausedUntil||0)>Date.now();
function clear(){commandUnsub.forEach(fn=>fn());commandUnsub=[];commandKey='';webTab='phones';drafts={};photoViewer=null;activityUnsub.forEach(fn=>fn());activityUnsub=[];activityKey='';unsub.forEach(fn=>fn());unsub=[];map?.remove();map=null;uid=null;pairId=null;selected=null;Object.assign(S,{pair:null,members:[],places:[],photos:[],releases:[],reviews:{},activity:{},commands:{}})}
function errorMessage(e){return e?.code==='auth/invalid-credential'||e?.code==='auth/wrong-password'||e?.code==='auth/user-not-found'?'Correo o contraseña incorrectos.':e?.code==='auth/too-many-requests'?'Espera un momento antes de intentarlo otra vez.':e?.code==='permission-denied'?'Esta cuenta no tiene acceso a este panel.':e?.message&&(e.message.startsWith('Verifica')||['Elige','Escribe','Primero'].some(x=>e.message.startsWith(x)))?e.message:'No se pudo completar. Revisa la conexión y vuelve a intentarlo.'}
function login(message=''){
 clear();app.innerHTML=`<div class="center"><form class="card" id="login" style="max-width:420px;width:100%"><h1>Nosotros · Privado</h1><p class="sub">Entra con el mismo correo de la app para encontrar tu teléfono y ver sus datos.</p><p><input name="email" type="email" autocomplete="username" placeholder="Tu correo" required></p><p><input name="password" type="password" autocomplete="current-password" placeholder="Tu contraseña" required></p><button type="submit">Entrar</button> <button type="button" data-act="forgot">Olvidé mi contraseña</button><p id="login-error" role="alert">${esc(message)}</p><p class="sub">Creado por Jancktony Murgas</p></form></div>`;
 app.querySelector('#login').addEventListener('submit',async event=>{
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]');button.disabled=true;
  try{await signInWithEmailAndPassword(auth,form.email.value.trim(),form.password.value);form.reset()}
  catch(e){form.querySelector('#login-error').textContent=errorMessage(e);button.disabled=false}
 });
}
async function enter(user){
 clear();uid=user.uid;
 try{
  await user.reload();if(!user.emailVerified)throw Error('Verifica tu correo desde el enlace recibido antes de entrar.');
  const pairs=await getDocs(query(collection(db,'pairs'),where('members','array-contains',uid),limit(5)));
  if(auth.currentUser?.uid!==user.uid)return;
  const pair=pairs.docs.find(d=>d.data().members?.length===2)||pairs.docs[0];
  if(!pair)throw Error('No hay una cuenta vinculada. Vincula los teléfonos desde la app.');
  pairId=pair.id;start();
 }catch(e){await signOut(auth);login(errorMessage(e))}
}
function start(){
 const p=doc(db,'pairs',pairId);let adminSubscriptions=false;
 unsub.push(onSnapshot(p,snapshot=>{
  const data=snapshot.data();if(!data?.members?.includes(uid)){signOut(auth);login('La cuenta ya no está vinculada.');return}
  S.pair=data;watchActivity();watchCommands();
  if(isAdmin()&&!adminSubscriptions){adminSubscriptions=true;
   unsub.push(onSnapshot(query(collection(db,'releases'),orderBy('publishedAt','desc'),limit(20)),s=>{S.releases=s.docs.map(d=>({id:d.id,...d.data()})).filter(r=>r.ready&&r.pairId===pairId);render()},()=>{notice='No se pudieron cargar las versiones';render()}));
   unsub.push(onSnapshot(collection(p,'releaseReviews'),s=>{S.reviews=Object.fromEntries(s.docs.map(d=>[d.id,d.data()]));render()},()=>{}));
  }
  render();
 },()=>{signOut(auth);login('No se pudo abrir el panel privado.')}));
 unsub.push(onSnapshot(collection(p,'members'),s=>{S.members=s.docs.map(d=>({id:d.id,...d.data()}));render()},()=>{}));
 unsub.push(onSnapshot(collection(p,'places'),s=>{S.places=s.docs.map(d=>({id:d.id,...d.data()}));render()},()=>{}));
 unsub.push(onSnapshot(query(collection(p,'photos'),orderBy('at','desc'),limit(24)),s=>{S.photos=s.docs.map(d=>({id:d.id,...d.data()}));render()},()=>{}));
}
function enabled(key,target=uid){const mode=S.pair?.featureFlags?.[key]||'off';return mode==='all'||(mode==='admin'&&target===S.pair?.admin)}
function watchActivity(){
 const signature=JSON.stringify([S.pair.members,S.pair.admin,S.pair.featureFlags?.appPresence,S.pair.featureFlags?.sleepStatus]);
 if(signature===activityKey)return;activityKey=signature;
 activityUnsub.forEach(fn=>fn());activityUnsub=[];S.activity={};
 for(const target of S.pair.members)for(const [key,collectionName] of [['appPresence','appPresence'],['sleepStatus','sleepPresence']]){
  if(!enabled(key,target))continue;
  const id=`${target}:${key}`;
  activityUnsub.push(onSnapshot(doc(db,'pairs',pairId,collectionName,target),snapshot=>{
   S.activity[id]=snapshot.metadata?.fromCache?null:snapshot.data();render();
  },()=>{delete S.activity[id];render()}));
 }
}
function activityText(member){
 const now=Date.now(),parts=[];
 for(const key of ['appPresence','sleepStatus']){
  if(!enabled(key,member.id))continue;
  const data=S.activity[`${member.id}:${key}`],age=now-(data?.updatedAt?.toMillis?.()||0);
  if(!data?.sharing||age<0||age>(key==='appPresence'?180000:720000))continue;
  if(key==='appPresence'&&data.label)parts.push(`Última app detectada: ${data.label}`);
  if(key==='sleepStatus'){
   const hours=Math.max(0,Math.floor((now-(data.since||now))/60000));
   const duration=`${Math.floor(hours/60)} h ${hours%60} min`;
   if(data.state==='awake')parts.push('Despierto · teléfono en uso');
   if(data.state==='sleeping')parts.push(`Posiblemente durmiendo · ${duration} estimados`);
   if(data.state==='overdue')parts.push(`Pasó su hora habitual de despertar · ${duration} estimados`);
  }
 }
 return parts.map(text=>`<p class="sub">${esc(text)}</p>`).join('');
}
function watchCommands(){
 const signature=JSON.stringify(S.pair.members);if(commandKey===signature)return;commandKey=signature;
 commandUnsub.forEach(fn=>fn());commandUnsub=[];S.commands={};
 for(const target of S.pair.members)commandUnsub.push(onSnapshot(doc(db,'pairs',pairId,'deviceCommands',target),snap=>{
  S.commands[target]=snap.data();render();
 },()=>{}));
}
function commandStatus(member){
 const c=S.commands[member.id];if(!c)return '';
 if(c.expiresAt<Date.now()&&c.status==='queued')return 'Sin respuesta. Revisa conexión y permisos del teléfono.';
 return ({queued:'Solicitud enviada · esperando al teléfono',ringing:'Reproduciendo sonido según el volumen del teléfono',stopped:'Sonido detenido',unavailable:'El teléfono no pudo reproducir el sonido'})[c.status]||'';
}
function settingsPanel(me){
 const date=S.pair.since?new Date(S.pair.since):null;
 const localDate=date&&!Number.isNaN(date.valueOf())?`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`:'';
 return `<section class="card"><span class="eyebrow">MI PERFIL</span><h2>Así te verá tu pareja</h2><label for="profile-name">Tu nombre</label><input id="profile-name" maxlength="40" autocomplete="nickname" value="${esc(drafts['profile-name']??me?.name??'')}"><button class="primary" data-act="save-profile">Guardar mi nombre</button><p class="sub">Solo editas tu propio perfil. Los permisos y el consentimiento de actividad se aceptan desde cada teléfono.</p></section>
 <section class="card"><span class="eyebrow">SU FECHA ESPECIAL</span><h2>Nuestro aniversario</h2><label for="anniversary-date">Fecha compartida</label><input id="anniversary-date" type="date" value="${esc(drafts['anniversary-date']??localDate)}"><button data-act="save-anniversary">Guardar fecha</button></section>
 <section class="card"><h2>Apariencia de esta web</h2><div class="toolbar">${[['night','Noche'],['light','Claro'],['pink','Rosa']].map(([value,title])=>`<button data-act="theme" data-mode="${value}">${title}</button>`).join('')}</div><p class="sub">Se guarda en este navegador. La apariencia de cada teléfono se elige en su app.</p></section>`;
}
function photosPanel(){return `<section class="card"><span class="eyebrow">SOLO USTEDES</span><h2>Sus recuerdos</h2><div class="photos">${S.photos.map(photo=>`<button class="photo-cell" data-act="photo" data-id="${esc(photo.id)}"><img alt="${esc(photo.caption||'Foto compartida')}" src="data:image/jpeg;base64,${esc(photo.thumb?.toBase64?.()||'')}"><span>${esc(photo.caption||'Un momento juntos')}</span></button>`).join('')||'<p class="sub">El primer recuerdo los está esperando.</p>'}</div></section>`}
function viewer(){if(!photoViewer)return '';return `<dialog id="photo-viewer"><div class="viewer-toolbar"><span>${esc(photoViewer.caption||'Un momento juntos')}</span><button data-act="photo-close">Cerrar</button></div><img alt="Foto compartida ampliada" src="data:image/jpeg;base64,${esc(photoViewer.src)}"><p class="sub">${esc(photoViewer.status||'')}</p></dialog>`}
function person(member){
 if(!member)return '<p class="sub">Esperando la primera señal</p>';
 const available=canLocate(member),isPaused=paused(member),own=member.id===uid;
 return `<h2>${esc(member.name|| (own?'Mi teléfono':'Teléfono de mi pareja'))}</h2><p class="sub">${own?'Tu cuenta':'Cuenta de tu pareja'} · ${esc(member.appVersion||'Sin versión informada')}</p>
 <div class="row"><span class="grow sub">Última ubicación</span><b>${ago(member.updatedAt)}</b></div>
 <div class="row"><span class="grow sub">Batería</span>${member.battery>=0?`${member.battery}%${member.charging?' · Cargando':''}`:'Sin datos'}</div>
 <p>${isPaused?'Ubicación pausada':esc(member.placeName||'Ubicación disponible según la última señal')}</p>
 ${activityText(member)}
 <button data-act="center" data-id="${esc(member.id)}" ${available?'':'disabled'}>Ver ${own?'mi teléfono':'teléfono'} en el mapa</button>
 <button data-act="locate" data-id="${esc(member.id)}" ${isPaused?'disabled':''}>Solicitar ubicación reciente</button>
 <div class="toolbar"><button data-act="ring" data-id="${esc(member.id)}" ${member.findPhoneEnabled&&enabled('findPhone',member.id)?'':'disabled'}>Hacer sonar</button>${available?`<a class="button" target="_blank" rel="noopener noreferrer" href="https://www.google.com/maps/dir/?api=1&destination=${member.lat},${member.lng}">Cómo llegar</a>`:''}</div>
 <p class="sub">${esc(commandStatus(member))}</p>
 ${!member.findPhoneEnabled?'<p class="sub">Para hacer sonar este teléfono, activa el permiso en su app → Ajustes → Ubicación. El administrador debe habilitar Encontrar teléfono.</p>':''}
 <p class="sub">${isPaused?'No se fuerza la ubicación mientras esté en pausa.': 'La solicitud necesita que el teléfono tenga internet, la app activa en segundo plano y permiso de ubicación.'}</p>`;
}
function adminPanel(){
 if(!isAdmin())return '';
 const me=S.members.find(m=>m.id===uid);
 return `<section class="card"><h2>Pruebas y versiones</h2><p class="sub">Primero pruebas solo en tu teléfono. Si te gusta, apruebas y liberas; si no, guardas las correcciones. Tu pareja conserva la versión anterior.</p><p>Liberada para tu pareja: <b>${esc(S.pair.relVersion||'Ninguna')}</b></p>
 ${S.releases.map(release=>{const review=S.reviews[release.id]||{},installed=me?.appVersion===release.version,live=S.pair.relVersion===release.version;return `<div class="row"><div class="grow"><b>${esc(release.version)}</b><p class="sub">${esc(review.state==='approved'?'Prueba aprobada':review.state==='changes_requested'?'Correcciones pendientes':installed?'Instalada en tu teléfono':'Candidata sin probar')}</p><p class="sub">${esc(review.notes||release.notes||'')}</p>
 <button data-act="test" data-id="${esc(release.id)}" ${installed?'disabled':''}>Instalar solo en mi teléfono</button>
 <button data-act="corrections" data-id="${esc(release.id)}">Pedir correcciones</button>
 <button data-act="approve" data-id="${esc(release.id)}" ${installed?'':'disabled'}>Aprobar mi prueba</button>
 <button data-act="force" data-id="${esc(release.id)}" ${installed&&review.state==='approved'?'':'disabled'}>Forzar actualización</button>
 <button data-act="release" data-id="${esc(release.id)}" ${installed&&review.state==='approved'&&!live?'':'disabled'}>${live?'Liberada':'Liberar para mi pareja'}</button></div></div>`}).join('')||'<p class="sub">Todavía no hay candidatas privadas disponibles.</p>'}</section>
 <section class="card"><h2>Funciones</h2>${FEATURES.map(([key,title,fallback])=>{const mode=S.pair.featureFlags?.[key]||fallback;return `<div class="row"><span class="grow">${esc(title)}</span>${[['off','Desactivada'],['admin','Solo yo'],['all','Ambos']].map(([value,label])=>`<button data-act="feature" data-id="${key}" data-mode="${value}" ${mode===value?'disabled':''}>${mode===value?'✓ ':''}${label}</button>`).join('')}</div>`}).join('')}</section>
 <section class="card"><h2>Mi marca de creador</h2><input id="creator-brand" maxlength="60" value="${esc(S.pair.creatorBrand||'Jancktony Murgas')}" aria-label="Tu marca"><p><button data-act="brand">Guardar mi marca</button></p></section>`;
}
function render(){
 if(!S.pair||!uid)return;
 const live=S.members.filter(m=>S.pair.members.includes(m.id));
 const me=live.find(m=>m.id===uid),other=live.find(m=>m.id!==uid);
 const savedCamera=map&&selected===renderedSelection&&map.getCenter?{center:map.getCenter(),zoom:map.getZoom(),bearing:map.getBearing(),pitch:map.getPitch()}:null;
 map?.remove();map=null;renderedSelection=selected;
 const nav=[['phones','Teléfonos'],['photos','Galería'],['settings','Ajustes'],...(isAdmin()?[['admin','Administración']]:[])];
 if(webTab==='admin'&&!isAdmin())webTab='phones';
 app.innerHTML=`<header class="topbar"><div><span class="wordmark">nosotros<span>✦</span></span><p class="sub">Creado por ${esc(S.pair.creatorBrand||'Jancktony Murgas')}</p></div><div class="toolbar"><span class="private-badge">ACCESO PRIVADO</span><button data-act="logout">Cerrar sesión</button></div></header>
 <nav class="web-nav" aria-label="Panel privado">${nav.map(([value,title])=>`<button data-act="web-tab" data-mode="${value}" class="${webTab===value?'active':''}">${title}</button>`).join('')}</nav>
 <div class="page-title"><span class="eyebrow">TU ESPACIO, DONDE ESTÉS</span><h1>${({phones:'Tus teléfonos',photos:'Sus recuerdos',settings:'A tu manera',admin:'Tú decides'})[webTab]}</h1><p class="sub">${webTab==='phones'?'Última señal, ubicación y herramientas para encontrarlos.':'Un espacio privado para ustedes dos.'}</p></div>
 ${notice?`<p class="card status" role="status">${esc(notice)}</p>`:''}
 ${webTab==='phones'?`<section class="grid"><article class="card">${person(me)}</article><article class="card">${person(other)}</article></section><section class="card"><h2>Encontrar el teléfono</h2><p class="sub">Última ubicación recibida. Un teléfono apagado o sin internet puede no responder. Hacer sonar respeta su volumen y No molestar.</p><div id="map"></div></section><section class="card"><h2>Sus lugares</h2>${S.places.filter(p=>!p.suggested&&p.name).map(p=>`<div class="row"><span class="grow">${esc(p.name)}</span><span class="sub">${Math.round(p.radius||100)} m</span></div>`).join('')||'<p class="sub">Todavía no hay lugares.</p>'}</section>`:''}
 ${webTab==='photos'?photosPanel():''}${webTab==='settings'?settingsPanel(me):''}${webTab==='admin'?adminPanel():''}${viewer()}`;
 if(photoViewer)document.getElementById('photo-viewer')?.showModal?.();
 if(webTab!=='phones')return;
 const located=live.filter(canLocate);if(!located.length){document.getElementById('map').innerHTML='<p class="sub">No hay una ubicación recibida todavía.</p>';return}
 const focus=located.find(m=>m.id===selected)||located[0];
 try{
  map=new maplibregl.Map({container:'map',style:'https://tiles.openfreemap.org/styles/dark',center:[focus.lng,focus.lat],zoom:selected?16:12,...(savedCamera||{})});
  map.on?.('style.load',()=>{
   if(enabled('mapGlobe'))map.setProjection({type:'globe'});
   if(enabled('mapPlaces'))map.addLayer({id:'universities',type:'symbol',source:'openmaptiles','source-layer':'poi',minzoom:12,
    filter:['match',['get','class'],['college','university','school'],true,false],layout:{'text-field':['coalesce',['get','name:es'],['get','name']],'text-font':['Noto Sans Regular'],'text-size':13,'text-max-width':12},paint:{'text-color':'#ffffff','text-halo-color':'#162133','text-halo-width':1.5}});
  });
  const bounds=new maplibregl.LngLatBounds();
  for(const member of located){new maplibregl.Marker({color:member.id===uid?'#5eead4':'#f9a8d4'}).setLngLat([member.lng,member.lat]).addTo(map);bounds.extend([member.lng,member.lat])}
  if(!savedCamera&&!selected&&located.length>1)map.fitBounds(bounds,{padding:55,maxZoom:15,animate:false});
 }catch{document.getElementById('map').textContent='No se pudo cargar el mapa. Revisa la conexión.'}
}
app.addEventListener('click',async event=>{
 const button=event.target.closest('[data-act]');if(!button)return;
 const action=button.dataset.act,id=button.dataset.id;
 if(action==='forgot'){
  const form=app.querySelector('#login'),email=form.email.value.trim();
  if(!email||!form.email.checkValidity()){form.querySelector('#login-error').textContent='Escribe un correo válido.';return}
  try{await sendPasswordResetEmail(auth,email);form.querySelector('#login-error').textContent='Si el correo tiene cuenta, recibirás el enlace en español.'}
  catch(e){form.querySelector('#login-error').textContent=errorMessage(e)}return;
 }
 if(!uid||!pairId)return;
 button.disabled=true;
 try{
  const p=doc(db,'pairs',pairId),release=S.releases.find(r=>r.id===id);
  if(action==='logout'){await signOut(auth);return}
  if(action==='web-tab'){webTab=button.dataset.mode;notice='';render();return}
  if(action==='theme'){
   const mode=button.dataset.mode;document.body.dataset.theme=mode;try{localStorage.setItem('nosotros-web-theme',mode)}catch{}return;
  }
  if(action==='save-profile'){
   const name=app.querySelector('#profile-name').value.trim();if(!name||name.length>40)throw Error('Escribe un nombre de hasta 40 caracteres.');
   await updateDoc(doc(p,'members',uid),{name});delete drafts['profile-name'];notice='Tu nombre fue actualizado. Se sincronizará al conectar tu teléfono.';render();return;
  }
  if(action==='save-anniversary'){
   const value=app.querySelector('#anniversary-date').value;if(!/^\d{4}-\d{2}-\d{2}$/.test(value))throw Error('Elige una fecha válida.');
   const old=new Date(S.pair.since||0),date=new Date(value+'T00:00:00');
   if(S.pair.since)date.setHours(old.getHours(),old.getMinutes());
   await updateDoc(p,{since:date.getTime()});delete drafts['anniversary-date'];notice='Su fecha especial quedó guardada.';render();return;
  }
  if(action==='ring'){
   const member=S.members.find(m=>m.id===id);if(!member?.findPhoneEnabled||!enabled('findPhone',id))throw Error('Primero activa Encontrar teléfono y su permiso en ese teléfono.');
   if(!confirm(`¿Hacer sonar ${member.name||'este teléfono'} hasta 30 segundos?`))return;
   await setDoc(doc(p,'deviceCommands',id),{requestId:crypto.randomUUID(),requester:uid,type:'ring',status:'queued',createdAt:serverTimestamp(),expiresAt:Date.now()+59000});
   notice='Solicitud enviada. Necesita internet y la app activa; el teléfono puede respetar silencio o No molestar.';render();return;
  }
  if(action==='photo-close'){photoViewer=null;render();return}
  if(action==='photo'){
   const photo=S.photos.find(x=>x.id===id);if(!photo)return;const actor=uid,origin=pairId;
   photoViewer={id,caption:photo.caption,src:photo.thumb?.toBase64?.()||'',status:'Cargando foto completa…'};render();
   try{const full=await getDoc(doc(p,'photos',id,'full','img'));if(uid!==actor||pairId!==origin||photoViewer?.id!==id)return;
    const bytes=full.data()?.img;if(bytes)photoViewer.src=bytes.toBase64();photoViewer.status=bytes?'':'Se muestra la vista previa; no se pudo cargar la foto completa.';
   }catch{if(photoViewer?.id===id)photoViewer.status='Se muestra la vista previa. Revisa la conexión.'}
   render();return;
  }
  if(action==='center'){selected=id;render();document.getElementById('map')?.scrollIntoView({behavior:'smooth',block:'center'});return}
  if(action==='locate'){
   const member=S.members.find(m=>m.id===id);if(!member||paused(member))throw Error('Ubicación pausada');
   await updateDoc(doc(p,'members',id),{liveRequestedUntil:Date.now()+240000});selected=id;notice='Solicitud enviada. Si el teléfono puede compartir, su ubicación se actualizará aquí.';
  }else{
   if(!isAdmin())throw Error('Solo el administrador puede hacer esto');
   if(action==='feature')await updateDoc(p,{[`featureFlags.${id}`]:button.dataset.mode});
   if(action==='brand'){
    const brand=app.querySelector('#creator-brand').value.trim();if(brand.length<2)throw Error('Escribe tu marca');await updateDoc(p,{creatorBrand:brand});notice='Marca guardada';
   }
   if(['test','corrections','approve','release','force'].includes(action)&&!release)throw Error('Candidata no disponible');
   if(action==='test'){await updateDoc(p,{testVersion:release.version});notice='Prueba seleccionada solo para tu teléfono. Abre la app o espera a que tenga Wi-Fi; puede requerir confirmación de Android.'}
   if(action==='corrections'){
    const notes=prompt('¿Qué debemos corregir en esta versión?',S.reviews[id]?.notes||'');if(!notes?.trim())return;
    const batch=writeBatch(db);batch.set(doc(p,'releaseReviews',id),{uid,state:'changes_requested',notes:notes.trim().slice(0,2000),updatedAt:serverTimestamp()});batch.update(p,{testVersion:deleteField()});await batch.commit();notice='Correcciones guardadas. No se libera una actualización a tu pareja.';
   }
   if(action==='approve'){await setDoc(doc(p,'releaseReviews',id),{uid,state:'approved',notes:'',updatedAt:serverTimestamp()});notice='Tu prueba quedó aprobada. Ahora puedes liberar para tu pareja.'}
   if(action==='force'){
    if(meVersion()!==release.version||S.reviews[id]?.state!=='approved')throw Error('Primero instala y aprueba tu prueba.');
    if(!confirm(`¿Liberar y solicitar instalar ${release.version} en el teléfono de tu pareja? Android puede pedir confirmación.`))return;
    const metered=confirm('¿Permitir datos móviles para esta orden? Cancelar mantiene solo Wi-Fi.');
    await updateDoc(p,{relVersion:release.version,relUrl:release.apkPath,relNotes:release.notes||'',forceUpdateVersion:release.version,forceAllowMetered:metered,updateRequestAt:serverTimestamp()});
    notice='Orden puntual enviada para la versión aprobada. Necesita una app compatible; Android puede pedir confirmar.';
   }
   if(action==='release'&&confirm(`¿Liberar ${release.version} para tu pareja?`)){await updateDoc(p,{relVersion:release.version,relUrl:release.apkPath,relNotes:release.notes||'',forceUpdateVersion:deleteField(),forceAllowMetered:false,updateRequestAt:serverTimestamp()});notice='Versión liberada para tu pareja'}
  }
  render();
 }catch(e){notice=errorMessage(e);render()}
 finally{if(button.isConnected)button.disabled=false}
});
if(!window.FB?.apiKey||!window.FB?.projectId){app.innerHTML='<div class="center"><p>El panel todavía no está disponible. Inténtalo más tarde.</p></div>'}
else{
 const fb=initializeApp(window.FB);db=getFirestore(fb);auth=getAuth(fb);auth.languageCode='es';
 await setPersistence(auth,browserSessionPersistence);
 onAuthStateChanged(auth,user=>{if(user)enter(user);else login()});
}

setInterval(()=>{if(uid&&S.pair&&Object.keys(S.activity).length)render()},30000);

function meVersion(){return S.members.find(m=>m.id===uid)?.appVersion}
app.addEventListener('input',event=>{if(['profile-name','anniversary-date'].includes(event.target.id))drafts[event.target.id]=event.target.value});
try{document.body.dataset.theme=localStorage.getItem('nosotros-web-theme')||'night'}catch{}
