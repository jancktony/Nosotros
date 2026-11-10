# Vista 3D realista (pendiente de activar)

La versión 1.8 usa MapLibre: imágenes satelitales planas y edificios vectoriales.
Esta integración añade un botón **3D real**, que abre Google Maps 3D en pantalla
completa. Mantiene el mapa habitual para navegación y edición de lugares.
La vista de Google muestra las ubicaciones recibidas por la app, sus lugares y
rutas; permite centrar ambos o cada persona, inclinar, girar y acercar con gestos.
Los marcadores usan iniciales (no fotografías de perfil). El mapa no obtiene
ubicaciones adicionales ni cambia los controles existentes de pausa.

Sin clave, el botón explica que la vista todavía no está activada y no crea un
Map3DView ni hace solicitudes a Google Maps 3D. Con clave, se abre solo al pulsar.
No se modifica el seguimiento en segundo plano. Al cerrar, se eliminan los
marcadores y rutas del estado compartido del SDK. Si falla, se ofrece volver.

## Activación por el propietario

1. Comprobar cobertura fotografiada en la ciudad de uso: no todos los edificios
   o zonas tienen malla 3D. Una imagen satelital disponible no garantiza edificios.
2. En Google Cloud, usar un proyecto con facturación y habilitar **Maps 3D SDK for Android**.
   Esto requiere decisión del propietario: este cambio no crea proyectos,
   habilita servicios ni acepta cargos. Revisar tarifas y cuotas antes de activar.
3. Crear una clave limitada a este SDK y a la aplicación Android
   `com.tony.nosotros`, con SHA-1 del certificado de firma **release** ya utilizado.
   No usar la clave de Firebase. Para debug se requiere otra restricción con su
   certificado; no eliminar restricciones para resolver un error de autorización.
4. Guardar la clave en el secreto de Actions `MAPS3D_API_KEY`, nunca en código ni
   en un mensaje. El workflow release ya la pasa al compilador. Para desarrollo,
   usar `secrets.properties` en la raíz (ignorado por git):

   ```properties
   MAPS3D_API_KEY=clave_restringida
   ```

   Las claves Android quedan incluidas en el APK por diseño: las restricciones
   de API, paquete y certificado son necesarias. No son un sustituto de los
   permisos de Firebase que protegen los datos de la pareja.
5. Compilar una versión nueva firmada y probar en Fold4/One UI 8 y A15: carga,
   cobertura, gestos, marcadores, rutas, volver/abrir, rotación, segundo plano,
   cierre de sesión, falta de red y clave no autorizada. No se ha validado con
   un dispositivo físico ni con una clave habilitada en esta preparación.
6. Después de validar y ajustar versión/código, publicar por el canal automático
   existente. Esta rama conserva 1.8 para evitar publicar una función sin activar.

## Dependencia y facturación

SDK nativo `com.google.android.gms:play-services-maps3d:16.0.1`, aislado en un
único Map3DView activo. El mapa de navegación sigue usando MapLibre. No se usa
un WebView de Google Earth ni se extraen sus teselas.

Google exige proyecto con facturación y clave. La documentación consultada el
10 de noviembre de 2026 anuncia cobro del SKU Immersive Maps a partir del
13 de noviembre de 2026. No asumir que una app personal está exenta de cargos.
Una alerta de presupuesto no equivale a un límite de gasto; revisar cuotas que
permita configurar el servicio y los precios vigentes antes de la activación.

Referencias oficiales:
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/setup
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/usage-and-billing
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/overview
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/add-a-3d-map
- https://github.com/googlemaps-samples/android-maps3d-samples
