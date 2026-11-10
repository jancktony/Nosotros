// One-time migration: pin the partner to their installed version, never release a candidate.
const {initializeApp, cert} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {readFileSync} = require('node:fs');
async function main() {
  const account = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
  if (account.project_id !== 'nosotros-1e43a') throw Error('Proyecto incorrecto');
  const app = initializeApp({credential:cert(account)});
  const source = readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt','utf8');
  const configured = source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1] || '';
  const emails = [...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(m=>m[1]);
  if (emails.length !== 2) throw Error('Lista incorrecta');
  const users = await Promise.all(emails.map(e=>getAuth(app).getUserByEmail(e)));
  const uids = users.map(u=>u.uid), db = getFirestore(app);
  const result = await db.collection('pairs').where('members','array-contains',uids[0]).get();
  const pairs = result.docs.filter(d=>d.data().members?.length===2 && d.data().members.every(u=>uids.includes(u)));
  if (pairs.length!==1) throw Error('Pareja ambigua');
  await db.runTransaction(async tx=> {
    const ref=pairs[0].ref, snap=await tx.get(ref), data=snap.data();
    if (data.members.length!==2 || !data.members.every(u=>uids.includes(u))) throw Error('La pareja cambió');
    const admin=data.admin || data.members[0];
    if (!data.members.includes(admin)) throw Error('Administrador inválido');
    const partner=data.members.find(u=>u!==admin);
    const member=await tx.get(ref.collection('members').doc(partner));
    const installed=member.get('appVersion');
    const version=typeof installed==='string' && /^\d+\.\d+(\.\d+)?$/.test(installed) ? installed : '1.8.1';
    tx.update(ref,{admin,relVersion:version,
      relUrl:`https://github.com/jancktony/Nosotros/releases/download/v${version}/Nosotros-v${version}.apk`,
      relNotes:'Solo el administrador puede liberar una versión nueva para este teléfono.'});
    console.log(`Pareja fijada en ${version}. Ninguna versión de prueba fue liberada por este proceso.`);
  });
}
main().catch(()=>{console.error('No se pudo fijar la versión de la pareja. No se muestran datos privados.');process.exitCode=1});
