import {verifyTicket, usableAttachment} from './auth.mjs';
const json = (body, status=200) => new Response(JSON.stringify(body), {
  status, headers:{'Content-Type':'application/json','Cache-Control':'no-store'}
});
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return json({service:'nosotros-voice', protocol:1});
    if (url.pathname !== '/voice' || request.method !== 'GET') return new Response('Not found',{status:404});
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') return new Response('Upgrade required',{status:426});
    try {
      const authorization=request.headers.get('Authorization') || '';
      if (!authorization.startsWith('Bearer ')) return new Response('Unauthorized',{status:401});
      const claim=await verifyTicket(authorization.slice(7),env.SIGNING_JWK);
      const headers=new Headers({'Upgrade':'websocket','X-Voice-Sub':claim.sub,
        'X-Voice-Role':claim.role,'X-Voice-Room':claim.room,'X-Voice-Exp':String(claim.exp)});
      return env.ROOMS.get(env.ROOMS.idFromName(claim.room))
        .fetch(new Request('https://room.internal/voice',{headers}));
    } catch { return new Response('Unauthorized',{status:401}); }
  }
};
export class VoiceRoom {
  constructor(state, env) { this.state=state; this.env=env; }
  connections() {
    return this.state.getWebSockets().filter(ws=>ws.readyState===1 && usableAttachment(ws.deserializeAttachment()));
  }
  presence() {
    const sockets=this.connections();
    for (const ws of sockets) {
      const role=ws.deserializeAttachment().role;
      const ready=sockets.some(other=>other!==ws && other.deserializeAttachment().role!==role);
      try { ws.send(JSON.stringify({type:'ready',ready,now:Date.now()})); } catch {}
    }
  }
  async fetch(request) {
    const role=request.headers.get('X-Voice-Role'), sub=request.headers.get('X-Voice-Sub'),
      room=request.headers.get('X-Voice-Room'), exp=Number(request.headers.get('X-Voice-Exp'));
    if (!['owner','partner'].includes(role)||!/^[a-f0-9]{64}$/.test(sub||'')||
        !/^[a-f0-9]{64}$/.test(room||'')||!Number.isInteger(exp)||exp*1000<=Date.now())
      return new Response('Unauthorized',{status:401});
    // Each signed identity has one receiving connection; reconnect replaces its stale socket.
    for(const ws of this.connections()) {
      if(ws.deserializeAttachment().role===role) ws.close(1012,'Reconnected');
    }
    const [client,server]=Object.values(new WebSocketPair());
    this.state.acceptWebSocket(server);
    server.serializeAttachment({role,sub,room,exp,windowAt:0,count:0});
    this.presence();
    return new Response(null,{status:101,webSocket:client});
  }
  async webSocketMessage(ws, message) {
    const a=ws.deserializeAttachment();
    if(!usableAttachment(a)){ws.close(1008,'Expired');this.presence();return}
    const now=Date.now();
    if(now-a.windowAt>=1000){a.windowAt=now;a.count=0}
    if(++a.count>32){ws.close(1008,'Rate limit');this.presence();return}
    ws.serializeAttachment(a);
    if(typeof message==='string') {
      if(message.length>5000){ws.close(1009,'Too large');return}
      try {
        const control=JSON.parse(message);
        if(control.type==='sync'){ws.send(JSON.stringify({type:'time',now}));return}
        if(control.type==='reauth') {
          const claim=await verifyTicket(control.ticket,this.env.SIGNING_JWK);
          if(claim.role!==a.role||claim.sub!==a.sub||claim.room!==a.room)throw Error('identity');
          a.exp=claim.exp;ws.serializeAttachment(a);
          ws.send(JSON.stringify({type:'renewed',now}));return;
        }
      }catch{}
      ws.close(1008,'Invalid control');return;
    }
    if(!(message instanceof ArrayBuffer)||message.byteLength<48||message.byteLength>512){
      ws.close(1009,'Invalid packet');return;
    }
    // Opaque encrypted packets are forwarded live, never recorded or queued for offline playback.
    for(const peer of this.connections()) {
      if(peer!==ws && peer.deserializeAttachment().role!==a.role) {
        try { peer.send(message); } catch { peer.close(1011,'Connection lost'); }
      }
    }
  }
  async webSocketClose(ws, code) {
    if(code>=1000&&code<5000&&![1004,1005,1006,1015].includes(code))ws.close(code);
    else ws.close();
    this.presence();
  }
  async webSocketError(ws) { try{ws.close(1011,'Connection lost')}catch{}this.presence(); }
}
