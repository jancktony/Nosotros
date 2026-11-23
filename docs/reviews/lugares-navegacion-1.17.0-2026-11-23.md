# Candidata privada 1.17.0 · 23-11-2026

## Resultado

- Revisión compilada y probada: `bec8bb39204febde8f870d0ab2c0825a273412de`.
- PR integrado: [#27](https://github.com/jancktony/Nosotros/pull/27), merge `ca0bed58d22a63b21d60e1aac3a590eafe2df34b`.
- Versión Android 1.17.0, código 35. APK firmada de **74.377.823 bytes**, distribuida en **142 fragmentos**.
- SHA-256 del artefacto de GitHub: `53a6be3a927eb092d528057476644e26c06de4e192d393a6bb1228de9376337c`.
- El publicador confirmó el 23-11-2026 a las 13:16:38 de Bogotá: «Candidata privada 1.17.0 verificada: 74377823 bytes en 142 fragmentos. Solo el panel libera a la pareja.» Comprueba los bytes descargados de Firestore antes de marcar ready.
- Release de GitHub conservada como borrador, apuntando a la revisión probada. La candidata se instala desde Administración → Versiones → Instalar solo para mí.
- Web privada publicada: https://nosotros-1e43a.web.app/. No se modificaron testVersion/relVersion, no se aprobó la prueba del dueño ni se liberó a Kelin.

## Integración de lo aprobado

Los lugares guardan dirección y ciudad separadas y requieren revisar el pin después de cambiarlas. Usan el buscador existente y permiten ajuste manual; los resultados aproximados se identifican. La dirección es el título de mapa/lista y de los nuevos registros. Se preservan los lugares anteriores; el nombre compatible que reciben los clientes antiguos se actualiza al confirmar una dirección nueva. Volver del selector conserva el borrador. Las etiquetas del mapa tienen ancho limitado; el dato completo sigue en la ficha.

La actividad ocupa una franja compacta y usa el icono instalado del emisor, bajo el consentimiento y la función ya existentes. Se exporta un PNG de 32×32 y hasta 8192 caracteres base64; el caché de iconos del detector está limitado a 24 entradas. La lectura nativa y web valida tamaño/dimensiones y usa un icono genérico si no está disponible. No hay catálogo externo, inventario de apps ni descarga de iconos a terceros. Actividad revocada, antigua o sin etiqueta no muestra el icono anterior. Cada teléfono necesita la nueva versión para enviar su icono.

La batería usa porcentaje interior y conserva un rayo visible al cargar. La barra tiene Onda, Foco y Muesca con movimiento configurable por teléfono o navegador, mantiene cuatro accesos y marca Más en sus pantallas secundarias. Su tamaño habitual es de 54 dp y ancho máximo 320 dp; se adapta a letra grande sin cambiar el ancho del mapa. Rosa original continúa como predeterminado y se conservan las elecciones personales.

## Comprobaciones

| Comprobación | Evidencia de la revisión probada |
| --- | --- |
| Reglas de privacidad y compatibilidad | 32 pruebas aprobadas |
| Web y actualizaciones incrementales | 9 pruebas aprobadas |
| Navegador | 5 anchos, 4 temas, tres geometrías y movimiento reducido |
| Android | Compilación debug/release y pruebas unitarias aprobadas |
| Interfaz, mapa, editor e iconos | 13 pruebas instrumentales aprobadas |
| Voz | 11 pruebas instrumentales existentes aprobadas |
| Distribución | APK firmada y lectura de integridad de los 142 fragmentos antes de ready |

Ejecuciones finales: [seguridad/Android](https://github.com/jancktony/Nosotros/actions/runs/45234806891), [web/reglas](https://github.com/jancktony/Nosotros/actions/runs/45234792771), [interfaz](https://github.com/jancktony/Nosotros/actions/runs/45234792877), [voz](https://github.com/jancktony/Nosotros/actions/runs/45234792797), [publicación](https://github.com/jancktony/Nosotros/actions/runs/45234792849).

## Límites de la evidencia

La búsqueda de direcciones depende de cobertura de los proveedores existentes y de la confirmación manual: las pruebas no garantizan localizar automáticamente cada número en Barranquilla o Riohacha. Las pruebas de voz usan instancias emuladas; no sustituyen una llamada real entre los Samsung ni prometen conexión universal. El dueño conserva su prueba física y la decisión de liberar.

El entorno de ejecución quedó no disponible durante la integración. Se completaron los cambios mediante las operaciones de árbol/commit/ref de GitHub y se validaron con Actions. No se afirma haber sincronizado el checkout local después de esa interrupción. No se creó una IPA ni infraestructura de pago.
