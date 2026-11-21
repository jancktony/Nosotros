# Fiabilidad y mejoras para dos

Solicitud de Jancktony: 21-11-2026. Implementación funcional autorizada; nuevos diseños pendientes de revisar en la vista previa existente.

## Prioridad y límites

1. Walkie: recuperar automáticamente las conexiones fallidas con espera creciente y sin abandonar tras tres intentos mientras la pantalla está abierta o el botón permanece pulsado. Cancelar al cerrar el servicio. No activar el micrófono sin una pulsación vigente. STUN no garantiza atravesar todas las redes; el retransmisor permanente propio sigue pendiente de infraestructura gratuita autorizada. No presentar estos cambios como solución universal.
2. Ubicación: la actividad del teléfono no renueva la antigüedad del GPS. Navegación solo con una medición reciente, precisa y no pausada. Comprobar caducidad, datos ausentes, futuros e imprecisos.
3. Propuesta visual operable: diagnóstico de voz, nota pendiente sin envío silencioso, viaje compartido con cancelación, estados GPS, aviso interno opcional de batería, favoritos/fechas en galería y observaciones del dueño antes de liberar. Mantener temas personales y rosa original predeterminado.

## Datos y privacidad

Esta fase de vista previa utiliza datos ficticios y no escribe en Firebase salvo autenticación. No graba audio ni consulta GPS. Los nuevos modelos de viaje, acuses de notas y consentimiento de batería requieren reglas y pruebas antes de integrarse realmente. No inferir que una nota llegó solo porque se guardó en el servidor. No enviar automáticamente grabaciones antiguas.

## Verificación

Pruebas Kotlin de caducidad GPS y reintentos posteriores al tercero, compilación Android en CI y pruebas existentes. Vista previa: probar escenarios y efectos de botones, regreso entre pantallas, cuatro temas y tamaños móvil/web. Diferenciar demostración, pruebas automáticas y pruebas físicas con los dos teléfonos.
