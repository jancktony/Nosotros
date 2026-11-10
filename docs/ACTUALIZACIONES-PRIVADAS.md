# Actualizaciones privadas: primero prueba el propietario, después libera

## Regla de entrega

GitHub Actions compila y firma una **candidata**. La guarda como borrador de
GitHub y en el canal privado de Firestore. El propietario administrador puede
instalarla y probarla. **Solo al pulsar Liberar en su panel**, la pareja recibe
esa versión. Ni un commit, ni crear un APK, ni publicar el manifiesto aprueban
una versión para la pareja. No hay workflow que libere automáticamente novedades.
Si falta una aprobación, la app no interpreta eso como «instalar la última».

La instalación de una versión ya aprobada puede ejecutarse automáticamente según
Wi-Fi y preferencias del teléfono. Android aún puede pedir confirmación. Los
controles de funciones son independientes: se pueden probar solo en la cuenta
administradora, apagar o liberar para ambos.

## Sin Storage ni Blaze

Para estas dos cuentas se reutiliza Firestore en su plan sin facturación. El APK
se divide en documentos de 512 KiB (cada documento queda por debajo del límite de
1 MiB). Se comprueba SHA-256 antes de exponer el manifiesto y después de descargar,
y Android comprueba paquete, certificado de firma y código de versión antes de
instalar. No hay token de GitHub ni credencial administrativa en la aplicación.

- Máximo 100 MiB por APK y cuatro versiones guardadas.
- Se conservan versiones aprobadas/instaladas y se eliminan candidatas antiguas.
- Si la cuota se agota, falla la publicación/descarga; no se habilita facturación.
- Los fragmentos solo los lee el administrador o la pareja cuando esa versión
  está aprobada. No se exponen enlaces públicos ni tokens de descarga.
- La lectura del APK usa REST con el token normal de Firebase del usuario para
  no llenar la caché local de Firestore. El token solo va al host oficial.

Es una solución acotada para dos personas, no un distribuidor público de APK.
La cuota gratuita compartida con ubicación/fotos/voz incluye 1 GiB almacenado,
50 000 lecturas diarias, 20 000 escrituras diarias y 10 GiB de salida al mes.
No se promete uso ilimitado. GitHub Actions tiene su propia cuota de minutos
para repositorios privados. No se habilita gasto adicional automáticamente.

Fuente: https://firebase.google.com/docs/firestore/quotas

## Migración única antes de privatizar

Las versiones 1.8/1.8.1 ya tienen un canal de GitHub. La transición a 1.9.1
se entrega **como actualización dentro de la app**, sin desinstalar ni descargar
manualmente otro APK. El propietario la recibe primero desde el canal existente.
La pareja conserva su versión fijada y solo recibe 1.9.1 cuando el propietario
pulsa **Liberar** en su panel.

Para esta única transición, el APK 1.9.1 se publica temporalmente en el GitHub
actual, que aún es público. El teléfono antiguo busca la versión expresamente
aprobada en ese catálogo y usa su enlace de GitHub; el hecho de que el panel nuevo
guarde también la ruta privada no impide esa selección. Las candidatas futuras
siguen siendo borradores y la app 1.9.1 solo descarga desde Firestore.

No se reinstala desde cero, no se borran datos ni se cambia el certificado. Android
puede pedir confirmar la instalación habitual de una actualización.

Orden:
1. Pruebas de reglas, compilación e integridad aprobadas.
2. Mantener fijada la versión de la pareja, sin aprobar el puente por CI.
3. Habilitar 1.9.1 en el canal GitHub existente para que el propietario actualice
   desde su app y realice sus pruebas.
4. Solo cuando el propietario pulse Liberar: la app de la pareja descarga esa
   misma versión desde el canal existente.
5. Confirmar ambos teléfonos con 1.9.1 o superior y verificar el canal privado.
6. Cambiar el repositorio a privado. Las actualizaciones posteriores usarán
   Firestore y seguirán exigiendo Liberar desde el panel del propietario.

No se debe privatizar antes del paso 5 porque los teléfonos antiguos perderían
su canal de actualización. Este entorno no tiene permiso de administración de
visibilidad del repositorio; el propietario puede cambiarla en Settings →
General → Danger Zone → Change repository visibility después de validar el canal.

## Estado de transición

La candidata 1.9 se retiró del catálogo público y quedó en borrador. El teléfono
de la pareja quedó fijado en 1.8 según su versión informada a Firestore. Esa
fijación no autoriza versiones nuevas. La voz permanece apagada por defecto.
No se ha validado aún la instalación del puente en los dos Samsung ni se ha
confirmado la privatización del repositorio.
