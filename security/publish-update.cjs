// Solo se ejecuta en CI con secretos de GitHub; no guardar esta credencial en la app.
const { initializeApp, cert } = require('firebase-admin/app');
const { getStorage } = require('firebase-admin/storage');
const { getFirestore } = require('firebase-admin/firestore');
const { readFileSync } = require('node:fs');
const { createHash } = require('node:crypto');
async function main() {
  const account = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
  const config = JSON.parse(readFileSync('app/google-services.json', 'utf8'));
  const bucket = process.env.FIREBASE_STORAGE_BUCKET || config.project_info.storage_bucket;
  const version = readFileSync('app/build.gradle.kts', 'utf8').match(/versionName = "([^"]+)"/)[1];
  if (!account.private_key || !bucket || account.project_id !== config.project_info.project_id) {
    throw new Error('Configura FIREBASE_SERVICE_ACCOUNT y el bucket del mismo proyecto Firebase.');
  }
  const app = initializeApp({ credential: cert(account), storageBucket: bucket });
  const [bucketExists] = await getStorage(app).bucket().exists();
  if (!bucketExists) {
    console.log('::warning::El bucket de actualizaciones privadas no existe. Se conserva el canal de GitHub; no se crea almacenamiento ni se habilita facturación.');
    return;
  }
  const path = `releases/${version}/Nosotros.apk`;
  const apk = 'app/build/outputs/apk/release/app-release.apk';
  const sha256 = createHash('sha256').update(readFileSync(apk)).digest('hex');
  // No ACL pública ni firebaseStorageDownloadTokens.
  await getStorage(app).bucket().upload(apk, {
    destination: path, resumable: false,
    preconditionOpts: { ifGenerationMatch: 0 },
    metadata: { contentType: 'application/vnd.android.package-archive', cacheControl: 'private, no-store' }
  }).catch(async e => {
    // Reintento idempotente después de un fallo al publicar el manifiesto.
    if (e.code !== 412) throw e;
    const [existing] = await getStorage(app).bucket().file(path).download();
    if (createHash('sha256').update(existing).digest('hex') !== sha256) throw new Error('La versión ya contiene otro APK; incrementa la versión.');
  });
  await getFirestore(app).collection('releases').doc(version).set({
    version, apkPath: `gs://${bucket}/${path}`, sha256,
    publishedAt: Date.now(), date: new Date().toISOString().slice(0, 10),
    notes: process.env.RELEASE_NOTES || 'Mejoras y correcciones.'
  });
  console.log(`Actualización privada ${version} publicada.`);
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
