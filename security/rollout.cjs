// Canal de transición: la app anterior solo entiende enlaces de GitHub Releases.
const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const { readFileSync } = require('node:fs');
async function main() {
  const version = readFileSync('app/build.gradle.kts', 'utf8').match(/versionName = "([^"]+)"/)[1];
  const app = initializeApp({ credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) });
  const emails = JSON.parse(process.env.PERSONAL_EMAILS || '[]');
  if (emails.length !== 2) throw new Error('PERSONAL_EMAILS debe contener los dos correos autorizados.');
  const users = await Promise.all(emails.map(email => getAuth(app).getUserByEmail(email)));
  const uids = users.map(u => u.uid);
  const db = getFirestore(app);
  const pairs = await db.collection('pairs').where('members', 'array-contains', uids[0]).get();
  const eligible = pairs.docs.filter(d => d.data().members.length === 2 && d.data().members.every(u => uids.includes(u)));
  if (eligible.length !== 1) throw new Error('No se encontró exactamente una pareja con ambas cuentas; no se modifica ninguna.');
  await eligible[0].ref.update({
    relVersion: version,
    relUrl: `https://github.com/jancktony/Nosotros/releases/download/v${version}/Nosotros-v${version}.apk`,
    relNotes: 'Actualización de seguridad y botón Buscar actualizaciones en Ajustes.'
  });
  console.log(`Actualización ${version} habilitada para los dos teléfonos. Android puede pedir confirmación.`);
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
