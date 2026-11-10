// Runs only in trusted GitHub Actions. Never log credential/API response bodies.
const { cert } = require('firebase-admin/app');
const { readFileSync } = require('node:fs');
const PROJECT = 'nosotros-1e43a';
async function main() {
  let account;
  try { account = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || ''); }
  catch { throw new Error('El secreto debe contener el JSON completo de la cuenta de servicio.'); }
  if (account.project_id !== PROJECT || !account.private_key) throw new Error('La credencial no corresponde al proyecto esperado.');
  const { access_token: token } = await cert(account).getAccessToken();
  console.log(`::add-mask::${token}`);
  async function api(url, method = 'GET', body) {
    const response = await fetch(url, {method, headers: {Authorization: `Bearer ${token}`, 'Content-Type':'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body)});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = String(data?.error?.message || 'API_ERROR').replaceAll(token, '[REDACTED]')
        .replace(/https?:\/\/[^\s]+/g, '[URL]').replace(/AIza[A-Za-z0-9_-]+/g, '[REDACTED]').replace(/[\r\n]/g, ' ').slice(0, 350);
      console.log(`Detalle HTTP ${response.status}: ${detail}`);
    }
    return {ok: response.ok, status: response.status, data};
  }
  let failures = 0;
  const authUrl = `https://identitytoolkit.googleapis.com/admin/v2/projects/${PROJECT}/config`;
  const auth = await api(authUrl);
  console.log(`Acceso a configuración de correos: HTTP ${auth.status}`);
  if (auth.ok && process.env.ONLY_RULES !== '1') {
    console.log('Campos de configuración: ' + Object.keys(auth.data).join(','));
    const update = async (mask, body) => api(authUrl + '?updateMask=' + encodeURIComponent(mask), 'PATCH', body);
    const locale = await update('notification.defaultLocale', {notification:{defaultLocale:'es'}});
    console.log(`Idioma predeterminado español: HTTP ${locale.status}`);
    if (!locale.ok) failures++;
    for (const [kind, subject, text, action] of [
      ['verifyEmailTemplate', 'Confirma tu correo · Nosotros', 'Confirma tu correo para empezar a compartir momentos en Nosotros.', 'Confirmar mi correo'],
      ['resetPasswordTemplate', 'Recupera tu contraseña · Nosotros', 'Recibimos una solicitud para cambiar la contraseña de tu cuenta en Nosotros.', 'Crear una nueva contraseña']
    ]) {
      const prefix = `notification.sendEmail.${kind}`;
      const previous = auth.data?.notification?.sendEmail?.[kind] || {};
      const preserved = Object.fromEntries(Object.entries(previous).filter(([key]) => key !== 'customized'));
      const branded = {...preserved, senderLocalPart:preserved.senderLocalPart || 'noreply', senderDisplayName:'Nosotros', subject};
      const branding = await update(prefix, {notification:{sendEmail:{[kind]:branded}}});
      console.log(`Remitente y asunto ${kind}: HTTP ${branding.status}`);
      if (!branding.ok) failures++;
      const body = `<div style="background:#fff5f7;padding:28px;font-family:Arial,sans-serif;color:#32232c"><div style="max-width:480px;margin:auto;background:#fff;padding:28px;border-radius:18px"><h1 style="color:#be426d;font-size:25px">Nosotros</h1><p>Hola:</p><p>${text}</p><p style="margin:28px 0"><a href="%LINK%" style="background:#be426d;color:#fff;padding:14px 20px;border-radius:10px;text-decoration:none;display:inline-block">${action}</a></p><p>Si no lo solicitaste, ignora este correo.</p><p>Con cariño,<br>Nosotros</p></div></div>`;
      const content = await update(prefix, {notification:{sendEmail:{[kind]:{...branded,body,bodyFormat:'HTML'}}}});
      console.log(`Diseño en español ${kind}: HTTP ${content.status}`);
      if (!content.ok) {
        const code = content.data?.error?.message || '';
        console.log(code.includes('EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED') ? 'Firebase restringe la edición del cuerpo de esta plantilla.' : 'El servidor rechazó el cuerpo personalizado.');
        failures++;
      }
    }
    const check = await api(authUrl);
    const email = check.data?.notification?.sendEmail || {};
    console.log('Comprobación de correos: ' + JSON.stringify({locale:check.data?.notification?.defaultLocale,
      verify:{sender:email.verifyEmailTemplate?.senderDisplayName,subject:email.verifyEmailTemplate?.subject},
      reset:{sender:email.resetPasswordTemplate?.senderDisplayName,subject:email.resetPasswordTemplate?.subject}}));
  } else if (!auth.ok) failures++;
  if (process.env.APPLY_RULES === '0') {
    if (failures) throw new Error('Correos parcialmente configurados');
    return;
  }
  const rulesUrl = `https://firebaserules.googleapis.com/v1/projects/${PROJECT}`;
  const current = await api(`${rulesUrl}/releases/cloud.firestore`);
  console.log(`Acceso a reglas Firestore: HTTP ${current.status}`);
  if (current.ok) {
    const rules = readFileSync('firestore.rules', 'utf8');
    const created = await api(`${rulesUrl}/rulesets`, 'POST', {source:{files:[{name:'firestore.rules',content:rules}]}});
    console.log(`Validación de reglas Firestore: HTTP ${created.status}`);
    if (created.ok) {
      const deployed = await api(`${rulesUrl}/releases/cloud.firestore`, 'PATCH', {
        release:{name:`projects/${PROJECT}/releases/cloud.firestore`,rulesetName:created.data.name},updateMask:'ruleset_name'
      });
      console.log(`Publicación de reglas Firestore: HTTP ${deployed.status}`);
      if (!deployed.ok) failures++;
      else {
        const check = await api(`${rulesUrl}/releases/cloud.firestore`);
        if (!check.ok || check.data.rulesetName !== created.data.name) throw new Error('No se pudo confirmar la publicación de reglas.');
        console.log(`Reglas activas confirmadas. Revisión anterior: ${current.data.rulesetName}`);
      }
    } else failures++;
  } else failures++;
  if (failures) throw new Error(`Quedan ${failures} operaciones pendientes; revisar permisos de la cuenta de servicio o restricciones indicadas.`);
}
main().catch(() => { console.error('Configuración incompleta. Revisa los estados HTTP anteriores; no se muestran credenciales ni respuestas privadas.'); process.exitCode=1; });
