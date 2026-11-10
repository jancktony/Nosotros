const { before, after, beforeEach, test } = require('node:test');
const { readFileSync } = require('node:fs');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc, writeBatch } = require('firebase/firestore');
const { ref, uploadBytes, getBytes } = require('firebase/storage');
let env;
const alice = () => env.authenticatedContext('alice', { email: 'jancktoni@gmail.com', email_verified: true });
const bob = () => env.authenticatedContext('bob', { email: 'kelinherrera130323@gmail.com', email_verified: true });
const stranger = () => env.authenticatedContext('mallory', { email: 'other@example.com', email_verified: true });
const p = (ctx, path = 'pairs/p') => doc(ctx.firestore(), path);
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-nosotros', firestore: { rules: readFileSync('firestore.rules', 'utf8') }, storage: { rules: readFileSync('storage.rules', 'utf8') } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(p(ctx), { members: ['alice', 'bob'], admin: 'alice', code: '123456', createdAt: Date.now() });
    await setDoc(p(ctx, 'pairs/p/members/alice'), { lat: 1, lng: 2, pausedUntil: 0 });
    await setDoc(p(ctx, 'pairs/p/members/bob'), { lat: 3, lng: 4, pausedUntil: 0 });
    await setDoc(p(ctx, 'pairs/p/photos/f'), { uid: 'bob', caption: 'privado' });
    await setDoc(p(ctx, 'pairs/p/photos/f/full/img'), { img: 'privado' });
    await setDoc(p(ctx, 'codes/123456'), { pairId: 'p', owner: 'alice', expiresAt: Date.now() + 3600000 });
  });
});
test('solo los dos correos verificados pueden leer; webUids antiguos no dan acceso', async () => {
  await assertSucceeds(getDoc(p(alice())));
  await assertSucceeds(getDoc(p(bob())));
  await assertFails(getDoc(p(stranger())));
  await assertFails(getDoc(p(env.unauthenticatedContext())));
  await assertFails(getDoc(p(env.authenticatedContext('alice', { email: 'jancktoni@gmail.com', email_verified: false }))));
  await env.withSecurityRulesDisabled(ctx => updateDoc(p(ctx), { webUids: ['mallory'] }));
  await assertFails(getDoc(p(stranger())));
});
test('la pareja no puede elevar permisos, añadir navegadores, cambiar al otro o sustituir el APK', async () => {
  for (const fields of [{ admin: 'bob' }, { members: ['bob'] }, { webUids: ['bob'] }, { relUrl: 'https://evil.example/a.apk' }]) {
    await assertFails(updateDoc(p(bob()), fields));
  }
  for (const fields of [{ lat: 99 }, { pausedUntil: 0 }, { name: 'suplantado' }]) {
    await assertFails(updateDoc(p(bob(), 'pairs/p/members/alice'), fields));
  }
  await assertFails(setDoc(p(bob(), 'releases/9'), { version: '9', apkPath: 'evil' }));
});
test('cada uno escribe su posición y puede solicitar cinco minutos en vivo al otro', async () => {
  await assertSucceeds(updateDoc(p(bob(), 'pairs/p/members/bob'), { lat: 5, pausedUntil: Date.now() + 10000 }));
  await assertSucceeds(updateDoc(p(bob(), 'pairs/p/members/alice'), { liveRequestedUntil: Date.now() + 240000 }));
  await assertFails(updateDoc(p(bob(), 'pairs/p/members/alice'), { liveRequestedUntil: Date.now() + 3600000 }));
});
test('códigos sin listado, sobrescritura ni borrado por terceros', async () => {
  await assertFails(getDocs(collection(alice().firestore(), 'codes')));
  await assertFails(getDoc(p(stranger(), 'codes/123456')));
  await assertFails(deleteDoc(p(stranger(), 'codes/123456')));
  await assertFails(setDoc(p(bob(), 'codes/123456'), { pairId: 'p', owner: 'bob', expiresAt: Date.now() + 1000 }));
});
test('entrar exige código vigente; no permite cambiar admin ni superar dos miembros', async () => {
  await env.withSecurityRulesDisabled(ctx => updateDoc(p(ctx), { members: ['alice'] }));
  await assertFails(updateDoc(p(bob()), { members: ['alice', 'bob'], joinCode: '000000' }));
  await assertFails(updateDoc(p(bob()), { members: ['alice', 'bob'], joinCode: '123456', admin: 'bob' }));
  await env.withSecurityRulesDisabled(ctx => updateDoc(p(ctx, 'codes/123456'), { expiresAt: Date.now() - 1000 }));
  await assertFails(updateDoc(p(bob()), { members: ['alice', 'bob'], joinCode: '123456' }));
  await env.withSecurityRulesDisabled(ctx => updateDoc(p(ctx, 'codes/123456'), { expiresAt: Date.now() + 3600000 }));
  await assertSucceeds(updateDoc(p(bob()), { members: ['alice', 'bob'], joinCode: '123456' }));
  await assertFails(updateDoc(p(alice()), { members: ['alice', 'bob', 'mallory'] }));
});
test('admin puede liberar y transferir; no puede añadir directamente otro UID', async () => {
  await assertSucceeds(updateDoc(p(alice()), { relVersion: '1.7', relUrl: 'gs://bucket/releases/1.7/app.apk' }));
  await assertFails(updateDoc(p(alice()), { members: ['alice', 'mallory'] }));
  await assertSucceeds(updateDoc(p(alice()), { admin: 'bob' }));
  await assertFails(updateDoc(p(alice()), { admin: 'alice' }));
});
test('foto y original se crean juntos; solo propietario/admin borran', async () => {
  const ctx = alice();
  const batch = writeBatch(ctx.firestore());
  batch.set(p(ctx, 'pairs/p/photos/new'), { uid: 'alice' });
  batch.set(p(ctx, 'pairs/p/photos/new/full/img'), { img: 'datos' });
  await assertSucceeds(batch.commit());
  await assertFails(deleteDoc(p(bob(), 'pairs/p/photos/new')));
  await assertFails(setDoc(p(bob(), 'pairs/p/events/fake'), { uid: 'alice', type: 'resume' }));
  await assertFails(setDoc(p(bob(), 'pairs/p/photos/new/full/other'), { img: 'otro' }));
  await assertSucceeds(deleteDoc(p(alice(), 'pairs/p/photos/f/full/img')));
  await assertSucceeds(deleteDoc(p(alice(), 'pairs/p/photos/f')));
});
test('Storage: APK privado para cuentas verificadas; nadie publica desde el cliente', async () => {
  const path = 'releases/1.7/Nosotros.apk';
  await env.withSecurityRulesDisabled(ctx => uploadBytes(ref(ctx.storage(), path), new Uint8Array([1, 2, 3])));
  await assertSucceeds(getBytes(ref(alice().storage(), path)));
  await assertSucceeds(getBytes(ref(bob().storage(), path)));
  await assertFails(getBytes(ref(stranger().storage(), path)));
  await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), path)));
  await assertFails(uploadBytes(ref(alice().storage(), path), new Uint8Array([4])));
});
