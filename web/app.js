import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence,sendPasswordResetEmail} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
import {getFirestore,doc,setDoc,updateDoc,deleteDoc,onSnapshot,collection,query,orderBy,limit,where,getDocs,serverTimestamp,writeBatch,deleteField} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js';

const app=document.getElementById('app');
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ago=t=>{if(!t)return 'Sin señal';const m=Math.max(0,Math.floor((Date.now()-t)/60000));return m<1?'Ahora':m<60?`Hace ${m} min`:m<1440?`Hace ${Math.floor(m/60)} h`:`Hace ${Math.floor(m/1440)} días`};
const FEATURES=[['walkie','Walkie-talkie en directo','off'],['voiceNotes','Notas de voz','off'],['map3d','Mapa inclinado y edificios','all'],['profilePhoto','Fotos de perfil','all'],['compactWidgets','Widgets compactos','all'],['placeAlerts','Avisos de llegada y salida','all'],['manualUpdates','Buscar actualizaciones','all'],['autoUpdates','Instalación de versiones aprobadas','all'],['lockDetails','Detalles en pantalla de bloqueo','all']];
let db,auth,uid=null,pairId=null,map,unsub=[],selected=null,notice='';
const S={pair:null,members:[],places:[],photos:[],releases:[],reviews:{}};
const isAdmin=()=>uid&&S.pair?.admin===uid;
const canLocate=m=>m&&Number.isFinite(m.lat)&&Number.isFinite(m.lng)&&(m.lat!==0||m.lng!==0)&&m.lat>=-90&&m.lat<=90&&m.lng>=-180&&m.lng<=180;
const paused=m=>(m?.pausedUntil||0)>Date.now();
function clear(){unsub.forEach(fn=>fn());unsub=[];map?.remove();map=null;uid=null;pairId=null;selected=null;Object.assign(S,{pair:null,members:[],places:[],photos:[],releases:[],reviews:{}})}
function errorMessage(e){return e?.code==='auth/invalid-credential'||e?.code==='auth/wrong-password'||e?.code==='auth/user-not-found'?'Correo o contraseña incorrectos.':e?.code==='auth/too-many-requests'?'Espera un momento antes de intentarlo otra vez.':e?.code==='permission-denied'?'Esta cuenta no tiene acceso a este panel.':e?.message?.startsWith('Verifica')?e.message:'No se pudo completar. Revisa la conexión y vuelve a intentarlo.'}
function login(message=''){
 clear();app.innerHTML=`<div class="center"><form class="card" id="login" style="max-width:420px;width:100%"><h1>Nosotros · Privado</h1><p class="sub">Entra con el mismo correo de la app para encontrar tu teléfono y ver sus datos.</p><p><input name="email" type="email" autocomplete="username" placeholder="Tu correo" required></p><p><input name="password" type="password" autocomplete="current-password" placeholder="Tu contraseña" required></p><button type="submit">Entrar</button> <button type="button" data-act="forgot">Olvidé mi contraseña</button><p id="login-error" role="alert">${esc(message)}</p><p class="sub">Creado por jancktony</p></form></div>`;
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
  S.pair=data;
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
function person(member){
 if(!member)return '<p class="sub">Esperando la primera señal</p>';
 const available=canLocate(member),isPaused=paused(member),own=member.id===uid;
 return `<h2>${esc(member.name|| (own?'Mi teléfono':'Teléfono de mi pareja'))}</h2><p class="sub">${own?'Tu cuenta':'Cuenta de tu pareja'} · ${esc(member.appVersion||'Sin versión informada')}</p>
 <div class="row"><span class="grow sub">Última ubicación</span><b>${ago(member.updatedAt)}</b></div>
 <div class="row"><span class="grow sub">Batería</span>${member.battery>=0?`${member.battery}%${member.charging?' · Cargando':''}`:'Sin datos'}</div>
 <p>${isPaused?'Ubicación pausada':esc(member.placeName||'Ubicación disponible según la última señal')}</p>
 <button data-act="center" data-id="${esc(member.id)}" ${available?'':'disabled'}>Ver ${own?'mi teléfono':'teléfono'} en el mapa</button>
 <button data-act="locate" data-id="${esc(member.id)}" ${isPaused?'disabled':''}>Solicitar ubicación reciente</button>
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
 <button data-act="release" data-id="${esc(release.id)}" ${installed&&review.state==='approved'&&!live?'':'disabled'}>${live?'Liberada':'Liberar para mi pareja'}</button></div></div>`}).join('')||'<p class="sub">Todavía no hay candidatas privadas disponibles.</p>'}</section>
 <section class="card"><h2>Funciones</h2>${FEATURES.map(([key,title,fallback])=>{const mode=S.pair.featureFlags?.[key]||fallback;return `<div class="row"><span class="grow">${esc(title)}</span>${[['off','Desactivada'],['admin','Solo yo'],['all','Ambos']].map(([value,label])=>`<button data-act="feature" data-id="${key}" data-mode="${value}" ${mode===value?'disabled':''}>${mode===value?'✓ ':''}${label}</button>`).join('')}</div>`}).join('')}</section>
 <section class="card"><h2>Mi marca de creador</h2><input id="creator-brand" maxlength="60" value="${esc(S.pair.creatorBrand||'jancktony')}" aria-label="Tu marca"><p><button data-act="brand">Guardar mi marca</button></p></section>`;
}
function render(){
 if(!S.pair||!uid)return;
 const live=S.members.filter(m=>S.pair.members.includes(m.id));
 const me=live.find(m=>m.id===uid),other=live.find(m=>m.id!==uid);
 map?.remove();map=null;
 app.innerHTML=`<header style="display:flex;gap:12px;align-items:center"><div class="grow"><h1>Nosotros · ${isAdmin()?'Tu panel':'Privado'}</h1><span class="sub">Creado por ${esc(S.pair.creatorBrand||'jancktony')}</span></div><button data-act="logout">Cerrar sesión</button></header>
 ${notice?`<p class="card ok" role="status">${esc(notice)}</p>`:''}
 <section class="grid"><div class="card">${person(me)}</div><div class="card">${person(other)}</div></section>
 <section class="card"><h2>Encontrar el teléfono</h2><p class="sub">Muestra la última ubicación recibida, no una posición garantizada en tiempo real.</p><div id="map"></div></section>
 ${adminPanel()}
 <section class="card"><h2>Lugares registrados</h2>${S.places.filter(p=>!p.suggested&&p.name).map(p=>`<div class="row"><span class="grow">${esc(p.name)}</span><span class="sub">${Math.round(p.radius||100)} m</span></div>`).join('')||'<p class="sub">Todavía no hay lugares.</p>'}</section>
 <section class="card"><h2>Sus fotos</h2><div class="photos">${S.photos.map(photo=>`<figure><img alt="Foto compartida" src="data:image/jpeg;base64,${esc(photo.thumb?.toBase64?.()||'')}"><figcaption>${esc(photo.caption||'')}<p class="sub">${ago(photo.at)}</p></figcaption></figure>`).join('')||'<p class="sub">Todavía no hay fotos.</p>'}</div></section>`;
 const located=live.filter(canLocate);if(!located.length){document.getElementById('map').innerHTML='<p class="sub">No hay una ubicación recibida todavía.</p>';return}
 const focus=located.find(m=>m.id===selected)||located[0];
 try{
  map=new maplibregl.Map({container:'map',style:'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',center:[focus.lng,focus.lat],zoom:selected?16:12});
  const bounds=new maplibregl.LngLatBounds();
  for(const member of located){new maplibregl.Marker({color:member.id===uid?'#5eead4':'#f9a8d4'}).setLngLat([member.lng,member.lat]).addTo(map);bounds.extend([member.lng,member.lat])}
  if(!selected&&located.length>1)map.fitBounds(bounds,{padding:55,maxZoom:15,animate:false});
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
   if(['test','corrections','approve','release'].includes(action)&&!release)throw Error('Candidata no disponible');
   if(action==='test'){await updateDoc(p,{testVersion:release.version});notice='Prueba seleccionada solo para tu teléfono. Abre la app o espera a que tenga Wi-Fi; puede requerir confirmación de Android.'}
   if(action==='corrections'){
    const notes=prompt('¿Qué debemos corregir en esta versión?',S.reviews[id]?.notes||'');if(!notes?.trim())return;
    const batch=writeBatch(db);batch.set(doc(p,'releaseReviews',id),{uid,state:'changes_requested',notes:notes.trim().slice(0,2000),updatedAt:serverTimestamp()});batch.update(p,{testVersion:deleteField()});await batch.commit();notice='Correcciones guardadas. No se libera una actualización a tu pareja.';
   }
   if(action==='approve'){await setDoc(doc(p,'releaseReviews',id),{uid,state:'approved',notes:'',updatedAt:serverTimestamp()});notice='Tu prueba quedó aprobada. Ahora puedes liberar para tu pareja.'}
   if(action==='release'&&confirm(`¿Liberar ${release.version} para tu pareja?`)){await updateDoc(p,{relVersion:release.version,relUrl:release.apkPath,relNotes:release.notes||''});notice='Versión liberada para tu pareja'}
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
