# Nosotros

App Android para compartir ubicación en pareja.

- Mapa oscuro (MapLibre + OpenStreetMap/CARTO) con los dos en vivo y la ruta en curso
- Medio de transporte: a pie, bici, carro, moto o bus (se puede corregir en el historial)
- Lugares frecuentes: sugeridos automáticamente (3 días distintos, más de 15 min) o marcados a mano
- Avisos de llegada y salida para la pareja
- Historial por día con trayectos y rutas
- Batería: GPS adaptativo según actividad, "Ver en vivo" a pedido y ahorro extremo con menos del 20 %
- Pausa de ubicación con aviso a la pareja

## Compilar

1. Copiar `google-services.json` del proyecto de Firebase en `app/`
2. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

## Firebase

- Authentication → método **Anónimo** activado
- Firestore Database creada, con las reglas de `firestore.rules`
- App Android registrada con el paquete `com.tony.nosotros`

## Estructura del código

- `tracking/TrackingService.kt` — servicio en segundo plano, política de batería y precisión
- `tracking/TripRecorder.kt` — trayectos y clasificación del transporte
- `tracking/PlaceEngine.kt` — llegadas, salidas y sugerencias de lugares
- `data/Repo.kt` — acceso a Firestore
- `ui/` — pantallas (Compose)
