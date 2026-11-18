import {initializeApp} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
import {getAuth,signInWithEmailAndPassword,signOut,onAuthStateChanged,setPersistence,browserSessionPersistence} from 'https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js';
const $=id=>document.getElementById(id),auth=getAuth(initializeApp(window.FB));
$('signin').onsubmit=async e=>{e.preventDefault();try{await setPersistence(auth,browserSessionPersistence);await signInWithEmailAndPassword(auth,$('email').value.trim(),$('password').value)}catch{$('error').textContent='No se pudo entrar con esa cuenta.'}finally{$('password').value=''}};
onAuthStateChanged(auth,async u=>{const ok=!!(u?.emailVerified&&u.email?.toLowerCase()==='jancktoni@gmail.com');$('login').hidden=ok;$('preview').hidden=!ok;if(u&&!ok){$('error').textContent='Propuesta exclusiva del creador.';await signOut(auth)}});
$('logout').onclick=()=>signOut(auth);
$('mobile').onclick=()=>document.body.classList.add('compact');$('wide').onclick=()=>document.body.classList.remove('compact');
function page(id){document.querySelectorAll('.page').forEach(x=>x.hidden=x.id!==id);document.querySelectorAll('[data-page]').forEach(x=>x.setAttribute('aria-current',String(x.dataset.page===id)))}
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>page(b.dataset.page));
document.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>page(b.dataset.open));
$('settings').onclick=()=>page('more');
$('receive').onclick=()=>{const on=$('receive').getAttribute('aria-pressed')!=='true';$('receive').setAttribute('aria-pressed',String(on));$('receive').textContent=on?'Activada ✓':'Desactivada';$('mic').disabled=!on;$('voice-state').textContent=on?'Demostración · canal listo':'Demostración · recepción desactivada'};
const mic=$('mic');function stop(){mic.setAttribute('aria-pressed','false');$('voice-state').textContent=$('receive').getAttribute('aria-pressed')==='true'?'Demostración · canal listo':'Demostración · recepción desactivada'}
mic.onpointerdown=e=>{if(mic.disabled)return;mic.setPointerCapture(e.pointerId);mic.setAttribute('aria-pressed','true');$('voice-state').textContent='Simulación de pulsación · no se graba audio'};
mic.onpointerup=stop;mic.onpointercancel=stop;mic.onlostpointercapture=stop;
mic.onkeydown=e=>{if(e.key===' '||e.key==='Enter'){e.preventDefault();mic.setAttribute('aria-pressed','true');$('voice-state').textContent='Simulación · no se graba audio'}};mic.onkeyup=e=>{if(e.key===' '||e.key==='Enter')stop()};mic.onblur=stop;
document.querySelectorAll('.mock').forEach(b=>b.onclick=()=>{$('notice').textContent='Solo vista previa: no se envió ninguna solicitud al teléfono.'});

$('glass-level').oninput=e=>document.documentElement.style.setProperty('--glass-alpha',String(1-Number(e.target.value)/100));
