// Private APKs in Firestore's existing free quota; never enables billing or releases to partner.
const {initializeApp,cert} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {readFileSync} = require('node:fs');
const {createHash} = require('node:crypto');
const CHUNK=512*1024, MAX_APK=100*1024*1024;
async function main() {
  const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
  if(account.project_id!=='nosotros-1e43a'||!account.private_key) throw Error('Credencial incorrecta');
  const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
  const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
  const configured=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
  const emails=[...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(m=>m[1]);
  if(emails.length!==2) throw Error('Lista incorrecta');
  const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),uids=users.map(u=>u.uid);
  const pairs=await db.collection('pairs').where('members','array-contains',uids[0]).get();
  const eligible=pairs.docs.filter(d=>d.data().members?.length===2&&d.data().members.every(u=>uids.includes(u)));
  if(eligible.length!==1) throw Error('Pareja ambigua');
  const pair=eligible[0],pairId=pair.id;
  if(!uids.includes(pair.get('admin'))) throw Error('Administrador no configurado');
  const version=readFileSync('app/build.gradle.kts','utf8').match(/versionName = "([^"]+)"/)[1];
  if(!/^\d+\.\d+(\.\d+)?$/.test(version)) throw Error('Versión incorrecta');
  const apk=readFileSync('app/build/outputs/apk/release/app-release.apk');
  if(!apk.length||apk.length>MAX_APK) throw Error('APK mayor que el límite de 100 MiB');
  const sha256=createHash('sha256').update(apk).digest('hex'),ref=db.collection('releases').doc(version);
  const previous=await ref.get();
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
  for(const d of stored) if(!keep.has(d.id))await db.recursiveDelete(d.ref);
  const chunkCount=Math.ceil(apk.length/CHUNK);
  const manifest={version,pairId,transport:'firestore-chunks-v1',apkPath:`firestore://releases/${version}`,
    sha256,size:apk.length,chunkSize:CHUNK,chunkCount,publishedAt:Date.now(),
    date:new Date().toISOString().slice(0,10),notes:process.env.RELEASE_NOTES||'Versión de prueba para el administrador.'};
  await ref.set({...manifest,ready:false});
  for(let i=0;i<chunkCount;i+=4){
    const batch=db.batch();
    for(let n=i;n<Math.min(i+4,chunkCount);n++)batch.set(ref.collection('chunks').doc(String(n).padStart(6,'0')),
      {data:apk.subarray(n*CHUNK,Math.min((n+1)*CHUNK,apk.length)),index:n});
    await batch.commit();
  }
  // Verify stored bytes before exposing a ready manifest.
  const check=createHash('sha256');let length=0;
  for(let i=0;i<chunkCount;i+=4){
    const refs=Array.from({length:Math.min(4,chunkCount-i)},(_,n)=>ref.collection('chunks').doc(String(i+n).padStart(6,'0')));
    const docs=await db.getAll(...refs);
    for(const d of docs.sort((a,b)=>a.id.localeCompare(b.id))){const bytes=d.get('data');if(!Buffer.isBuffer(bytes))throw Error('Fragmento incorrecto');check.update(bytes);length+=bytes.length;}
  }
  if(length!==apk.length||check.digest('hex')!==sha256)throw Error('Integridad no confirmada');
  await ref.update({ready:true});
  console.log(`Candidata privada ${version} verificada: ${apk.length} bytes en ${chunkCount} fragmentos. Solo el panel libera a la pareja.`);
}
main().catch(()=>{console.error('No se pudo publicar la candidata privada. Revisa cuota, permisos y versión. No se muestran datos privados.');process.exitCode=1});
