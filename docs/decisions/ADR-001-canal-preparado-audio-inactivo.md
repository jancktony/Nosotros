# ADR-001: Separar el canal preparado de los recursos de audio

Estado: decisión técnica dentro de la corrección autorizada para 1.15.9.
Fecha: 2026-11-19. Implementación: Codex. Restricciones del dueño: conexión rápida, recepción voluntaria, batería y servicios gratuitos.

## Contexto
Cerrar PeerConnection tras un minuto de silencio obliga a repetir oferta, respuesta e ICE. La disponibilidad dependía de haber creado AudioTrack, por lo que mantener la conexión también mantenía recursos de audio innecesarios.

## Decisión
Conservar el canal de datos ya establecido mientras siga válido y la recepción continúe activa. Crear AudioTrack/codec al recibir o hablar; liberar recursos tras un minuto de inactividad. La disponibilidad exige canal abierto e ICE conectado. El micrófono conserva la restricción de actividad visible y permiso.

## Alternativas consideradas
- Cerrar todo tras un minuto: ahorra la conexión, pero repite negociación antes de la siguiente voz.
- Mantener siempre AudioTrack y decoder: evita esa preparación pero retiene recursos de audio sin uso.
- Añadir TURN o el servidor persistente: sigue siendo una alternativa de transporte separada; requiere activar alojamiento y no resuelve por sí sola la gestión local de recursos.

## Consecuencias
Se reduce la negociación tras silencio en conexiones válidas; el transporte preparado mantiene su tráfico de control. No garantiza conectividad entre todas las redes. El cierre de sesión, la desactivación, la interrupción de red y los permisos siguen controlando el canal. Para evitar que una versión antigua cierre su extremo después de un minuto, ambos clientes necesitan la corrección.
