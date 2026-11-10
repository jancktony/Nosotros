// Private APKs in Firestore's existing free quota; never enables billing or releases to partner.
const {initializeApp,cert} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {readFileSync} = require('node:fs');
const {createHash} = require('node:crypto');
const CHUNK=512*1024, MAX_APK=100*1024*1024;
let stage='inicio';
async function timed(promise, label) {
  stage=label;
  let timer;
  try { return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('TIMEOUT')),60000)})]); }
  finally { clearTimeout(timer); }
}
async function main() {
  const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
  if(account.project_id!=='nosotros-1e43a'||!account.private_key) throw Error('Credencial incorrecta');
  const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
  const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
  const configured=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
  const emails=[...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(m=>m[1]);
  if(emails.length!==2) throw Error('Lista incorrecta');
  const users=await timed(Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),'autenticación'),uids=users.map(u=>u.uid);
  const pairs=await timed(db.collection('pairs').where('members','array-contains',uids[0]).get(),'pareja');
  const eligible=pairs.docs.filter(d=>d.data().members?.length===2&&d.data().members.every(u=>uids.includes(u)));
  if(eligible.length!==1) throw Error('Pareja ambigua');
  const pair=eligible[0],pairId=pair.id;
  if(!uids.includes(pair.get('admin'))) throw Error('Administrador no configurado');
  const version=readFileSync('app/build.gradle.kts','utf8').match(/versionName = "([^"]+)"/)[1];
  if(!/^\d+\.\d+(\.\d+)?$/.test(version)) throw Error('Versión incorrecta');
  const apk=readFileSync('app/build/outputs/apk/release/app-release.apk');
  if(!apk.length||apk.length>MAX_APK) throw Error('APK mayor que el límite de 100 MiB');
  const sha256=createHash('sha256').update(apk).digest('hex'),ref=db.collection('releases').doc(version);
  const previous=await timed(ref.get(),'manifiesto previo');
  if(previous.exists&&previous.get('sha256')!==sha256) throw Error('Versión inmutable: incrementa la versión');
  if(previous.get('ready')===true) { console.log(`Candidata ${version} ya existe. No se libera a la pareja.`);return; }
  // Bounded retention: protect approved and installed versions; keep at most four APKs.
  const members=await Promise.all(uids.map(u=>pair.ref.collection('members').doc(u).get()));
  const protectedVersions=new Set([version,pair.get('relVersion'),...members.map(m=>m.get('appVersion'))].filter(Boolean));
  const catalog=await db.collection('releases').where('pairId','==',pairId).get();
  const stored=catalog.docs.filter(d=>d.get('transport')==='firestore-chunks-v1');
  const keep=new Set(stored.filter(d=>protectedVersions.has(d.id)).map(d=>d.id));keep.add(version);
  if(keep.size>4) throw Error('Demasiadas versiones protegidas');
  for(const d of stored.sort((a,b)=>(b.get('publishedAt')||0)-(a.get('publishedAt')||0))) if(keep.size<4)keep.add(d.id);
  for(const d of stored) if(!keep.has(d.id)) {
    const retired = await db.runTransaction(async tx => {
      const [livePair, ...liveMembers] = await Promise.all([tx.get(pair.ref), ...uids.map(u=>tx.get(pair.ref.collection('members').doc(u)))]);
      if(livePair.get('relVersion')===d.id || liveMembers.some(m=>m.get('appVersion')===d.id)) return false;
      // Approval rules require ready=true, so a version cannot be approved while being removed.
      tx.update(d.ref,{ready:false}); return true;
    });
    if(retired) await db.recursiveDelete(d.ref);
    else keep.add(d.id);
  }
  if(keep.size>4) throw Error('Una aprobación cambió durante la limpieza; reintenta sin añadir almacenamiento');
  const chunkCount=Math.ceil(apk.length/CHUNK);
  const manifest={version,pairId,transport:'firestore-chunks-v1',apkPath:`firestore://releases/${version}`,
    sha256,size:apk.length,chunkSize:CHUNK,chunkCount,publishedAt:Date.now(),
    date:new Date().toISOString().slice(0,10),notes:process.env.RELEASE_NOTES||'Versión de prueba para el administrador.'};
  await timed(ref.set({...manifest,ready:false}),'crear manifiesto incompleto');
  console.log(`Cargando candidata ${version}: ${chunkCount} fragmentos.`);
  for(let i=0;i<chunkCount;i+=4){
    const batch=db.batch();
    for(let n=i;n<Math.min(i+4,chunkCount);n++)batch.set(ref.collection('chunks').doc(String(n).padStart(6,'0')),
      {data:apk.subarray(n*CHUNK,Math.min((n+1)*CHUNK,apk.length)),index:n});
    await timed(batch.commit(),`carga ${i}/${chunkCount}`);
    if(i%32===0) console.log(`Carga privada: ${Math.min(i+4,chunkCount)}/${chunkCount}`);
  }
  // Verify stored bytes before exposing a ready manifest.
  const check=createHash('sha256');let length=0;
  for(let i=0;i<chunkCount;i+=4){
    const refs=Array.from({length:Math.min(4,chunkCount-i)},(_,n)=>ref.collection('chunks').doc(String(i+n).padStart(6,'0')));
    const docs=await timed(db.getAll(...refs),`verificación ${i}/${chunkCount}`);
    for(const d of docs.sort((a,b)=>a.id.localeCompare(b.id))){const bytes=d.get('data');if(!Buffer.isBuffer(bytes))throw Error('Fragmento incorrecto');check.update(bytes);length+=bytes.length;}
  }
  if(length!==apk.length||check.digest('hex')!==sha256)throw Error('Integridad no confirmada');
  await timed(ref.update({ready:true}),'publicar manifiesto verificado');
  console.log(`Candidata privada ${version} verificada: ${apk.length} bytes en ${chunkCount} fragmentos. Solo el panel libera a la pareja.`);
}
main().then(()=>process.exit(0)).catch(e=>{console.error(`No se pudo publicar la candidata privada; fase: ${stage}; código: ${Number.isInteger(e.code)?e.code:'no disponible'}. No se muestran datos privados.`);process.exit(1)});
