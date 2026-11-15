// Read-only export. Personal content is encrypted before leaving the runner.
const {initializeApp, cert}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const {readFileSync,writeFileSync}=require('node:fs');
const {createPublicKey,randomBytes,publicEncrypt,createCipheriv,constants}=require('node:crypto');
async function main(){
 const recipient=createPublicKey(Buffer.from(process.env.REVIEW_RECIPIENT||'', 'base64'));
 if(recipient.asymmetricKeyType!=='rsa'||recipient.asymmetricKeyDetails.modulusLength<3072)throw Error('recipient');
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a'||!account.private_key)throw Error('project');
 const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
 const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
 const configured=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
 const emails=[...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(x=>x[1]);
 if(emails.length!==2)throw Error('accounts');
 const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),uids=users.map(u=>u.uid);
 const pairs=await db.collection('pairs').where('members','array-contains',uids[0]).get();
 const valid=pairs.docs.filter(d=>d.get('members')?.length===2&&d.get('members').every(u=>uids.includes(u)));
 if(valid.length!==1)throw Error('pair');
 const pair=valid[0];
 const docs=await pair.ref.collection('releaseReviews').orderBy('updatedAt','desc').limit(10).get();
 const reviews=docs.docs.filter(d=>d.get('uid')===pair.get('admin')).map(d=>({version:d.id,state:d.get('state'),notes:d.get('notes')||'',updatedAt:d.get('updatedAt')?.toDate().toISOString()}));
 const requestsSnapshot=await pair.ref.collection('improvementRequests').orderBy('createdAt','desc').limit(20).get();
 const requests=requestsSnapshot.docs.filter(d=>d.get('uid')===pair.get('admin')).map(d=>({id:d.id,text:d.get('text'),state:d.get('state'),version:d.get('version'),createdAt:d.get('createdAt')?.toDate().toISOString()}));
 const diagnostics={featureModes:{walkie:pair.get('featureFlags')?.walkie||'off',relay:pair.get('featureFlags')?.walkieRelay||'off'},phones:[],calls:[]};
 for(const user of uids){
  const [member,presence]=await Promise.all([pair.ref.collection('members').doc(user).get(),pair.ref.collection('walkiePresence').doc(user).get()]);
  diagnostics.phones.push({role:user===pair.get('admin')?'admin':'partner',version:member.get('appVersion')||'',relay:presence.get('relay')===true,protocol:presence.get('protocol')||'',updatedAt:presence.get('updatedAt')?.toDate().toISOString()||null});
 }
 const calls=await pair.ref.collection('walkieCalls').orderBy('createdAt','desc').limit(8).select('uid','createdAt','answer').get();
 diagnostics.calls=calls.docs.map(d=>({sender:d.get('uid')===pair.get('admin')?'admin':'partner',answered:typeof d.get('answer')==='string',createdAt:d.get('createdAt')?.toDate().toISOString()||null}));
 const key=randomBytes(32),iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,iv);
 const encrypted=Buffer.concat([cipher.update(JSON.stringify({reviews,requests,diagnostics}),'utf8'),cipher.final()]);
 const b64=b=>b.toString('base64');
 writeFileSync('reviews.encrypted.json',JSON.stringify({key:b64(publicEncrypt({key:recipient,padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha256'},key)),iv:b64(iv),tag:b64(cipher.getAuthTag()),data:b64(encrypted)}));
 console.log('Exportación cifrada lista; no se modificaron datos ni versiones.');
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudo leer la revisión; no se muestran datos privados.');process.exit(1)});
