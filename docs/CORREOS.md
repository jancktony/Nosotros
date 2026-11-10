# Correos de Nosotros

La app solicita español (`es`) a Firebase Authentication antes de enviar
verificación o recuperación. Se aplica desde 1.8.1; no cambia correos ya enviados.

## Estado y configuración en Firebase Console

Proyecto `nosotros-1e43a`:

1. Configuración del proyecto → General → Configuración pública:
   nombre público **Nosotros** (sustituye `project-428781876267`).
2. Authentication → Templates: idioma **Español**, remitente **Nosotros**
   en verificación y recuperación. Conservar dirección y enlaces de Firebase.
3. Asunto de verificación: **Confirma tu correo · Nosotros**.
4. Asunto de recuperación: **Recupera tu contraseña · Nosotros**.

Texto propuesto para recuperación, si la consola permite editar el cuerpo:

```html
<p>Hola:</p>
<p>Recibimos una solicitud para cambiar la contraseña de tu cuenta en <strong>Nosotros</strong>.</p>
<p><a href="%LINK%">Crear una nueva contraseña</a></p>
<p>Si no lo solicitaste, ignora este correo. Tu contraseña seguirá igual.</p>
<p>Con cariño,<br>Nosotros</p>
```

Firebase restringe la edición del cuerpo de algunos mensajes, incluida la
verificación. Usar su traducción oficial con nombre público, asunto y remitente
personalizados. Un diseño HTML totalmente propio requiere un servicio de envío
propio y enlaces generados en un servidor; no incluir credenciales SMTP ni
Firebase Admin en la app.

Comprobar con las dos cuentas autorizadas: remitente, asunto, cuerpo y página de
acción en español, y que el enlace permita verificar/restablecer correctamente.
No guardar enlaces reales de acción en el repositorio: contienen códigos de uso único.

El idioma `es` ya se aplicó al proyecto mediante la API administrativa el
10 de noviembre de 2026. El propietario indicó que cambió el nombre público a
Nosotros en la consola. Google rechaza actualmente la personalización de remitente,
asunto y diseño con `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`; no se da por aplicada.
No se enviaron correos de prueba. Para diseño completamente propio se necesita
un servidor que genere enlaces con Firebase Admin y un proveedor de envío:
https://firebase.google.com/docs/auth/admin/email-action-links

La configuración de consola no se aplica al publicar el APK. La credencial de
administración se usa exclusivamente mediante secretos de GitHub Actions.

Referencias:
- https://firebase.google.com/docs/auth/android/manage-users
- https://support.google.com/firebase/answer/7000714?hl=es
