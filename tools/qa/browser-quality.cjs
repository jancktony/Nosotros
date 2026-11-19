// Manual QA: requires Playwright and Chromium. Synthetic session; no Firebase or phone access.
const {chromium}=require('playwright');
const fs=require('node:fs');
const root=require('node:path').resolve(__dirname,'../../web');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.NOSOTROS_CHROMIUM||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const results=[];
 try{
 for(const width of [320,390,430,768,1440]){
  const page=await browser.newPage({viewport:{width,height:900},ignoreHTTPSErrors:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://tiles.openfreemap.org/**',r=>r.fulfill({contentType:'application/json',body:JSON.stringify({version:8,sources:{},layers:[{id:'background',type:'background',paint:{'background-color':'#18232c'}}]})}));
  await page.route('https://nosotros.test/**',async r=>{
   const pathname=new URL(r.request().url()).pathname;
   if(pathname==='/config.js')return r.fulfill({contentType:'text/javascript',body:''});
   if(pathname==='/app.js'){
    let source=fs.readFileSync(root+'/app.js','utf8').replace(/^import .+;\r?\n/gm,'');
    source='const doc=()=>({});\n'+source;
    source+=`\nconst BaseMap=maplibregl.Map;window.__mapCount=0;maplibregl.Map=class extends BaseMap{constructor(o){super(o);window.__map=this;window.__mapCount++}};
uid='alice';pairId='demo';S.pair={members:['alice','bob'],admin:'alice',creatorBrand:'Jancktony Murgas'};S.members=[{id:'alice',name:'Yo',lat:10.99,lng:-74.8,updatedAt:Date.now(),locationAt:Date.now(),battery:80},{id:'bob',name:'Kelin',lat:10.98,lng:-74.81,updatedAt:Date.now(),locationAt:Date.now(),battery:62}];render();
window.__quality={updates(){for(let n=0;n<50;n++){S.members[1].lat+=.000001;render()}},render};`;
    return r.fulfill({contentType:'text/javascript',body:source});
   }
   const file=root+(pathname==='/'?'/index.html':pathname);
   if(fs.existsSync(file)&&fs.statSync(file).isFile())return r.fulfill({contentType:pathname.endsWith('.css')?'text/css':pathname.endsWith('.js')?'text/javascript':'text/html',body:fs.readFileSync(file)});
   return r.fulfill({status:404,body:''});
  });
  await page.goto('https://nosotros.test/',{waitUntil:'networkidle'});
  await page.waitForFunction(()=>window.__map?.isStyleLoaded(),{timeout:15000});
  await page.evaluate(()=>{window.__map.jumpTo({zoom:17});window.__canvas=document.querySelector('#map canvas');window.__quality.updates()});
  const check=await page.evaluate(()=>({instances:window.__mapCount,zoom:window.__map.getZoom(),canvas:window.__canvas===document.querySelector('#map canvas'),overflow:document.documentElement.scrollWidth>innerWidth}));
  if(check.instances!==1||check.zoom!==17||!check.canvas||check.overflow)throw Error('Map regression: '+JSON.stringify({width,...check}));
  await page.getByRole('button',{name:'Administración',exact:true}).click();
  const brand=page.locator('#creator-brand');await brand.fill('JK · Jancktony');
  await page.evaluate(()=>{const x=document.getElementById('creator-brand');x.focus();x.setSelectionRange(3,7);window.__quality.render()});
  const draft=await page.locator('#creator-brand').evaluate(x=>({value:x.value,focused:document.activeElement===x,start:x.selectionStart,end:x.selectionEnd}));
  if(draft.value!=='JK · Jancktony'||!draft.focused||draft.start!==3||draft.end!==7)throw Error('Draft regression '+JSON.stringify(draft));
  if(errors.length)throw Error('Page errors: '+errors.join(';'));
  results.push({width,...check,draft:true});await page.close();
 }
 console.log(JSON.stringify(results));
 }finally{await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
