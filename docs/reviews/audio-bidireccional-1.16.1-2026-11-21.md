# Revisión · audio en un solo sentido · 1.16.1

Revisión automatizada por Codex. Contrato: [audio-bidireccional-1.16.1](../specs/audio-bidireccional-1.16.1.md).

El dueño confirma que oye el pitido de Probar altavoz y las notas de voz de Kelin, pero no su voz en directo. Una consulta de metadata proyectada, sin audio, coordenadas, nombres ni SDP, confirmó dueño 1.16.0, pareja 1.15.9, protocolo común y consentimiento de ambos al canal alternativo. No se atribuye el fallo solo a la versión.

## Correcciones

- AudioTrack ya no descarta resultados negativos o parciales: se recrea la salida una sola vez y el cierre puede interrumpir una escritura sin retener su monitor.
- Un hilo decodificador fallido invalida PushAudio; ambos transportes descartan ese motor. El canal alternativo puede recuperar su estado tras volver a aceptar PCM real.
- La captura informa lecturas fallidas y silenciamiento del cliente por Android. No cambia volumen ni fuerza salida sobre accesorios.
- El envío separa cuadros AMR-NB agrupados o partidos, incluyendo la cabecera opcional, en lugar de descartar buffers mayores de 64 bytes. Se conserva el protocolo y bitrate.
- No cambia permisos, reglas de privacidad, temas ni funciones de 1.16.0.

Son defectos verificables del código; no se ha identificado mediante registros de los Samsung cuál provocaba el síntoma físico concreto. Oír una nota no prueba que VOICE_COMMUNICATION capture o que el decodificador AMR reproduzca correctamente en esos dispositivos.

## Verificación y publicación

Código probado: `89bbed4eb7d17fd27727c460475b7bfdba594d23`. Integrado por [PR #26](https://github.com/jancktony/Nosotros/pull/26), merge `f741c654cf247c097e25cecda9ccf79f415b9a99`.

- [Unitarias, reglas y Android](https://github.com/jancktony/Nosotros/actions/runs/44980433167): aprobado; incluye errores permanentes/temporales de salida, escrituras parciales, cierre y fragmentación AMR.
- [Voz Android](https://github.com/jancktony/Nosotros/actions/runs/44980433185): 11 pruebas aprobadas, incluidas dos instancias PushAudio con audio sintético que atraviesa el codificador, el separador de cuadros, el decodificador y AudioTrack en ambos sentidos y turnos consecutivos. Conserva las pruebas de transporte, reconexión y notas.
- [Mapa e interfaz](https://github.com/jancktony/Nosotros/actions/runs/44980433123): aprobado.
- [APK y candidata privada](https://github.com/jancktony/Nosotros/actions/runs/44980433131): aprobada y verificada, 1.16.1 código 34; 74.328.671 bytes, 142 fragmentos; SHA-256 `f5c76405f1fde0b8f22380b146b5eeecd500f7fb4c0d5dd9c17a36087e9eea87`.

Pendiente validar físicamente la llamada en sus Samsung. Las correcciones de captura se aplican únicamente en el teléfono que se actualiza; no se promete arreglo universal de hardware o redes. Se publica solo una candidata privada: no se aprueba ni libera a Kelin, ni se modifican testVersion/relVersion.
