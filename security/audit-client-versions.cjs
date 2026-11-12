// Read-only compatibility check. Never prints emails, UIDs, credentials, coordinates or personal content.
const {initializeApp,cert}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const {readFileSync}=require('node:fs');
async function main(){
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a'||!account.private_key)throw Error('project');
 const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
 const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
 const configured=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
 const emails=[...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(x=>x[1]);if(emails.length!==2)throw Error('accounts');
 const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),uids=users.map(u=>u.uid);
 const pairs=await db.collection('pairs').where('members','array-contains',uids[0]).get();
 const valid=pairs.docs.filter(d=>d.get('members')?.length===2&&d.get('members').every(u=>uids.includes(u)));
 if(valid.length!==1)throw Error('pair');const pair=valid[0];
 const result={};for(const uid of uids){const m=await pair.ref.collection('members').doc(uid).get();const v=m.get('appVersion');
  result[uid===pair.get('admin')?'adminVersion':'partnerVersion']=typeof v==='string'&&/^\d+(\.\d+){1,2}$/.test(v)?v:'sin dato';}
 console.log('COMPATIBILITY '+JSON.stringify(result));
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudo verificar compatibilidad. No se muestran datos privados.');process.exit(1)});
