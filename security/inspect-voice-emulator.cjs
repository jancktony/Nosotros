const {initializeApp}=require('firebase-admin/app'),{getFirestore}=require('firebase-admin/firestore');
if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8080')throw Error('emulator only');
const db=getFirestore(initializeApp({projectId:'demo-nosotros'}));
(async()=>{for(let i=0;i<3;i++){const docs=await db.collection('pairs/voice-integration-'+i+'/walkiePresence').get();console.log('DEMO presence round '+i+': '+JSON.stringify(docs.docs.map(d=>({uid:d.id,relay:d.get('relay'),at:d.get('updatedAt')?.toMillis()}))));}})().then(()=>process.exit(0));
