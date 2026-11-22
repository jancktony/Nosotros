# Revisión de la experiencia completa 1.16.0

Fecha: 2026-11-21. Revisión automatizada por Codex; guía review aplicada al contrato [experiencia-completa-1.16](../specs/experiencia-completa-1.16.md).

## Alcance integrado

- Se integraron los temas en Kotlin/Compose y la web real, no en una maqueta. Rosa original es el predeterminado y se conservan elecciones anteriores; Rojo y negro, Negro y rosa y Rosa completo cubren controles, Más, Juntos y widgets. Las preferencias son por dispositivo/navegador.
- La ficha del mapa incorpora búsqueda y accesos a lugares reales; mantiene el panel ocultable por gestos y el mapa independiente del ancho de navegación. Más usa tarjetas adaptables y el sistema de cristal estático evita añadir desenfoque costoso al mapa 3D.
- Las notas fallidas válidas se guardan en archivos privados separados por pareja/cuenta, con escritura temporal y reintento explícito. Un identificador estable evita duplicados; la confirmación del servidor reconcilia el borrador. Solo el receptor puede escribir el acuse. Se distingue recepción de reproducción: no se afirma que la persona escuchó.
- El destino y ETA inicial se comparten desde navegación por acción voluntaria. El emisor puede eliminar su viaje. La llegada exige mediciones precisas dentro de 40 m durante 20 s; nunca se confirma por ETA. Caduca como máximo a las 24 h y el emisor elimina registros vencidos al abrir la app con conexión, mediante transacción para no borrar un viaje nuevo.
- El aviso de batería es interno y requiere consentimiento propio; no añade notificaciones del sistema. La galería conserva fechas y añade favoritos locales que no editan fotos de la pareja.
- Administración añade los controles sharedTrips, batteryAlerts y photoFavorites, inicialmente Solo dueño, compatibles con las reglas. Conserva las observaciones, prueba instalada, aprobación y liberación manual existentes.
- La navegación y los widgets utilizan la hora real del GPS, no la actividad o batería del teléfono. La distancia del widget caduca si cualquiera de sus dos mediciones es antigua.

## Corrección adicional detectada por la prueba de recuperación

La limpieza de WalkieRelay trataba una marca de tiempo del servidor todavía pendiente como cero y podía borrar un fragmento nuevo. Ahora consulta el servidor y solo limpia documentos confirmados, no pendientes, con fecha válida y más de 15 segundos. VoicePacketExpiryTest cubre fecha ausente, escritura pendiente, caché, futuro y límite de caducidad.

Las reglas permiten que los dos miembros eliminen de forma idempotente fragmentos/llamadas ya borrados y candidatos huérfanos, sin permitir lectura ni acceso a terceros. La regresión de reglas comprueba ambos miembros y rechaza a un extraño. La prueba del relay conserva sus tres rondas y la desconexión/reconexión; la lista observada entre hilos es CopyOnWriteArrayList y los fallos incluyen estado y número de fragmentos, sin audio ni datos reales.

## Validación

Revisión probada y publicada: `0ba6d0c0c8ddb0f4fbf6699a0077eccb65c8f1de`, integrada mediante [PR #25](https://github.com/jancktony/Nosotros/pull/25), merge `d959f04b27ae852665f66e6f0db060be03659089`.

- [Seguridad y Android](https://github.com/jancktony/Nosotros/actions/runs/44975344270): aprobado, incluidas 31 pruebas de reglas y 8 regresiones web.
- [Voz](https://github.com/jancktony/Nosotros/actions/runs/44975341454): 10 pruebas aprobadas; conserva tres rondas con pérdida de red y recuperación, además del flujo de notas.
- [Mapa, temas e instalación](https://github.com/jancktony/Nosotros/actions/runs/44975341445): 10 pruebas aprobadas.
- [Web privada](https://github.com/jancktony/Nosotros/actions/runs/44975341557): cuatro temas en 320, 390, 430, 1024 y 1440 px; publicada y verificada.
- [APK firmado y publicación privada](https://github.com/jancktony/Nosotros/actions/runs/44975341381): aprobado. Versión 1.16.0, código 33, 74.312.291 bytes en 142 fragmentos, leídos y verificados antes de ready=true. SHA-256: `57b869c5d03c8b329ce0e2ee8f8a99557a62791f47aa35b98af3df514a7e7ecd`.

 Compilación Android, pruebas unitarias y reglas no añaden dependencias de pago ni permisos nuevos. Se añadieron regresiones de paletas, widgets, permanencia de llegada, permisos de viajes, acuses, favoritos y conservación de cámara/borradores de la web. Pruebas instrumentales verifican la selección de temas por píxeles, navegación de Más, aislamiento de borradores y el flujo de una nota sin conexión, reintento sin duplicar y acuse del otro usuario.

La publicación exige voz, mapa y seguridad de la misma revisión. Las pruebas usan datos ficticios y emuladores, nunca ubicaciones o audio reales de la pareja.

## Límites explícitos

No se ha probado físicamente esta candidata entre los dos teléfonos. La reconexión directa y el respaldo de notas no sustituyen un retransmisor permanente para redes que bloqueen WebRTC. Ese servidor y una IPA firmada siguen pendientes de infraestructura/herramientas de Apple. Street View se pospuso; no se añaden mapas descargados ni navegación nueva completamente sin internet. La ETA es inicial y los favoritos no se sincronizan entre dispositivos.

El publicador solo crea la candidata privada y verifica su integridad. No cambia testVersion ni relVersion, no aprueba por el dueño y no libera a Kelin. No activa facturación.
