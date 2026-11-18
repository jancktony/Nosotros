// Local integration fixture only. Refuses to run against a real Firebase project.
import {Miniflare} from 'miniflare';
import {generateKeyPairSync,randomBytes,createHash,sign} from 'node:crypto';
import {initializeApp} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
if(!/^(127\.0\.0\.1|localhost):[0-9]+$/.test(process.env.FIRESTORE_EMULATOR_HOST||''))
 throw Error('Local emulator required');
const app=initializeApp({projectId:'demo-nosotros'}),db=getFirestore(app);
const keys=generateKeyPairSync('rsa',{modulusLength:2048}),publicJwk=keys.publicKey.export({format:'jwk'});
const hash=x=>createHash('sha256').update(x).digest('hex'),room=hash('fixture-pair');
const root=db.doc('pairs/socket-integration');
await root.set({members:['alice','bob'],admin:'alice',featureFlags:{walkie:'all',walkieRelay:'all'},
 voiceStream:{enabled:true,url:'ws://10.0.2.2:8787/voice',room,key:randomBytes(32).toString('base64')}});
for(const uid of ['alice','bob']){
 const now=Math.floor(Date.now()/1000),role=uid==='alice'?'owner':'partner';
 const header=Buffer.from(JSON.stringify({alg:'RS256',typ:'JWT'})).toString('base64url');
 const payload=Buffer.from(JSON.stringify({iss:'nosotros-voice',aud:'nosotros-voice-v1',sub:hash(uid),room,role,iat:now,exp:now+3600})).toString('base64url');
 const body=header+'.'+payload,token=body+'.'+sign('RSA-SHA256',Buffer.from(body),keys.privateKey).toString('base64url');
 await root.collection('voiceTickets').doc(uid).set({token,role,room,expiresAt:(now+3600)*1000});
}
const mf=new Miniflare({modules:true,scriptPath:new URL('./worker.mjs',import.meta.url).pathname,
 compatibilityDate:'2025-09-01',host:'127.0.0.1',port:8787,bindings:{SIGNING_JWK:JSON.stringify(publicJwk)},
 durableObjects:{ROOMS:{className:'VoiceRoom',useSQLite:true}}});
await mf.ready;
console.log('Local private voice fixture ready.');
process.on('SIGTERM',async()=>{await mf.dispose();process.exit(0)});
