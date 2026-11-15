// Owner-only transport metadata. No audio, coordinates, emails, SDP or user identifiers leave the runner.
const {initializeApp,cert}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const {readFileSync}=require('node:fs');
async function github(path,method='GET',body){
 const r=await fetch('https://api.github.com/repos/'+process.env.GITHUB_REPOSITORY+path,{method,headers:{Authorization:'Bearer '+process.env.GH_TOKEN,Accept:'application/vnd.github+json','Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
 if(!r.ok)throw Error('github');return r.json();
}
async function main(){
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a'||!account.private_key)throw Error('project');
 const releases=await github('/releases?per_page=100');
 const release=releases.find(r=>r.tag_name==='v1.13.1');
 if(!release)throw Error('private candidate missing');
 if(release.draft!==true)throw Error('Diagnostic destination must remain private');
 const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
 const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
 const configured=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
 const emails=[...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(x=>x[1]);
 if(emails.length!==2)throw Error('accounts');
 const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e))),uids=users.map(x=>x.uid);
 const result=await db.collection('pairs').where('members','array-contains',uids[0]).get();
 const pairs=result.docs.filter(d=>d.get('members')?.length===2&&d.get('members').every(x=>uids.includes(x)));
 if(pairs.length!==1)throw Error('pair');const pair=pairs[0],admin=pair.get('admin');
 const diagnostic={checkedAt:new Date().toISOString(),releasedVersion:pair.get('relVersion')||null,modes:{walkie:pair.get('featureFlags')?.walkie||'off',relay:pair.get('featureFlags')?.walkieRelay||'off'},phones:[],offers:[],packets:[]};
 for(const uid of uids){
  const [member,presence]=await Promise.all([pair.ref.collection('members').doc(uid).get(),pair.ref.collection('walkiePresence').doc(uid).get()]);
  const time=presence.get('updatedAt')?.toDate()?.getTime();
  diagnostic.phones.push({role:uid===admin?'admin':'partner',version:member.get('appVersion')||null,publishedRelayConsent:presence.exists?presence.get('relay')===true:null,protocol:presence.get('protocol')||null,heartbeatAgeSeconds:time?Math.round((Date.now()-time)/1000):null});
 }
 const calls=await pair.ref.collection('walkieCalls').orderBy('createdAt','desc').limit(8).select('uid','answer','createdAt').get();
 diagnostic.offers=calls.docs.map(d=>({sender:d.get('uid')===admin?'admin':'partner',answered:typeof d.get('answer')==='string',ageSeconds:Math.round((Date.now()-(d.get('createdAt')?.toDate()?.getTime()||0))/1000)}));
 const packets=await pair.ref.collection('walkiePackets').limit(20).select('uid','createdAt').get();
 diagnostic.packets=packets.docs.map(d=>({sender:d.get('uid')===admin?'admin':'partner',ageSeconds:Math.round((Date.now()-(d.get('createdAt')?.toDate()?.getTime()||0))/1000)}));
 const marker='\n<!-- private-walkie-diagnostic -->';
 const body=(release.body||'').split(marker)[0]+marker+'\nDiagnóstico privado de transporte (sin contenido):\n\n'+JSON.stringify(diagnostic,null,2);
 const latest=await github('/releases/'+release.id);if(latest.draft!==true)throw Error('private destination changed');
 const updated=await github('/releases/'+release.id,'PATCH',{body,draft:true});
 if(updated.draft!==true)throw Error('private destination changed');
 console.log('Diagnóstico guardado solo en el borrador privado existente. No se cambiaron permisos, versiones liberadas ni funciones.');
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudo guardar el diagnóstico privado. No se muestran datos personales.');process.exit(1)});
