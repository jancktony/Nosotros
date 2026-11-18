const assert=require('node:assert/strict'),{readFileSync}=require('node:fs'),{JSDOM}=require('jsdom');
const dom=new JSDOM(readFileSync('preview-juntos/rediseno.html','utf8'),{url:'https://preview.invalid',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,d=w.document;
w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};
w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')};
w.eval("function initializeApp(){return {}};function getAuth(){return {}};function onAuthStateChanged(a,cb){cb(null)};function setPersistence(){return Promise.resolve()};function signInWithEmailAndPassword(){return Promise.resolve()};function signOut(){return Promise.resolve()};const browserSessionPersistence={};");
w.eval(readFileSync('preview-juntos/rediseno.js','utf8').replace(/^import[^\n]*\n/gm,''));
const click=s=>{const el=d.querySelector(s);assert.ok(el,'Missing control: '+s);el.click();return el};
try {
 assert.equal(d.getElementById('preview').hidden,true,'The proposal must stay gated before authentication');
 d.getElementById('preview').hidden=false;
 for(const page of ['voice','find','history','places','photos','settings','admin','map']){
  click('[data-go="'+page+'"]');assert.equal(d.getElementById('screen-'+page).hidden,false);
  assert.equal([...d.querySelectorAll('.screen')].filter(e=>!e.hidden).length,1);
 }
 click('[data-go="places"]');
 const search=d.getElementById('place-search');search.value='universidad';search.dispatchEvent(new w.Event('input'));
 assert.equal(d.querySelectorAll('.place-item').length,1);assert.match(d.getElementById('place-list').textContent,/Universidad/);
 click('#add-place');assert.match(d.getElementById('place-list').textContent,/Nuevo lugar/);
 click('[data-go="voice"]');click('#reception');assert.equal(d.getElementById('mic').disabled,true);
 click('#reception');assert.equal(d.getElementById('mic').disabled,false);
 const mic=d.getElementById('mic');mic.dispatchEvent(new w.KeyboardEvent('keydown',{key:' ',bubbles:true}));
 assert.equal(mic.getAttribute('aria-pressed'),'true');assert.match(d.getElementById('voice-status').textContent,/no se graba/);
 mic.dispatchEvent(new w.KeyboardEvent('keyup',{key:' ',bubbles:true}));assert.equal(mic.getAttribute('aria-pressed'),'false');
 click('[data-go="settings"]');click('[data-setting="Perfil"]');click('#edit-profile');
 d.getElementById('demo-name').value='<b>Nombre de prueba</b>';click('#save-demo-name');
 assert.equal(d.getElementById('profile-name').textContent,'<b>Nombre de prueba</b>');assert.equal(d.getElementById('profile-name').children.length,0);
 click('[data-go="photos"]');click('[data-memory="0"]');assert.ok(d.getElementById('detail-dialog').open);click('#close-dialog');
 click('[data-go="history"]');click('[data-filter="yesterday"]');assert.match(d.getElementById('timeline').textContent,/Llegó al parque/);
 click('[data-go="admin"]');click('#release-next');assert.match(d.getElementById('release-status').textContent,/solo para el dueño/);
 click('#release-next');assert.match(d.getElementById('release-status').textContent,/aprobada/);
 click('#release-next');assert.match(d.getElementById('release-status').textContent,/simulada/);
 click('[data-go="map"]');click('#sheet-toggle');assert.equal(d.querySelector('.sheet-details').hidden,false);
 click('#mobile');assert.ok(d.body.classList.contains('compact'));click('#wide');assert.ok(!d.body.classList.contains('compact'));
 console.log('Private gate and interactive navigation, voice, places, history, gallery, profile and simulated release checks passed.');
} finally { w.close(); }
