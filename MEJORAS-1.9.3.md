# Nosotros 1.9.3 · candidata para pruebas del administrador

- El mapa satélite amplía imágenes del nivel 17 disponible en vez de solicitar niveles sin cobertura. Se confirmó que Esri devuelve HTTP 200 con una imagen gris «Map data not yet available» en nivel 19 en Riohacha. La cámara mantiene su ampliación máxima; ampliar no inventa detalle fotográfico.
- El estado en vivo muestra la actividad actual, no el transporte predominante de todo el trayecto.
- Detección de vehículo/a pie combina transiciones de Android y varias lecturas GPS precisas. Descarta lecturas antiguas, picos aislados y desplazamientos inferiores al margen de error. Caminar puede recuperarse tras bajar del vehículo; una parada no se convierte automáticamente en caminar.
- Se conservan las transiciones WALKING/RUNNING admitidas por Android; eventos de actividad antiguos no cambian el estado actual.
- Carro/moto usan el vehículo habitual configurado: el GPS no distingue esos vehículos ni un bus en tráfico. Se elimina la inferencia de bus por semáforos.
- Esta candidata no cambia la versión aprobada de la pareja. El administrador instala, prueba, solicita correcciones o aprueba y libera desde su panel.

Validación: pruebas unitarias de movimiento y compilación Android en CI. La recepción de sensores Samsung y la cobertura satélite fuera de los puntos consultados requieren pruebas en los teléfonos.
