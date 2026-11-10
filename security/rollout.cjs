// Canal de transición: la app anterior solo entiende enlaces de GitHub Releases.
const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const { readFileSync } = require('node:fs');
async function main() {
  const version = readFileSync('app/build.gradle.kts', 'utf8').match(/versionName = "([^"]+)"/)[1];
  const account = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
  if (account.project_id !== 'nosotros-1e43a' || !account.private_key) throw new Error('Credencial de proyecto incorrecta.');
  const app = initializeApp({ credential: cert(account) });
  const source = readFileSync('app/src/main/java/com/tony/nosotros/data/Repo.kt', 'utf8');
  const configured = source.match(/personalEmails = setOf\(([^)]+)\)/)?.[1] || '';
  const allowed = [...configured.matchAll(/"([^"\s]+@[^"\s]+)"/g)].map(m => m[1].toLowerCase());
  const emails = process.env.PERSONAL_EMAILS ? JSON.parse(process.env.PERSONAL_EMAILS) : allowed;
  if (allowed.length !== 2 || !Array.isArray(emails) || new Set(emails).size !== 2 ||
      !emails.every(e => typeof e === 'string' && allowed.includes(e.toLowerCase()))) throw new Error('Correos de liberación no autorizados.');
  if (emails.length !== 2) throw new Error('PERSONAL_EMAILS debe contener los dos correos autorizados.');
  const users = await Promise.all(emails.map(email => getAuth(app).getUserByEmail(email)));
  const uids = users.map(u => u.uid);
  const db = getFirestore(app);
  const pairs = await db.collection('pairs').where('members', 'array-contains', uids[0]).get();
  const eligible = pairs.docs.filter(d => d.data().members.length === 2 && d.data().members.every(u => uids.includes(u)));
  if (eligible.length !== 1) throw new Error('No se encontró exactamente una pareja con ambas cuentas; no se modifica ninguna.');
  const pair = eligible[0].data();
  if (pair.admin && !pair.members.includes(pair.admin)) throw new Error('Administrador de pareja inválido.');
  await eligible[0].ref.update({
    ...(!pair.admin ? {admin: pair.members[0]} : {}),
    relVersion: version,
    relUrl: `https://github.com/jancktony/Nosotros/releases/download/v${version}/Nosotros-v${version}.apk`,
    relNotes: 'Panel de funciones administradas, walkie-talkie y notas de voz. La voz empieza desactivada: el administrador decide cuándo liberarla.'
  });
  console.log(`Actualización ${version} habilitada para los dos teléfonos. Android puede pedir confirmación.`);
}
main().catch(() => { console.error('No se pudo liberar la versión para la pareja autorizada. No se muestran datos privados.'); process.exitCode = 1; });
