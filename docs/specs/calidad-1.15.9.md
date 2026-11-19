# Calidad 1.15.9

Estado: implementada, comprobada automáticamente y publicada como candidata del administrador. Alcance autorizado por Jancktony al pedir aplicar las skills y lanzar una versión. La prueba física y liberación a Kelin siguen bajo su control. Fecha: 2026-11-19.

## Problema y evidencia inicial
- WalkieConnection liga disponibilidad a AudioTrack y cierra toda la conexión tras 60 s de silencio. El siguiente mensaje vuelve a negociar aunque la red no cambió.
- Los callbacks del micrófono no distinguen una pulsación de la siguiente; un cierre antiguo puede cancelar una pulsación nueva.
- LiveMap crea imágenes con una clave diferente por velocidad y no retira las anteriores del estilo.
- La web elimina y reconstruye MapLibre al recibir snapshots; además usa updatedAt para indicar la edad del GPS y borra el borrador de la marca durante una actualización.

## Solución
Mantener el diseño aprobado, corregir estos comportamientos y publicar una candidata 1.15.9 solo para el administrador. La revisión de seguridad comprueba que no se amplíen cuentas, consentimiento ni permisos de Firebase.

## Diseño y secuencia
1. Separar canal WebRTC preparado y recursos de audio: crear audio solo al hablar o recibir, soltarlo tras inactividad sin cerrar el canal.
2. Copiar los buffers nativos antes de cruzar hilos y validar tamaño/formato; disponibilidad exige canal abierto e ICE conectado.
3. Ordenar callbacks de fallo y distinguir generaciones de pulsaciones para conservar el control del micrófono.
4. Limitar las imágenes históricas del mapa, conservando todas las utilizadas en la actualización actual.
5. Actualizar las tarjetas web sin reemplazar el mapa durante snapshots; conservar cámara y marcadores, borradores y foco al editar.
6. Usar locationAt para la antigüedad del GPS y ejecutar comprobaciones antes de distribuir.

## Verificación
- Web: múltiples snapshots conservan la misma instancia de mapa/cámara; editar marca conserva texto/foco; GPS antiguo no parece reciente por cambiar batería; cerrar sesión libera el mapa.
- Android: capacidad limitada de imágenes con imágenes visibles protegidas; WebRTC nativo y reglas en emuladores; canal preparado y envío después de inactividad; pulsar/soltar y codec; panel y proceso de instalación.
- Seguridad: suite de reglas de cuentas autorizadas y control de versiones.

## Alternativas y límites
- Se conserva el transporte existente y su compatibilidad; la activación del servidor persistente de #20 sigue pendiente. STUN no garantiza conexión a través de todas las redes.
- Se conserva la dirección visual aprobada: la corrección actúa en recursos y estado, sin un rediseño nuevo que requiera otra propuesta.
- No se ejecuta carga contra producción ni se habilita facturación. No se modifican testVersion, relVersion o flags de la pareja.
- Las mediciones automáticas no demuestran FPS en el Samsung ni conectividad entre Barranquilla y Riohacha. Eso requiere la prueba física del dueño.
