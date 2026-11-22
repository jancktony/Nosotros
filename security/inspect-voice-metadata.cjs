// Read-only, redacted metadata. Never selects audio, GPS, emails, SDP or names.
const {initializeApp,cert}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
(async()=>{
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a')throw Error('project');
 const db=getFirestore(initializeApp({credential:cert(account)}));
 const pairs=await db.collection('pairs').select('members','admin','featureFlags','testVersion','relVersion').get();
 const result=[];
 for(const pair of pairs.docs){
  const members=pair.get('members');if(!Array.isArray(members)||members.length!==2)continue;
  const phones=await pair.ref.collection('members').select('appVersion').get();
  const presence=await pair.ref.collection('walkiePresence').select('relay','protocol','updatedAt').get();
  result.push({releasedVersion:pair.get('relVersion')||null,testVersion:pair.get('testVersion')||null,
   modes:{walkie:pair.get('featureFlags')?.walkie||'off',relay:pair.get('featureFlags')?.walkieRelay||'off'},
   phones:members.map(uid=>{const m=phones.docs.find(d=>d.id===uid),p=presence.docs.find(d=>d.id===uid);return {
    role:uid===pair.get('admin')?'admin':'partner',version:m?.get('appVersion')||null,relayConsent:p?.get('relay')===true,
    protocol:p?.get('protocol')||null,heartbeatAgeSeconds:p?.get('updatedAt')?Math.round((Date.now()-p.get('updatedAt').toMillis())/1000):null
   }})});
 }
 console.log(JSON.stringify(result));
})().catch(()=>{console.error('No se pudo leer metadata técnica.');process.exitCode=1});
