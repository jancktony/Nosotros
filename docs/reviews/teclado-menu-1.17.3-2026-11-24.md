# Corrección del teclado al navegar desde Más · 1.17.3

Jancktony reportó que al cambiar de pantalla desde Más el teclado aparecía y se ocultaba brevemente. La corrección conserva el diseño aprobado y no requiere una propuesta visual nueva.

## Cambio

La ventana no editable del menú conserva los eventos de teclado y Atrás, pero usa `FLAG_ALT_FOCUSABLE_IM` para no convertirse en destino del teclado virtual. Mantiene `FLAG_WATCH_OUTSIDE_TOUCH` y la herencia de protección de pantalla de Compose. Al usar la barra se limpia el foco del editor anterior y se oculta su teclado. La selección secundaria se guarda temporalmente y se ejecuta cuando la animación termina y el popup ya se ha retirado, evitando crear los campos del destino mientras la ventana del menú conserva el foco.

Se consultó el código fuente de `ui-android:1.7.6`, compatible con el BOM del proyecto, para utilizar el constructor público de `PopupProperties(flags=...)`. No se incorpora código de la biblioteca ni dependencias nuevas. Sin cambios de autenticación, permisos, servicios de voz, datos privados o liberación a la pareja.

## Comprobación

- Revisión probada: `505cca2e6dce6f2db69e5108eb770c95af78f34d`.
- PR 31 integrada mediante `7a6edd3cb8868a4f8278fe553bc5f202fdf8c413`.
- Android, pruebas unitarias y reglas/web: Actions `45504555664`, correcto.
- Interfaz/mapa: Actions `45504509373`, 18 pruebas correctas. La prueba nueva comienza con un editor enfocado, inspecciona los flags de la ventana real, verifica que el editor pierde el foco y que el destino se crea tras retirar el popup, sin enfocar su editor automáticamente. Las pruebas previas de selección, cierre con Atrás y letra grande siguen pasando.
- Voz: Actions `45504509170`, correcto.
- Publicación privada: Actions `45504508726`.
- APK `Nosotros-v1.17.3.apk`, código 38, 74.394.211 bytes.
- SHA-256: `79d351bbe6b073bd73ae033fe22665647aa6d9d111b686b3fe31882033d4669f`.

La prueba automática verifica el contrato de foco y ventana; no reproduce la interfaz del teclado Samsung ni sustituye la prueba física del dueño. No se marca su prueba como aprobada ni se modifica `testVersion`/`relVersion`. La liberación a Kelin sigue siendo exclusivamente manual desde Administración. El compañero iOS continúa siendo el panel web sin firma, sin equivalencia con la navegación nativa Android.

Publicación privada completada: Actions confirmó `Candidata privada 1.17.3 verificada: 74394211 bytes en 142 fragmentos` el 24-11-2026 a las 18:00:57 de Colombia. La candidata está lista para la prueba del dueño y permanece en borrador en GitHub. El compañero iOS 1.17.3 sin firma también se generó automáticamente con éxito en Actions `45505490504` y quedó adjunto al mismo borrador; esta corrección del menú corresponde a Android.
