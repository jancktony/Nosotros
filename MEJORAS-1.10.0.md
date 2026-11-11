# Nosotros 1.10.0 · pruebas del administrador

Las funciones nuevas comienzan apagadas en Administración → Funciones. El dueño puede probarlas solo en su teléfono y después habilitarlas para ambos. La versión de la pareja solo cambia cuando el dueño aprueba y libera.

## Actividad voluntaria

- Ajustes → Actividad: cada persona acepta compartir su última aplicación detectada y/o su descanso estimado. El administrador no puede dar consentimiento por su pareja.
- La app en uso necesita el permiso especial de acceso de uso de Android, concedido en ese teléfono. Solo se comparte el nombre; no mensajes, imágenes, contraseñas, historial ni una lista de apps instaladas. En multiventana se muestra la última actividad detectada, no una afirmación de atención exclusiva.
- Actualización aproximada cada 30 segundos con pantalla desbloqueada. Al bloquear se vacía la app. Solo estado actual en un documento por usuario; sin historial. El estado en pantalla caduca a los 3 minutos sin señal.
- El descanso usa pantalla bloqueada/inactividad observada desde que se activa, no datos médicos ni alarmas. Horario personal configurable. Tras 30 min nocturnos muestra «Posiblemente durmiendo»; una hora después de la hora habitual muestra «Pasó su hora habitual de despertar». Nunca certifica sueño ni obliga a responder.
- El descanso deja de considerarse actual a los 12 min sin señal; un teléfono apagado no se interpreta como dormido. No se reconstruye el sueño anterior a activar o reiniciar el servicio. Sin alertas adicionales.
- Cada interruptor permite retirar el consentimiento. Sin conexión, el borrado se encola y la interfaz explica que está pendiente; los receptores descartan los datos antiguos. Cerrar sesión/desvincular intenta borrar el estado antes de salir.
- Reglas: solo el propio UID escribe su estado, incluido el consentimiento. Apagar la disponibilidad impide leer/publicar el estado ajeno. Solo las dos cuentas verificadas de la pareja.

## Mapa

- Migración a OpenFreeMap, que ofrece mapas públicos sin clave, registro ni facturación: https://openfreemap.org/
- Se verificaron tiles públicos con Universidad del Norte / Universidad Libre Sede Norte en Barranquilla y Universidad de La Guajira en Riohacha.
- Nombres de universidades, colegios y otros puntos de interés de OpenStreetMap, también sobre satélite. No registra automáticamente lugares personales ni inventa nombres donde falten datos.
- Globo real al alejar, con MapLibre Native 2026.11.2-globe. Es una versión preliminar y queda detrás de la función «Globo terrestre experimental». No añade edificios fotografiados ni cobertura de Google Earth. Requiere prueba física en Fold4/A15 antes de liberar.
- Estilos empaquetados con licencia y atribución, tiles mediante el endpoint estable `/planet`. Se mantiene el límite de imagen satélite disponible y su ampliación.
- La web conserva cámara/zoom al llegar datos, muestra únicamente estados voluntarios recientes y soporta globo cuando está habilitado.

## Walkie de bajo consumo

- Reemplaza PCM de 256 kbit/s (~32 KB/s) por AMR-NB mono de 7,95 kbit/s (~1 KB/s de voz). El consumo real es mayor por paquetes, cifrado, señalización y Firebase. No funciona sin internet ni garantiza latencia con cobertura insuficiente.
- Paquetes cortos, colas limitadas y descarte de voz atrasada. El micrófono y el codificador solo se abren al mantener pulsado con canal listo y la actividad visible; límite de 30 s. El audio se cierra por inactividad.
- Se declara el tipo de servicio de micrófono exigido por Android moderno y solo se solicita mientras la actividad está visible y se mantiene pulsado. Al soltar, se retira y se detiene la captura.
- Reproducción por volumen multimedia para evitar que quede en el auricular de llamadas. Botones «Probar altavoz» y «Reconectar».
- Se detectan versiones antiguas para no enviar voz comprimida al receptor PCM. Ambos teléfonos necesitan 1.10 o posterior para probar esta versión del walkie.
- Un segundo STUN y límite de 12 s evitan esperar 45 s por un canal directo bloqueado. STUN no sustituye a TURN ni atraviesa todos los NAT.
- Modo opcional «Redes difíciles»: habilitado por el dueño y aceptado por cada teléfono. Usa pequeños fragmentos privados de voz en Firestore cuando no está listo el canal directo; no necesita contratar TURN ni activar Blaze. Añade retraso (habitualmente del orden de segundos) y consume la cuota gratuita compartida.
- Ambos deben consentir el modo relay. Los fragmentos se eliminan al recibir o tras 15 s por el emisor. Si ambas apps mueren, la limpieza continúa al volver; no se promete borrado de servidor con TTL ni se habilita facturación. La reproducción descarta fragmentos con más de 6 s, paquetes repetidos y la caché local. Se permite limpiar incluso al apagar la función.

## Fuentes y verificación

Inspiración investigada, sin copiar interfaces ni algoritmos privados:
- Bump, descanso estimado por patrones: https://help.bumpmaps.com/en/articles/15058132-sleeping
- Bump, pin (batería y estado): https://help.bumpmaps.com/en/articles/11603065-map-pin
- Bump, lugares públicos/personales: https://help.bumpmaps.com/en/articles/13369523-public-vs-personal-places
- Globo preliminar: https://github.com/maplibre/maplibre-native/releases/tag/android-v2026.11.2-globe
- Acceso de uso Android: https://developer.android.com/reference/android/app/usage/UsageStatsManager

Pruebas: reglas de consentimiento, revocación y relay; estimación nocturna y caducidad; límites del protocolo de voz; compilación Android; prueba instrumental con codificador/decodificador reales usando sonido sintético, sin micrófono, mensajes ni cuentas personales. Los resultados concretos se registran en CI. La recepción de extremo a extremo, volumen Samsung, batería y calidad en datos móviles necesitan pruebas en ambos teléfonos.
