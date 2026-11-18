// Backend-only signer. No Firebase credentials, signing key, tickets or audio keys are printed.
const {initializeApp,cert}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {generateKeyPairSync,createPublicKey,createHash,randomBytes,sign}=require('node:crypto');
const {readFileSync,writeFileSync}=require('node:fs');
let phase='start';
const hash=value=>createHash('sha256').update(value).digest('hex');
async function main(){
 const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
 if(account.project_id!=='nosotros-1e43a'||!account.private_key)throw Error('account');
 const app=initializeApp({credential:cert(account)}),db=getFirestore(app);
 phase='authorized-pair';
 const source=readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
 const list=source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1]||'';
 const emails=[...list.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(x=>x[1]);
 if(emails.length!==2)throw Error('accounts');
 const users=await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e)));
 if(users.some(u=>u.disabled||!u.emailVerified))throw Error('unverified');
 const ids=users.map(u=>u.uid);
 const all=await db.collection('pairs').where('members','array-contains',ids[0]).get();
 const eligible=all.docs.filter(d=>d.get('members')?.length===2&&d.get('members').every(id=>ids.includes(id)));
 if(eligible.length!==1)throw Error('pair');
 const pair=eligible[0];
 if(!ids.includes(pair.get('admin')))throw Error('owner');
 phase='private-signing-key';
 const ref=db.doc('voiceSecrets/signing'),saved=await ref.get();
 let privateKey=saved.get('privateKey');
 if(!privateKey){
  privateKey=generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}}).privateKey;
  await ref.create({privateKey,createdAt:FieldValue.serverTimestamp()});
 }
 const publicJwk=createPublicKey(privateKey).export({format:'jwk'});
 const mode=process.argv[2]||'tickets';
 if(mode==='prepare'){
  writeFileSync(process.argv[3],JSON.stringify(publicJwk),{mode:0o600});
  console.log('Clave pública preparada; clave privada conservada solo en el backend.');
  return;
 }
 let config=pair.get('voiceStream');
 if(mode==='activate'){
  phase='worker-health';
  const cfAccount=process.env.CLOUDFLARE_ACCOUNT_ID||'',token=process.env.CLOUDFLARE_API_TOKEN||'';
  if(!/^[a-f0-9]{32}$/.test(cfAccount)||!token)throw Error('cloud-account');
  const response=await fetch('https://api.cloudflare.com/client/v4/accounts/'+cfAccount+'/workers/subdomain',
   {headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(15000)});
  const data=await response.json();
  const sub=data.result?.subdomain;
  if(!response.ok||data.success!==true||!/^[a-z0-9-]{1,63}$/.test(sub||''))throw Error('subdomain');
  const address='https://nosotros-voice.'+sub+'.workers.dev';
  const check=await fetch(address+'/health',{signal:AbortSignal.timeout(15000)});
  if(!check.ok||(await check.json()).service!=='nosotros-voice')throw Error('health');
  config={enabled:true,protocol:1,url:address.replace('https:','wss:')+'/voice',
   room:hash('nosotros:'+pair.id),key:config?.key||randomBytes(32).toString('base64')};
  if(Buffer.from(config.key,'base64').length!==32)throw Error('audio-key');
  await pair.ref.update({voiceStream:config});
 }else if(mode!=='tickets')throw Error('mode');
 if(config?.enabled!==true||typeof config.room!=='string'){
  console.log('Canal privado todavía sin activar. No se cambió ninguna versión.');
  return;
 }
 phase='renew-tickets';
 const now=Math.floor(Date.now()/1000),exp=now+86400;
 for(const uid of ids){
  const role=uid===pair.get('admin')?'owner':'partner';
  const header=Buffer.from(JSON.stringify({alg:'RS256',typ:'JWT'})).toString('base64url');
  const payload=Buffer.from(JSON.stringify({iss:'nosotros-voice',aud:'nosotros-voice-v1',sub:hash(uid),
   room:config.room,role,iat:now,exp})).toString('base64url');
  const body=header+'.'+payload;
  const jwt=body+'.'+sign('RSA-SHA256',Buffer.from(body),privateKey).toString('base64url');
  await pair.ref.collection('voiceTickets').doc(uid).set({token:jwt,role,room:config.room,
   expiresAt:exp*1000,updatedAt:FieldValue.serverTimestamp()});
 }
 console.log('Sesiones privadas renovadas para las dos cuentas. No se liberó una actualización.');
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudo preparar el canal privado; fase: '+phase+'. Sin datos privados en los logs.');process.exit(1)});
