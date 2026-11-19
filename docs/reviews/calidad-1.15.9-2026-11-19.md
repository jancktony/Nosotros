# Revisión de calidad — Nosotros 1.15.9

Fecha: 2026-11-19. Rama: `feature/calidad-1.15.9`.
Revisión selectiva de los circuitos de voz, mapa, ajustes web, notificaciones y distribución. Las guías se aplican al código existente; no equivale a certificar todos los teléfonos o cada pantalla.

## Skills utilizadas
- `tech-audit` y `deuda-invisible`: seguir los circuitos reales y localizar divergencias entre lo prometido y el estado mostrado.
- `performance-audit` e `impeccable optimize`: conservar recursos y medir el número de reconstrucciones; preservar el diseño aprobado.
- `uiux-audit` y `web-design-guidelines`: continuidad de cámara, foco, borradores y datos de ubicación veraces.
- `cybersecurity`: reglas, sesión, consentimiento, datos no confiables y dependencias de herramientas.
- `spec`, `adr` y `review`: contrato, decisión técnica y comprobación de la entrega.
Las guías de maqueta, animaciones o refactor se mantienen disponibles; esta revisión no introduce otra dirección visual ni una refactorización general.

## Hallazgos y correcciones

| Severidad | Circuito | Evidencia inicial | Corrección |
| --- | --- | --- | --- |
| Alto | Voz | `WalkieConnection.attach`: el timer llamaba `end` a los 60 s y `ready` dependía de `audio != null`. | Canal preparado independiente del audio; liberar solo recursos inactivos. |
| Alto | Voz | `WalkieConnection.onMessage`: consumía directamente un buffer cuyo dueño es WebRTC. | Copiar el paquete dentro del callback y validar antes de usarlo en el hilo principal. |
| Alto | Voz | `WalkieConnection.talk`, `WalkieRelay.talk`, `WalkieService.startTalking`: fallos y cierres de una pulsación podían reentrar o cancelar otra. | Orden de callbacks y generaciones de micrófono; cambio a un transporte disponible conservando la pulsación. |
| Medio | Servicio/aviso | `WalkieService.onDestroy` llamaba `stopTalking`, que volvía a ejecutar `startForeground`. | Evitar reactivar el servicio destruido; restaurar el aviso de ubicación si su servicio continúa. |
| Medio | Mapa Android | `LiveMap.updateData`: cada velocidad añadía una imagen diferente sin retirar las antiguas. | Historial por estilo limitado a 32 imágenes, con todas las visibles protegidas. |
| Alto | Mapa web | `web/app.js:render` retiraba y creaba MapLibre por cada snapshot. | Tarjetas actualizadas por separado, mismo mapa, cámara y marcadores. |
| Medio | Ajustes web | `creator-brand` no estaba en `drafts` y el DOM sustituido perdía foco/selección. | Conservar borrador y selección durante actualizaciones. |
| Medio | Ubicación web | La etiqueta Última ubicación usaba `updatedAt`, también actualizado por batería o estado. | Usar `locationAt`; mostrar sin señal cuando no existe una fecha de GPS. |

## Medición y pruebas

- Web anterior: 1 de 5 pruebas de regresión pasa; 4 reproducen defectos. Web corregida: 5 de 5 pasan.
- Prueba de 50 snapshots: instancias de mapa 51 → 1, con la misma cámara y nodo DOM. Es una medición con SDK simulado y DOM real de JSDOM, no una medición de FPS del navegador.
- Reglas Firestore/Storage locales: 26 pruebas pasan, sin abrir accesos.
- Detector mecánico Impeccable para los archivos visuales cambiados: sin hallazgos. No sustituye una revisión renderizada en un Samsung.
- Prueba del historial de imágenes: 2.000 cambios de velocidad con imágenes activas protegidas; se verifica en la suite Android/JVM de CI.
- CI requerido antes del catálogo: compilación y unitarias, reglas, codec/gestos/WebRTC/relay y canal directo después de inactividad, mapa/animación/instalación.
- La compilación local de Android no llegó a comenzar: la descarga de Gradle fue rechazada en este entorno. La validación Android se realiza en GitHub Actions; no se considera ese intento local una compilación aprobada.

## Seguridad y datos

| Control | Resultado |
| --- | --- |
| Dos correos autorizados y verificados | Se conserva `Repo.authorizedSession` y `firestore.rules:personal`. |
| Funciones y consentimiento de cada teléfono | Se conservan checks de flags, permiso de micrófono y actividad visible; no se habilita recepción remotamente. |
| APK de prueba y liberación | Solo se crea candidata; chunks restringidos por reglas, aprobación y liberación continúan bajo el dueño. |
| Nombres no confiables en HTML | Regresión de escape pasa tanto antes como después de la optimización. |
| Audio | Paquetes acotados y validados; no se añade almacenamiento, grabación ni servicio externo. |
| Cuentas/permisos Firebase | Sin cambios en reglas de acceso; suite de denegaciones pasa. |

### Dependencias de herramientas: pendiente explícito
`npm audit` reporta 32 entradas agregadas (1 crítica, 11 altas, 20 moderadas) en la cadena de herramientas Node fijada en el proyecto. No son 32 exploits demostrados de la app Android. Destacan `firebase-tools@14.26.0 → tar@6.2.1` (GHSA-34x7-hfp2-rc4v y posteriores) y `firebase@11.10.0 → @grpc/grpc-js@1.9.16` (GHSA-m9gg-hp2v-232j). npm propone incluso degradar Firebase a 9.14; no se aplica esa sugerencia automáticamente. Actualizar las herramientas y sus dependencias requiere una migración validada por separado; no se declara corregida en esta candidata. No se procesan archivos de terceros durante estas pruebas ni se cambian las dependencias Android o el SDK CDN de la web.

## Cobertura y límites
La inspección de entrada comprende Auth/Repo/reglas, MainActivity/manifest, las rutas de pantallas, modelos/flags, voz, mapa y controles de distribución. Las correcciones se limitan a los hallazgos de la tabla; no se certifican historial, galería, widgets o geocercas completos mediante esta revisión selectiva. Su diseño aprobado se conserva.

El identificador compartido de aviso por ubicación y voz se mantiene para no añadir otro aviso fijo. Compartirlo por sí solo no demuestra un fallo de conexión; la corrección comprobable está en no reactivar voz durante su destrucción.

El servidor persistente de #20 continúa pendiente de alojamiento. Una red que bloquee la vía directa puede seguir necesitando otro transporte; no se promete llamada inmediata universal. El cambio de inactividad requiere ambos clientes actualizados. Las pruebas físicas entre ambos Samsung y redes Wi-Fi/datos quedan para el dueño.

## Veredicto
Implementación preparada para validación Android de CI; candidata bloqueada por sus workflows hasta que esa revisión apruebe. No aprobar automáticamente la prueba del dueño ni liberar a Kelin. Plan siguiente: terminar CI, verificar APK privado, actualizar web y registrar los resultados reales.
