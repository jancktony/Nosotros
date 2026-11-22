const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const vm=require('node:vm');
const {JSDOM}=require('jsdom');

async function fixture(t){
 const dom=new JSDOM('<main id="app"></main>',{url:'https://nosotros.test',runScripts:'outside-only'});
 t.after(()=>dom.window.close());
 const maps=[],markers=[];
 class MapView{
  constructor(options){this.camera={center:options.center,zoom:options.zoom,bearing:0,pitch:0};this.removed=false;maps.push(this)}
  on(){} setProjection(){} addLayer(){} getCenter(){return this.camera.center} getZoom(){return this.camera.zoom}
  getBearing(){return this.camera.bearing} getPitch(){return this.camera.pitch} remove(){this.removed=true}
  fitBounds(){} easeTo(values){Object.assign(this.camera,values)}
 }
 class Marker{
  constructor(){markers.push(this)}setLngLat(point){this.point=point;return this}addTo(){return this}remove(){this.removed=true}
 }
 dom.window.maplibregl={Map:MapView,Marker,LngLatBounds:class{extend(){return this}}};
 const source=readFileSync(process.env.NOSOTROS_WEB_SOURCE||require.resolve('../app.js'),'utf8').replace(/^import .+;\r?\n/gm,'');
 const api=await vm.runInContext(`(async()=>{${source}\nreturn {S,render,clear,tab(v){webTab=v},session(){uid='alice';pairId='demo'}}})()`,dom.getInternalVMContext());
 api.session();api.S.pair={members:['alice','bob'],admin:'alice',creatorBrand:'Jancktony Murgas'};
 api.S.members=[{id:'alice',name:'Yo',lat:10.99,lng:-74.8,updatedAt:Date.now(),locationAt:Date.now(),battery:80},
  {id:'bob',name:'Kelin',lat:10.98,lng:-74.81,updatedAt:Date.now(),locationAt:Date.now(),battery:62}];
 return {api,dom,maps,markers};
}

test('GPS, battery and activity snapshots retain the map instance, canvas and manual camera',async t=>{
 const {api,dom,maps,markers}=await fixture(t);api.render();
 const host=dom.window.document.getElementById('map');maps[0].camera.zoom=18;
 for(let n=0;n<50;n++){api.S.members[1].lat+=.00001;api.render()}
 assert.equal(maps.length,1);assert.equal(maps[0].removed,false);assert.equal(maps[0].camera.zoom,18);
 assert.equal(dom.window.document.getElementById('map'),host);assert.equal(markers.length,2);
 assert.equal(markers[1].point[1],api.S.members[1].lat);
 api.clear();assert.equal(maps[0].removed,true);assert.ok(markers.every(m=>m.removed));
});

test('creator draft and text selection survive incoming snapshots',async t=>{
 const {api,dom}=await fixture(t);api.tab('admin');api.render();
 const input=dom.window.document.getElementById('creator-brand');input.focus();input.value='JK · Jancktony';input.setSelectionRange(3,7);
 input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));api.S.members[1].battery=61;api.render();
 const after=dom.window.document.getElementById('creator-brand');
 assert.equal(after.value,'JK · Jancktony');assert.equal(dom.window.document.activeElement,after);
 assert.equal(after.selectionStart,3);assert.equal(after.selectionEnd,7);
});

test('fresh battery data does not turn an old GPS fix into a recent location',async t=>{
 const {api,dom}=await fixture(t);api.S.members[1].locationAt=Date.now()-10*60000;api.render();
 const partner=dom.window.document.querySelectorAll('.grid article')[1];
 assert.match(partner.textContent,/Hace 10 min/);
 api.S.members[1].locationAt=0;api.render();
 assert.match(dom.window.document.querySelectorAll('.grid article')[1].textContent,/Sin señal/);
});

test('revoking location data removes only that marker and sign-out releases all map resources',async t=>{
 const {api,maps,markers}=await fixture(t);api.render();api.S.members[1].lat=0;api.S.members[1].lng=0;api.render();
 assert.equal(maps.length,1);assert.equal(markers[1].removed,true);assert.notEqual(markers[0].removed,true);
 api.tab('settings');api.render();assert.equal(maps[0].removed,true);
 api.tab('phones');api.render();assert.equal(maps.length,2);api.clear();assert.equal(maps[1].removed,true);
});

test('untrusted names are displayed as text through incremental updates',async t=>{
 const {api,dom}=await fixture(t);api.render();api.S.members[1].name='<img src=x onerror=alert(1)>';api.render();
 const partner=dom.window.document.querySelectorAll('.grid article')[1];
 assert.equal(partner.querySelector('img'),null);assert.match(partner.textContent,/<img src=x onerror=alert\(1\)>/);
});

test('personal themes default to pink and survive live updates',async t=>{
 const {api,dom}=await fixture(t);assert.equal(dom.window.document.body.dataset.theme,'pink');api.tab('settings');api.render();
 for(const theme of ['redblack','blackpink','fullpink','pink']){
  dom.window.document.querySelector(`[data-act="theme"][data-mode="${theme}"]`).click();
  assert.equal(dom.window.document.body.dataset.theme,theme);api.S.members[1].battery--;api.render();
  assert.equal(dom.window.document.body.dataset.theme,theme);assert.equal(dom.window.localStorage.getItem('nosotros-web-theme'),theme);
 }
});
test('favorites persist locally without editing the partner photo',async t=>{
 const {api,dom}=await fixture(t);api.S.photos=[{id:'photo',at:Date.now(),caption:'Recuerdo'}];api.tab('photos');api.render();
 dom.window.document.querySelector('[data-act="favorite-photo"]').click();
 assert.deepEqual(JSON.parse(dom.window.localStorage.getItem('nosotros-photo-favorites')),['photo']);
 assert.match(dom.window.document.querySelector('.photos').textContent,/Favorito/);
 dom.window.document.querySelector('[data-act="favorite-photo"]').click();assert.deepEqual(JSON.parse(dom.window.localStorage.getItem('nosotros-photo-favorites')),[]);
});
test('shared journeys escape destinations and label estimates honestly',async t=>{
 const {api,dom}=await fixture(t);api.S.journeys=[{id:'alice',name:'<img src=x>',state:'active',expiresAt:Date.now()+600000,etaAt:Date.now()+300000}];api.render();
 const card=dom.window.document.querySelector('.journey-card');assert.ok(card);assert.equal(card.querySelector('img'),null);assert.match(card.textContent,/Estimación al iniciar/);assert.match(card.textContent,/Dejar de compartir/);
 api.S.journeys[0].expiresAt=Date.now()-1000;api.render();assert.equal(dom.window.document.querySelector('.journey-card'),null);
});
