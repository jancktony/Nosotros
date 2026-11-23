const {chromium}=require('playwright'),http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve('preview-juntos');
 const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  if(name==='/config.js'){res.setHeader('Content-Type','text/javascript');return res.end('window.FB={projectId:"demo-preview"};')}
  const file=path.resolve(root,'.'+name);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.statusCode=404;return res.end();}
  res.setHeader('Content-Type',file.endsWith('.svg')?'image/svg+xml':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html');
  res.end(fs.readFileSync(file));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.PREVIEW_BROWSER_BIN,args:["--no-sandbox"]});
 try{
  for(const width of [320,390,430,1024,1440]){
   const page=await browser.newPage({viewport:{width,height:920}});
   await page.route('https://www.gstatic.com/firebasejs/**',route=>route.fulfill({contentType:'text/javascript',body:'export function initializeApp(){return {}}; export function getAuth(){return {}}; export function onAuthStateChanged(a,cb){cb({emailVerified:true,email:"jancktoni@gmail.com"})}; export function signInWithEmailAndPassword(){return Promise.resolve()}; export function signOut(){return Promise.resolve()}; export function setPersistence(){return Promise.resolve()}; export const browserSessionPersistence={};'}));
   await page.route('https://unpkg.com/**',route=>route.abort());
   await page.goto('http://127.0.0.1:'+server.address().port+'/rediseno.html');
   await page.waitForFunction(()=>!document.getElementById('preview').hidden);
   assert.equal(await page.locator('html').getAttribute('data-theme'),'pink');
   await page.locator('.activity-line img').waitFor();
   assert.equal(await page.locator('.activity-line img').evaluate(e=>e.complete&&e.naturalWidth>0),true,'Bundled Instagram icon loads');
   const activity=await page.locator('.activity-line').evaluate(e=>({height:e.getBoundingClientRect().height,fits:e.scrollWidth<=e.clientWidth+1}));
   assert.ok(activity.height<=36&&activity.fits,'Compact activity fits at '+width);
   if(width===390)await page.screenshot({path:'/tmp/nosotros-actividad-compacta.png'});
   for(const id of ['map','voice','find','history','places','photos','more','settings','admin','improvements']){
    await page.evaluate(id=>document.querySelector('[data-go="'+id+'"]').click(),id);
    await page.waitForTimeout(300);
    const bad=await page.evaluate(()=>{
     const app=document.querySelector('.app').getBoundingClientRect(),bad=[];
     if(document.documentElement.scrollWidth>innerWidth+1)bad.push('document overflow');
     for(const el of document.querySelectorAll('.app button,.app input')){
      const r=el.getBoundingClientRect();if(!r.width||!r.height)continue;
      if(r.x<app.x-1||r.right>app.right+1||el.scrollWidth>el.clientWidth+2)bad.push((el.getAttribute('aria-label')||el.textContent.trim()).slice(0,70));
     }
     return bad;
    });
    assert.deepEqual(bad,[],'Clipped controls at width '+width+' on '+id+': '+bad.join(', '));
   }
   await page.evaluate(()=>document.querySelector('[data-go="places"]').click());
   await page.locator('#add-place').click();
   await page.locator('#address-city').fill('Barranquilla');
   await page.locator('#address-street').fill('Calle 56 #43-20');
   assert.equal(await page.locator('#address-save').isDisabled(),true);
   await page.locator('#address-locate').click();
   assert.equal(await page.locator('#address-summary').textContent(),'Calle 56 #43-20');
   const addressFit=await page.locator('#detail-dialog').evaluate(e=>e.scrollWidth<=e.clientWidth+1&&e.getBoundingClientRect().right<=innerWidth);
   assert.equal(addressFit,true,'Address form fits at width '+width);
   await page.locator('#address-save').click();
   assert.match(await page.locator('#address-map-label').textContent(),/Calle 56 #43-20/);
   await page.locator('#address-map-label button').click();
   await page.evaluate(()=>document.querySelector('[data-go="map"]').click());
   await page.locator('#route').click();await page.locator('#end-route').click();
   const handle=page.locator('#sheet-toggle'),grip=await handle.boundingBox();
   await page.mouse.move(grip.x+grip.width/2,grip.y+grip.height/2);await page.mouse.down();
   await page.mouse.move(grip.x+grip.width/2,grip.y+grip.height/2+60,{steps:5});await page.mouse.up();
   assert.equal(await page.locator('#person-sheet').getAttribute('data-size'),'compact','Drag-down compact sheet '+width);
   await page.locator('#route').click();await page.locator('#route-summary').click();
   assert.equal(await page.locator('#route-actions').isVisible(),true);
   const routeFit=await page.locator('#route-summary').evaluate(e=>e.scrollWidth<=e.clientWidth+1);
   assert.equal(routeFit,true,'Route metrics overflow '+width);
   await page.locator('#finish-route').click();assert.equal(await page.locator('#route-panel').isVisible(),false);
   await page.evaluate(()=>document.querySelector('[data-go="settings"]').click());
   await page.locator('[data-setting="General"]').click();
   const original=await page.evaluate(()=>['--mint','--pink'].map(n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim()));
   for(const theme of ['redblack','blackpink','fullpink','pink']){
    await page.locator('[data-theme-choice="'+theme+'"]').click();
    assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
    const colors=await page.evaluate(()=>['--bg','--ui','--ink'].map(n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim()));
    if(theme==='redblack'){const micColor=await page.locator('#mic').evaluate(e=>getComputedStyle(e).backgroundColor);assert.equal(micColor,'rgb(230, 0, 46)','Red theme must render saturated red on microphone')}
    assert.equal(colors[0],{pink:'#f9f0f3',redblack:'#0b0b0d',blackpink:'#120e13',fullpink:'#fce4ef'}[theme]);
    assert.equal(await page.locator('[data-theme-choice][aria-pressed="true"]').count(),1);
    const fit=await page.locator('.theme-choices').evaluate(e=>e.scrollWidth<=e.clientWidth+1);
    assert.equal(fit,true,'Theme options overflow '+width);
    await page.evaluate(()=>document.querySelector('[data-go="more"]').click());
    const moreColors=await page.evaluate(()=>{
     const probe=document.createElement('span');probe.style.color='var(--ui)';document.body.append(probe);
     const expected=getComputedStyle(probe).color;probe.remove();
     return {expected,actual:[...document.querySelectorAll('#screen-more .more-card>span:first-child,#screen-more .story-card>span')].map(e=>getComputedStyle(e).color)};
    });
    assert.equal(moreColors.actual.length,6);
    assert.ok(moreColors.actual.every(c=>c===moreColors.expected),'More icons must follow '+theme+' at '+width);
    await page.evaluate(()=>document.querySelector('[data-go="settings"]').click());
   }
   await page.evaluate(()=>document.querySelector('[data-go="improvements"]').click());
   for(const tab of ['0','1','2','3','4','5']){
    await page.locator('[data-improvement="'+tab+'"]').click();
    const fit=await page.locator('#improvement-content').evaluate(e=>[...e.querySelectorAll('button,textarea')].every(b=>b.scrollWidth<=b.clientWidth+2));
    assert.equal(fit,true,'Improvement controls overflow '+tab+' at '+width);
   }
   await page.locator('[data-trial="test-approve"]').click();assert.equal(await page.locator('[data-trial="test-release"]').isEnabled(),true);
   await page.locator('#trial-feedback').fill('Hay que corregir el ejemplo');assert.equal(await page.locator('[data-trial="test-release"]').isEnabled(),false);
   await page.evaluate(()=>document.querySelector('[data-go="settings"]').click());
   await page.locator('[data-theme-choice="redblack"]').click();await page.reload();
   await page.waitForFunction(()=>!document.getElementById('preview').hidden);
   assert.equal(await page.locator('html').getAttribute('data-theme'),'redblack','Choice survives reload');
   await page.evaluate(()=>document.querySelector('[data-go="settings"]').click());
   await page.locator('[data-theme-choice="pink"]').click();
   await page.locator('[data-accent="lilac"]').click();
   const changed=await page.evaluate(()=>['--mint','--pink'].map(n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim()));
   assert.deepEqual(changed,original,'Base colors changed '+width);
   assert.deepEqual(original,['#a1e4cf','#efc4d3']);
   await page.evaluate(()=>document.querySelector('[data-go="voice"]').click());
   const mic=await page.locator('#mic').boundingBox(),nav=await page.locator('#bottom-nav').boundingBox();
   if(nav)assert.ok(mic.y+mic.height<=nav.y,'Microphone behind navigation '+width);
   await page.mouse.move(mic.x+mic.width/2,mic.y+mic.height/2);await page.mouse.down();
   await page.mouse.move(mic.x+mic.width+40,mic.y+mic.height+20,{steps:4});
   assert.equal(await page.locator('#mic').getAttribute('aria-pressed'),'true');await page.mouse.up();
   assert.equal(await page.locator('#mic').getAttribute('aria-pressed'),'false');
   console.log('Navigation, sheet drag, route, original colors and microphone capture: '+width+'px');
   await page.close();
  }
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1)});
