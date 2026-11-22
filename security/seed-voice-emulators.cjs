// Isolated local emulator fixtures; no production access.
const {initializeApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth'),{getFirestore}=require('firebase-admin/firestore');
async function main(){
 if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8080'||process.env.FIREBASE_AUTH_EMULATOR_HOST!=='127.0.0.1:9099')throw Error('emulators required');
 const app=initializeApp({projectId:'demo-nosotros'}),auth=getAuth(app),db=getFirestore(app);
 for(const [uid,email] of [['alice','jancktoni@gmail.com'],['bob','kelinherrera130323@gmail.com']])
  await auth.createUser({uid,email,emailVerified:true,password:'demo-emulator-only-123'});
 for(let round=0;round<3;round++){
  const pair=db.doc('pairs/voice-integration-'+round);
  await pair.set({members:['alice','bob'],admin:'alice',featureFlags:{walkie:'all',walkieRelay:'all'}});
  for(const uid of ['alice','bob'])await pair.collection('members').doc(uid).set({appVersion:'1.14.1'});
 }
 await db.doc('pairs/notes-integration').set({members:['alice','bob'],admin:'alice',featureFlags:{voiceNotes:'all'}});
 console.log('Tres parejas ficticias listas en emuladores locales.');
}
main().then(()=>process.exit(0)).catch(()=>{console.error('No se pudieron preparar emuladores locales');process.exit(1)});
