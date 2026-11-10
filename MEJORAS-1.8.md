# Nosotros 1.8

- Avisos de llegada/salida: tanto el título como el texto corto incluyen la acción y el lugar; la hora queda como información adicional. La estimación de llegada conserva el lugar de salida. En Avisos puedes elegir mostrar los detalles en el bloqueo; Android/Samsung también controla su visibilidad.
- Ajustes por categorías: General, Perfil, Ubicación, Avisos, Widgets y Cuenta; Administración solo para el administrador.
- Foto de perfil mediante el selector de fotos de Android: se recorta, comprime y almacena una miniatura privada en el documento del propio miembro. No exige crear un bucket. Se muestra en las tarjetas y en el marcador del mapa al estar quieto.
- Cerrar sesión al final de Ajustes: detiene el servicio, cierra Firebase Auth, limpia preferencias personales, notificaciones y widgets. No desvincula a la pareja ni elimina sus datos del servidor. Al volver a entrar se recuperan el nombre, foto y pareja de esa cuenta. La caché interna de Firestore sigue administrada por su SDK; no se descartan escrituras pendientes al salir.
- Widgets nuevos: Distancia · compacto (rectangular) y Días juntos · compacto (pequeño), además de los dos widgets existentes. No muestran distancia tras cerrar sesión o cuando la pareja está en pausa; indican la antigüedad de la señal. Los widgets se actualizan por cambios del servicio y por el intervalo de Android (30 minutos), no cada segundo en segundo plano.
- Mapa con control 2D/3D persistente: cámara inclinada y extrusión de edificios de la fuente vectorial del estilo. Las alturas dependen de los datos; si falta la altura se usa una representación aproximada. En satélite se inclina la imagen, sin fotogrametría ni terreno 3D.
- Administración: activar últimas versiones automáticas para la pareja, fijar una versión, recargar catálogo, solicitar ubicación reciente respetando pausas y compartir un diagnóstico sin coordenadas.

## Samsung Z Fold4 (One UI 8) y A15

Son widgets Android normales y compactos con categoría de bloqueo. Samsung decide qué apps admite en el editor de miniwidgets/Brief widgets; una app no puede forzar su inclusión en esa lista. No se usa la API de Flex Window: es para la pantalla exterior de los Flip, no la pantalla de bloqueo del Fold4 ni del A15.

1. En el editor de bloqueo, comprobar si aparece Nosotros.
2. Si no aparece, usar Good Lock → LockStar → añadir widgets, si el modelo, software y región lo permiten.
3. Elegir «Días juntos · compacto» o «Distancia · compacto» y ajustar su tamaño. Los botones de la app para añadir widgets solicitan colocarlos en el inicio, no en el bloqueo.

Samsung documenta las [apps admitidas por Brief widgets](https://www.samsung.com/uk/support/mobile-devices/brief-widgets-on-your-samsung-galaxy-devices-lock-screen/) y el uso de [Good Lock/LockStar para widgets de terceros](https://www.samsung.com/uk/mobile-phone-buying-guide/what-are-widgets/). La aparición y el tamaño exactos en el Fold4/A15 requieren comprobarse en los dispositivos; no se pueden validar con la compilación de Android.

## Validación y activación

- Pruebas unitarias para conservar acción/lugar en avisos y evitar distancias ficticias o datos tras salir/pausar.
- Pruebas de reglas en emuladores: avatar propio, límite de tamaño, rechazo de suplantación y política de versiones solo para el administrador.
- Compilación y pruebas Android en GitHub Actions antes de publicar el APK firmado.

Las nuevas comprobaciones de avatar en firestore.rules deben desplegarse en Firebase con las credenciales del proyecto. El resto del acceso privado y Storage conserva los pendientes descritos en SEGURIDAD.md. La publicación de un APK no despliega esas reglas ni activa la distribución privada.
