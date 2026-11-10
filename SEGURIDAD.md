# Uso personal y migración sin perder actualizaciones

## Cambios

Solo los dos correos autorizados y verificados pueden acceder a Firestore y Storage.
Las reglas protegen administrador, posiciones y pausas; los códigos no se pueden listar.
El panel web usa correo y contraseña, sin usuarios anónimos ni códigos de navegador.
Ajustes incluye «Buscar actualizaciones», con descarga, comprobación de firma/paquete/versión y reintento.
Las actualizaciones automáticas continúan con Wi-Fi. Android conserva la decisión final de instalar.

## Orden para activar

1. Publicar primero la versión 1.7 por el canal actual de GitHub. Conservar la misma clave de firma y los secretos existentes GOOGLE_SERVICES_JSON, KEYSTORE_B64 y KS_PASS. No privatizar todavía el repositorio: los APK anteriores dependen de sus Releases públicos.
2. Verificar los dos correos en Firebase Authentication. La app nueva envía un enlace al iniciar sesión si falta verificar. No eliminar ni recrear las cuentas: se deben conservar sus UID para mantener los datos y la pareja.
3. Crear un bucket privado de Firebase Storage en el mismo proyecto. Firebase puede exigir el plan Blaze: revisar el plan y el coste antes de habilitarlo. Evitar ACL públicas y enlaces con firebaseStorageDownloadTokens. La configuración google-services.json debe incluir project_info.storage_bucket; alternativamente definir FIREBASE_STORAGE_BUCKET en las variables de GitHub Actions.
4. Configurar el secreto FIREBASE_SERVICE_ACCOUNT con la credencial de una cuenta de servicio del proyecto, limitada a publicar objetos en ese bucket y manifiestos en Firestore (Storage Object Admin limitado al bucket y Cloud Datastore User). No enviar la clave por chat ni añadirla al repositorio/APK. GitHub Actions publicará automáticamente las siguientes versiones en Storage y sus manifiestos en /releases. Para publicar 1.7 en Storage tras el primer lanzamiento, ejecutar `node security/publish-update.cjs` con ese secreto en un entorno seguro, o volver a ejecutar el paso del workflow antes de crear su tag.
5. Para habilitar 1.7 en ambos teléfonos aunque exista una versión fijada para la pareja, configurar PERSONAL_EMAILS como un array JSON con los dos correos autorizados y ejecutar el workflow «Habilitar actualización en los dos teléfonos». La cuenta de servicio necesita además Firebase Authentication Viewer para resolver sus UID. El script exige exactamente una pareja con las dos cuentas; no modifica otras parejas. Los teléfonos antiguos consultan cada 30 minutos con Wi-Fi si la actualización automática está activada; si ya fallaron al intentar esa versión, su app antigua puede exigir abrirla y aceptar el aviso. No se puede forzar a distancia una instalación que Android no autorice.
6. Tras recibir 1.7, desplegar firestore.rules y storage.rules con `npx firebase deploy --only firestore:rules,storage --project ID_REAL`. El despliegue usa una cuenta administrativa autorizada; esta tarea no incluye credenciales de Firebase. Las reglas nuevas bloquean las sesiones sin verificar y el panel web antiguo: publicar también el nuevo web/index.html y mantener web/config.js fuera de Git.
7. Comprobar que los dos teléfonos descargan desde Storage y después cambiar GitHub a Private desde Settings → Danger Zone. El fallback temporal de GitHub solo admite el repositorio original y exige firma del APK idéntica; deja de funcionar al privatizar el repositorio. No se guarda un token de GitHub en la app.

No se ejecuta el despliegue de reglas automáticamente al hacer merge: aplicarlas antes de verificar las cuentas interrumpiría el servicio actual. El secreto de publicación no permite a clientes subir APK; los clientes solo pueden leerlos con sus sesiones verificadas.

## Validación

`npm ci && npm test` ejecuta intentos permitidos y denegados contra emuladores Firestore y Storage, sin tocar producción.
`./gradlew testDebugUnitTest assembleDebug` valida Android sin claves privadas. La versión release requiere el keystore original.

El código, los APK y las copias ya descargadas mientras el repositorio era público no pueden retirarse de equipos ajenos. La confidencialidad de ubicaciones y fotos depende de desplegar las reglas en Firebase, no de ocultar la configuración del cliente.
