const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve('web');
 const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  const file=path.resolve(root,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.statusCode=404;return res.end()}
  let data=fs.readFileSync(file);if(file.endsWith('index.html'))data=data.toString().replace(/<script\b[^>]*>[\s\S]*?<\/script>/g,'');
  res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html');res.end(data);
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,executablePath:process.env.PREVIEW_BROWSER_BIN,args:['--no-sandbox']});
 try{
  const source=fs.readFileSync(path.join(root,'app.js'),'utf8').replace(/^import .+;\r?\n/gm,'');
  for(const width of [320,390,430,1024,1440]){
   const page=await browser.newPage({viewport:{width,height:1000}});
   await page.goto('http://127.0.0.1:'+server.address().port+'/');
   await page.evaluate(async source=>{
    const run=Object.getPrototypeOf(async()=>{}).constructor;
    window.fixture=await new run(source+`\nreturn {S,render,tab(t){webTab=t},session(){uid='alice';pairId='demo'}};`)();
    const f=window.fixture;f.session();f.S.pair={members:['alice','bob'],admin:'alice',featureFlags:{sharedTrips:'all',batteryAlerts:'all',photoFavorites:'all'},creatorBrand:'Jancktony Murgas'};
    f.S.members=[{id:'alice',name:'Jancktony',lat:11,lng:-74.8,updatedAt:Date.now(),locationAt:Date.now(),battery:80,appVersion:'1.16.0'},
     {id:'bob',name:'Kelin',lat:11.01,lng:-74.81,updatedAt:Date.now(),locationAt:Date.now(),battery:15,shareBatteryAlerts:true,appVersion:'1.15.10'}];
    f.S.journeys=[{id:'bob',name:'Universidad de ejemplo',etaAt:Date.now()+600000,expiresAt:Date.now()+3600000,state:'active'}];f.render();
   },source);
   for(const theme of ['pink','redblack','blackpink','fullpink']){
    await page.evaluate(()=>{fixture.tab('settings');fixture.render()});await page.locator(`[data-act="theme"][data-mode="${theme}"]`).click();
    assert.equal(await page.locator('body').getAttribute('data-theme'),theme);
    for(const tab of ['phones','photos','settings','admin']){
     await page.evaluate(tab=>{fixture.tab(tab);fixture.render()},tab);
     const overflow=await page.evaluate(()=>{
      const bad=[];if(document.documentElement.scrollWidth>innerWidth+1)bad.push('page');
      for(const el of document.querySelectorAll('button,input,textarea')){const r=el.getBoundingClientRect();if(r.width&&r.height&&(r.left < -1||r.right>innerWidth+1||el.scrollWidth>el.clientWidth+2))bad.push((el.textContent||el.id).slice(0,70))}
      return bad;
     });assert.deepEqual(overflow,[],`${width}px ${theme} ${tab}: overflow`);
     if(process.env.QA_SCREENSHOT_DIR&&width===390&&tab==='phones')await page.screenshot({path:path.join(process.env.QA_SCREENSHOT_DIR,`web-${theme}.png`),fullPage:true});
    }
   }
   console.log('Web responsive and four themes verified at '+width+'px');await page.close();
  }
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exit(1)});
