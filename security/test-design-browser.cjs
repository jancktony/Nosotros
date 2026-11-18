const {chromium}=require('playwright'),http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve('preview-juntos');
 const server=http.createServer((req,res)=>{
  const name=new URL(req.url,'http://localhost').pathname;
  if(name==='/config.js'){res.setHeader('Content-Type','text/javascript');return res.end('window.FB={projectId:"demo-preview"};')}
  const file=path.resolve(root,'.'+name);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.statusCode=404;return res.end();}
  res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html');
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
   for(const id of ['map','voice','find','history','places','photos','more','settings','admin']){
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
   console.log('No horizontal overflow/clipped controls: '+width+'px');
   await page.close();
  }
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1)});
