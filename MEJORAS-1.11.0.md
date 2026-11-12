# Nosotros 1.11.0 · diseño aprobado, entrega para pruebas

Vista previa aprobada: https://nosotros-vista-previa.metricpiano32.chatgpt.site/ . Jancktony autorizó implementar y pidió dejar como regla permanente mostrar una vista previa breve y hecha con código antes de futuros cambios de interfaz. La regla está en AGENTS.md.

## Aplicación Android

- Una misma línea visual para mapa, historial, lugares, voz, galería, navegación, ajustes y administración. Temas Noche, Claro y Rosa; accesos por apartados y herramientas agrupadas.
- Walkie directo fijo, sin contenedor de desplazamiento. Captura del puntero en la fase inicial: mover el dedo fuera del botón no termina el habla. Soltar, cancelar, perder el foco o salir de pantalla detienen la captura; se conserva el límite de 30 segundos y el audio comprimido de 1.10.
- Opciones de voz en un diálogo separado. El botón se adapta al espacio disponible. Prueba instrumental de deriva, cancelación y tamaño de pantalla pequeño/grande, además de la prueba del códec real.
- Perfiles compactos en el mapa. Panel con posición mínima de 88 dp y estado totalmente oculto. Solo gestos de cámara lo ocultan; centrar automáticamente no lo hace. Botón para volver a abrir detalles. Márgenes y controles se adaptan al panel.
- Galería privada con filtros, cuadrícula, fecha, navegación y visor ampliable de 1x a 4x. Carga de fotos completas bajo demanda y recuerdos anteriores por páginas de 40. Confirmación antes de borrar y eliminación atómica de foto y contenido.
- Ajustes: General, Perfil, Actividad, Ubicación, Avisos, Widgets y Cuenta. Tarjetas, vistas de widgets y horarios de actividad en un diálogo. Cerrar sesión permanece al final.
- Ocultar aviso fijo abre **solo** la categoría de segundo plano de Android. No se desactivan automáticamente los avisos de llegada/salida ni se promete quitar el indicador del sistema en Aplicaciones activas.

## Administración y actualizaciones

- Resumen, Versiones, Funciones y Herramientas, con acceso directo y enlace copiable al panel web.
- Probar solo en el teléfono del dueño; aprobar o anotar correcciones; liberar para la pareja.
- «Forzar actualización» es una orden puntual para la versión aprobada, vinculada al manifiesto y a la prueba instalada por el dueño. Solo el administrador puede emitirla. Nonce de servidor para reintentos; no fuerza versiones privadas sin aprobación.
- La orden intenta instalar aunque esté apagada la preferencia automática personal. Wi-Fi/red sin medición por defecto; permitir datos móviles requiere una decisión explícita del dueño. Android puede pedir confirmación. Teléfonos con versiones antiguas necesitan actualizar primero para comprender la orden.
- Estado comunicado desde el propio teléfono: descargando, instalando, pendiente de confirmar, instalada o fallo. Se vuelve a comprobar al recuperar una red sin medición. No se agregan notificaciones de actualización a la barra.
- La candidata publicada no aprueba la prueba ni cambia la versión liberada para la pareja. El dueño conserva esas decisiones.

## Web privada

https://nosotros-1e43a.web.app/ · Firebase Hosting. Mismas dos cuentas autorizadas y verificadas; el administrador conserva los controles de versiones/funciones y cada persona solo edita su perfil. La fecha de aniversario es compartida. El nombre se sincroniza sin que un teléfono desconectado sobrescriba una edición reciente.

- Teléfonos, Galería, Ajustes y Administración. Apariencia local en cada navegador. La cámara del mapa se conserva al llegar nuevos datos; el contenido privado se retira al salir.
- Última ubicación recibida, batería, versión y estado de actualización; solicitar ubicación reciente respeta pausa y permisos; ruta a la última posición recibida.
- «Hacer sonar» exige disponibilidad habilitada por el dueño y consentimiento previo **en el teléfono receptor**. Comando autenticado, vigencia de 60 segundos, intervalo de 10 segundos y antirrepetición persistente. Sonido de alarma hasta 30 segundos y botón para detenerlo dentro de la app. Respeta el volumen/No molestar; no localiza un teléfono apagado ni sustituye la red Buscar de Apple.
- Los controles web no conceden permisos del sistema ni aceptan actividad/sueño por otra persona. No hay acceso anónimo ni claves de servidor en el navegador.

## iPhone

El proyecto ios/ compila un compañero SwiftUI/WebKit del panel para iOS 16+. Su alcance y límites están en ios/README.md y en la propia interfaz. El IPA es un binario real arm64, **sin firma/provisión Apple válida**: no se instala directamente y no se garantiza que Scarlet lo firme ni que su certificado dure indefinidamente. No se ha comprado una cuenta ni se han utilizado certificados de terceros.

No es una conversión del APK ni una versión completa con ubicación y walkie en segundo plano en iOS. El panel también puede usarse desde Safari y añadirse al inicio. Las dos cuentas y las reglas Firebase siguen controlando el acceso.

## Validación

CI compila Android y comprueba reglas/unidades. Pruebas instrumentales verifican el códec y los gestos del walkie sin mensajes ni cuentas personales. Regresión web con datos simulados: roles, perfil propio, aprobación/liberación/orden forzada, sonido con permiso, galería y caducidad/revocación de actividad. Compilación iPhone con Xcode y comprobación de binario arm64 sin perfil de aprovisionamiento. No se inició sesión con contraseñas reales ni se enviaron mensajes/correos de prueba.

La audición y recepción en ambos Samsung, permisos de One UI, ubicación en segundo plano y comportamiento de Scarlet requieren pruebas físicas del dueño. No se habilitó facturación ni un bucket de pago.
