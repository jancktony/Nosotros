import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
const $=id=>document.getElementById(id),auth=getAuth(initializeApp(window.FB));
$('signin').addEventListener('submit',async e=>{e.preventDefault();$('error').textContent='';try{await setPersistence(auth,browserSessionPersistence);await signInWithEmailAndPassword(auth,$('email').value.trim(),$('password').value)}catch{$('error').textContent='No se pudo entrar. Usa tu cuenta de Nosotros.'}finally{$('password').value=''}});
onAuthStateChanged(auth,async user=>{const allowed=Boolean(user?.emailVerified&&user.email?.toLowerCase()==='jancktoni@gmail.com');$('login').hidden=allowed;$('preview').hidden=!allowed;if(user&&!allowed){$('error').textContent='Esta propuesta es privada del creador.';await signOut(auth)}});
$('logout').onclick=()=>signOut(auth);
const paths={
settings:'<path d="m9 3 1-1h4l1 3 3 1 3-1 2 4-2 2v3l2 2-2 4-3-1-3 1-1 3h-4l-1-3-3-1-3 1-2-4 2-2v-3L2 9l2-4 3 1Z"/><circle cx="12" cy="12" r="3"/>',
map:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2Z M9 3v16 M15 5v16"/>',
locate:'<circle cx="12" cy="12" r="6"/><path d="M12 2v3 M12 19v3 M2 12h3 M19 12h3"/>',
mic:'<rect x="8" y="2" width="8" height="13" rx="4"/><path d="M5 10v2a7 7 0 0 0 14 0v-2 M12 19v3 M8 22h8"/>',
more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
route:'<path d="m3 10 18-7-7 18-3-8Z"/>',
layers:'<path d="m12 3 10 6-10 6L2 9Z M2 13l10 6 10-6 M2 17l10 6 10-6"/>'
};
const icon=name=>'<svg viewBox="0 0 24 24" aria-hidden="true">'+paths[name]+'</svg>';
$('settings').innerHTML=icon('settings');
for(const x of document.querySelectorAll('[data-icon]'))x.innerHTML=icon(x.dataset.icon);
for(const x of document.querySelectorAll('[data-action]'))if(paths[x.dataset.action])x.innerHTML=icon(x.dataset.action);
let layout='sheet',person='kelin',expanded=true,detailSource=null;
const people={kelin:{name:'Kelin',initial:'K',avatar:'k',app:'Instagram',icon:'IG',age:'30 s',battery:'52%'},yo:{name:'Yo',initial:'J',avatar:'j',app:'WhatsApp',icon:'WA',age:'1 min',battery:'73%'}};
function activity(){
 const p=people[person],state=$('activity-state').value;
 if(state==='home')return {title:'Pantalla de inicio',caption:'Detectada hace '+p.age,icon:'⌂',detail:'Se detectó la pantalla de inicio. Se muestra como estado, sin el nombre técnico del lanzador.'};
 if(state==='stale')return {title:'Sin señal reciente',caption:'Última app sin datos recientes',icon:'—',detail:'No se muestra una aplicación antigua como si siguiera abierta.'};
 if(state==='private')return {title:'Actividad no compartida',caption:'No hay una app para mostrar',icon:'—',detail:'Cada persona decide si comparte su actividad.'};
 return {title:p.app,caption:'Última app detectada · hace '+p.age,icon:p.icon,detail:'Última aplicación detectada. Puede haber cambiado desde esa lectura.'};
}
function render(){
 const p=people[person],a=activity();
 $('scene').classList.toggle('layout-top',layout==='top');$('scene').classList.toggle('layout-sheet',layout==='sheet');
 $('scene').classList.toggle('expanded',expanded);$('sheet').classList.toggle('collapsed',!expanded);
 $('expand').setAttribute('aria-expanded',String(expanded));$('expand').setAttribute('aria-label',expanded?'Reducir ficha':'Expandir ficha');
 $('person-name').textContent=p.name;$('sheet-avatar').textContent=p.initial;$('sheet-avatar').className='avatar '+p.avatar;$('battery-value').textContent=p.battery;
 for(const b of document.querySelectorAll('[data-layout]'))b.setAttribute('aria-pressed',String(b.dataset.layout===layout));
 for(const b of document.querySelectorAll('[data-person]'))b.setAttribute('aria-pressed',String(b.dataset.person===person));
 for(const b of document.querySelectorAll('.activity')){
  b.querySelector('.app-icon').textContent=a.icon;
  b.querySelector('.activity-title').textContent=(b.id==='top-activity'?p.name+' · ':'')+a.title;
  b.querySelector('.activity-caption').textContent=a.caption;
 }
 const proximity=$('proximity').value,joined=proximity==='together';
 $('scene').classList.toggle('not-together',!joined);
 $('top-status').textContent=joined?'Juntos':proximity==='checking'?'Confirmando':'2,4 km';
 $('metric-label').textContent=proximity==='apart'?'Distancia':'Estado';
 $('metric-value').textContent=joined?'Juntos':proximity==='checking'?'Confirmando':'≈ 2,4 km';
 document.querySelector('.together-label').textContent=joined?'Juntos':proximity==='checking'?'Confirmando':'Separados';
 if(!$('detail').hidden)fillDetail();
}
function fillDetail(){
 const p=people[person],a=activity();
 $('detail-title').textContent='Actividad de '+p.name;$('detail-icon').textContent=a.icon;$('detail-app').textContent=a.title;$('detail-time').textContent=a.caption;$('detail-copy').textContent=a.detail;
}
function openDetail(e){detailSource=e.currentTarget;fillDetail();$('detail').hidden=false;$('close-detail').focus()}
function closeDetail(){$('detail').hidden=true;detailSource?.focus()}
$('top-activity').onclick=$('sheet-activity').onclick=openDetail;
$('close-detail').onclick=$('detail-back').onclick=closeDetail;
$('detail').onclick=e=>{if(e.target===$('detail'))closeDetail()};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('detail').hidden)closeDetail()});
$('expand').onclick=()=>{expanded=!expanded;render()};
for(const b of document.querySelectorAll('[data-layout]'))b.onclick=()=>{layout=b.dataset.layout;render()};
for(const b of document.querySelectorAll('[data-person]'))b.onclick=()=>{person=b.dataset.person;render()};
$('activity-state').onchange=$('proximity').onchange=render;
$('size').onchange=()=>document.querySelector('.phone').style.setProperty('--phone-width',$('size').value+'px');
let toastTimer;
function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').hidden=false;toastTimer=setTimeout(()=>{$('toast').hidden=true},2500)}
$('live').onclick=()=>toast('Vista previa · no se activa la ubicación real');
for(const b of document.querySelectorAll('[data-action]'))b.onclick=()=>toast('Control de ejemplo · sin cambiar la app');
const descriptions={Encontrar:'Acceso a los teléfonos y a sus acciones autorizadas de búsqueda.',Voz:'Controles del walkie y notas de voz. Esta propuesta no conecta llamadas.',Más:'Historial · Lugares · Fotos. La propuesta conserva los accesos de la aplicación.',Ajustes:'General · Perfil · Actividad · Ubicación · Avisos · Widgets · Cuenta · Administración'};
function tab(name){
 $('other').hidden=name==='Mapa';$('other-title').textContent=name;$('other-copy').textContent=descriptions[name]||'';
 for(const b of document.querySelectorAll('[data-tab]'))b.setAttribute('aria-pressed',String(b.dataset.tab===name));
}
for(const b of document.querySelectorAll('[data-tab]'))b.onclick=()=>tab(b.dataset.tab);
$('settings').onclick=()=>tab('Ajustes');$('back-map').onclick=()=>tab('Mapa');
render();
