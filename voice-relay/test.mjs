import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {Miniflare} from 'miniflare';
import {verifyTicket} from './auth.mjs';
const keys=generateKeyPairSync('rsa',{modulusLength:2048});
const jwk=JSON.stringify(keys.publicKey.export({format:'jwk'}));
function ticket(role,change={}) {
 const now=Math.floor(Date.now()/1000);
 const header=Buffer.from(JSON.stringify({alg:'RS256',typ:'JWT'})).toString('base64url');
 const payload=Buffer.from(JSON.stringify({iss:'nosotros-voice',aud:'nosotros-voice-v1',
  sub:(role==='owner'?'a':'b').repeat(64),room:'c'.repeat(64),role,iat:now,exp:now+3600,...change})).toString('base64url');
 const body=header+'.'+payload;
 return body+'.'+sign('RSA-SHA256',Buffer.from(body),keys.privateKey).toString('base64url');
}
test('signed tickets reject strangers, expiry, wrong audience and forged signatures',async()=>{
 assert.equal((await verifyTicket(ticket('owner'),jwk)).role,'owner');
 for(const bad of [ticket('stranger'),ticket('owner',{exp:1}),ticket('owner',{aud:'another-app'}),ticket('owner').slice(0,-20)+'bad'])
  await assert.rejects(verifyTicket(bad,jwk));
});
test('two authorized sockets deliver encrypted bytes, renew and report a disconnect',async()=>{
 const mf=new Miniflare({modules:true,scriptPath:new URL('./worker.mjs',import.meta.url).pathname,
  compatibilityDate:'2025-09-01',bindings:{SIGNING_JWK:jwk},
  durableObjects:{ROOMS:{className:'VoiceRoom',useSQLite:true}}});
 const sockets=[];
 async function connect(role) {
  const response=await mf.dispatchFetch('http://local/voice',{headers:{Upgrade:'websocket',Authorization:'Bearer '+ticket(role)}});
  assert.equal(response.status,101);const ws=response.webSocket;
  const queue=[],waiting=[];
  ws.addEventListener('message',e=>{const next=waiting.shift();if(next)next(e.data);else queue.push(e.data)});
  ws.accept();sockets.push(ws);
  async function next(){if(queue.length)return queue.shift();return Promise.race([
   new Promise(r=>waiting.push(r)),new Promise((_,reject)=>{const timer=setTimeout(()=>reject(Error('Message timeout')),5000);timer.unref()})])}
  return {ws,next,queue};
 }
 try {
  const denied=await mf.dispatchFetch('http://local/voice',{headers:{Upgrade:'websocket',Authorization:'Bearer wrong'}});
  assert.equal(denied.status,401);
  const a=await connect('owner');assert.equal(JSON.parse(await a.next()).ready,false);
  const b=await connect('partner');
  assert.equal(JSON.parse(await a.next()).ready,true);assert.equal(JSON.parse(await b.next()).ready,true);
  const frame=Uint8Array.from({length:64},(_,i)=>i);a.ws.send(frame);
  assert.deepEqual(new Uint8Array(await b.next()),frame);
  b.ws.send(JSON.stringify({type:'reauth',ticket:ticket('partner',{exp:Math.floor(Date.now()/1000)+7200})}));
  assert.equal(JSON.parse(await b.next()).type,'renewed');
  b.ws.send(frame);assert.deepEqual(new Uint8Array(await a.next()),frame);
  b.ws.close(1000);assert.equal(JSON.parse(await a.next()).ready,false);
 }finally{for(const ws of sockets)try{ws.close()}catch{}await mf.dispose()}
});
