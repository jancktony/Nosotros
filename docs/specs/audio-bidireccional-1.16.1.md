# Audio en un solo sentido

El dueño informa que Kelin oye su voz y él no oye la de Kelin. El pitido de Probar altavoz se escucha. Metadata sin contenido confirma dueño 1.16.0, pareja 1.15.9, mismo protocolo walkie-amrnb-v1 y ambos consentimientos del canal alternativo activos. Esto no prueba que la pareja capture audio ni que el PCM recibido se reproduzca.

Defectos verificables del código: AudioTrack.write ignoraba resultados negativos/parciales; un fallo del hilo decodificador no invalidaba PushAudio; WalkieRelay conservaba esa instancia sin consumidor. La captura trataba lecturas negativas como un fin normal y no detectaba silenciamiento de Android.

Corregir salida con recuperación única y acotada, permitir interrumpir escrituras al cerrar, invalidar reproductores fallidos y recrearlos al recibir. Distinguir captura de reproducción y verificar silenciamiento de AudioRecord como máximo una vez por segundo. No subir volumen, cambiar permisos ni forzar salida sobre accesorios del usuario.

Pruebas: dispositivo PCM falso con error de ruta y escrituras parciales, errores permanentes, cierre concurrente; codificación AMR sintética y dos PushAudio reales que reproducen ambos sentidos en turnos consecutivos. Mantener las pruebas de red y recepción existentes. No grabar micrófonos o leer audio/GPS de producción para diagnosticar. Publicar candidata privada, sin liberar a Kelin, y distinguir las pruebas de emulador de las dos pruebas físicas pendientes.
