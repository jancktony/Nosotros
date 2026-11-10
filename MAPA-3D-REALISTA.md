# Viabilidad del mapa 3D fotografiado

Revisión: 10 de noviembre de 2026. Ciudades de uso: Barranquilla y Riohacha,
Colombia. Objetivo: edificios fotografiados como Google Earth.

## Resultado

La tabla oficial de Google Maps Platform marca Colombia con **—** en la columna
**Maps 3D (JavaScript, Android, iOS)**. La leyenda define ese símbolo como no
disponible o con baja calidad/disponibilidad. También indica solo teselas 2D,
sin la marca adicional de cobertura de teselas 3D que sí aparece para países
como Argentina, Brasil o Chile.

Por tanto, la documentación no respalda ofrecer edificios fotografiados en
Barranquilla o Riohacha con el SDK de Google. Una clave, facturación habilitada
y un teléfono compatible no añaden imágenes ausentes del proveedor.

Fuente oficial, columna y fila deben leerse juntas:
https://developers.google.com/maps/coverage

## Qué muestra la versión 1.8

MapLibre inclina la cámara y da volumen a edificios vectoriales cuando los datos
cartográficos incluyen sus huellas/alturas. La vista satelital actual es una
imagen plana. Ninguna de estas técnicas reconstruye fachadas fotografiadas.
Cambiar la inclinación o aumentar una altura por defecto no cumple el objetivo.

## Decisión técnica

Se mantiene el mapa actual. No se integra ni activa Google Maps 3D, no se
introduce su dependencia, no se configura una clave y no se habilita facturación.
La versión y el mecanismo de actualizaciones conservan su configuración.

Antes de integrar otro proveedor, exigir una demostración con edificios
fotografiados de las dos ciudades y comprobar licencia, costes y compatibilidad
Android. Si Google amplía su cobertura, revisar de nuevo la tabla y probar zonas
concretas antes de preparar una publicación.

## Comprobaciones y límites

- Se consultó la documentación oficial de cobertura y del SDK Android.
- Se intentó contrastar las ciudades en el visor oficial de superficies 3D:
  https://developers.google.com/maps/documentation/javascript/3d/coverage
- En el navegador de prueba, ese visor usó renderizado RASTER y su capa de
  cobertura no estuvo disponible (`isDataDrivenStylingAvailable=false`,
  `FeatureLayer.isAvailable=false`). El control con Nueva York también falló;
  sus capturas sin áreas azules **no son prueba de ausencia de cobertura**.
  La conclusión se basa en la tabla oficial por país.
- No se probó el renderizado nativo en Samsung ni se usó una clave facturable.
- Este resultado se refiere a Google Maps Platform; no demuestra que ningún
  otro proveedor o levantamiento fotogramétrico tenga datos de esas ciudades.

Referencias adicionales:
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/overview
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/setup
- https://developers.google.com/maps/documentation/maps-3d/android-sdk/usage-and-billing
