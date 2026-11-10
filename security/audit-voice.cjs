const {initializeApp,cert}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const {readFileSync}=require('node:fs');
async function main(){
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a')throw Error('Proyecto incorrecto');
 const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
 const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
 const list=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
 const emails=[...list.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(m=>m[1]);
 if(emails.length!==2)throw Error('Configuración incorrecta');
 const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),uids=users.map(u=>u.uid);
 const pairs=await db.collection('pairs').where('members','array-contains',uids[0]).get();
 const eligible=pairs.docs.filter(d=>d.data().members?.length===2&&d.data().members.every(u=>uids.includes(u)));
 if(eligible.length!==1)throw Error('Pareja ambigua');
 const pair=eligible[0],data=pair.data();
 const owner=users.find(u=>u.email.toLowerCase().startsWith('jancktoni@'));
 const members=await Promise.all(uids.map(u=>pair.ref.collection('members').doc(u).get()));
 const admin=members.find(m=>m.id===data.admin),partner=members.find(m=>m.id!==data.admin);
 console.log(JSON.stringify({adminMatchesOwner:data.admin===owner?.uid,
  adminReportedVersion:admin?.get('appVersion')||'sin datos',partnerReportedVersion:partner?.get('appVersion')||'sin datos',
  adminLastSeen:admin?.get('updatedAt')||0,approvedVersion:data.relVersion||null,
  walkie:data.featureFlags?.walkie||'off',voiceNotes:data.featureFlags?.voiceNotes||'off'}));
}
main().then(()=>process.exit(0)).catch(()=>{console.error('Diagnóstico no disponible; no se muestran datos privados.');process.exit(1)});
