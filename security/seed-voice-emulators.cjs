// DEMO project only: no production credentials or messages.
const {initializeApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth'),{getFirestore}=require('firebase-admin/firestore');
async function main(){
 if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8080'||process.env.FIREBASE_AUTH_EMULATOR_HOST!=='127.0.0.1:9099')throw Error('emulators required');
 const app=initializeApp({projectId:'demo-nosotros'}),auth=getAuth(app),db=getFirestore(app);
 for(const [uid,email] of [['alice','jancktoni@gmail.com'],['bob','kelinherrera130323@gmail.com']]){
  await auth.createUser({uid,email,emailVerified:true,password:'demo-emulator-only-123'});
  await db.doc('pairs/voice-integration/members/'+uid).set({appVersion:'1.13.1'});
 }
 await db.doc('pairs/voice-integration').set({members:['alice','bob'],admin:'alice',featureFlags:{walkie:'all',walkieRelay:'all'}});
 console.log('Cuentas ficticias de emulador listas. Ningún mensaje enviado ni acceso a producción.');
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudieron preparar emuladores locales');process.exit(1)});
