# Menú flotante · Nosotros 1.17.2

Petición y aprobación: Jancktony revisó la maqueta interactiva del menú flotante del reel de Code By Gaurav y ordenó «implementa esa funcion a la app». La fuente de la propuesta se conserva en `/workspace/entregas/nosotros-menu.html`; se observaron directamente fotogramas del reel 2165202627413832, no solamente su descripción.

## Resultado

Android abre Más encima de la barra sin navegar primero a otra pantalla. Ofrece Historial, Lugares, Fotos y Perfil; este último abre directamente la categoría existente. El botón cambia a X/Cerrar, y el panel usa escala, desplazamiento y opacidad. Selección, toque exterior y Atrás lo cierran. La sección secundaria activa queda identificada en la barra.

Se conservan los temas, Onda/Foco/Muesca, funciones habilitadas y preferencias de movimiento. El panel tiene límites de altura, desplazamiento para texto grande y objetivos táctiles de al menos 48 dp. El posicionamiento respeta los límites de la ventana y RTL. No se modifica el mapa, ubicación, voz, permisos, dependencias, autenticación ni flags del administrador. No se añade desenfoque costoso sobre el mapa 3D.

Alcance: implementación nativa Android. El compañero iOS continúa abriendo el panel web existente; su generación automática de IPA sin firma sigue activa y no supone equivalencia con las funciones Android.

## Evidencia

- Revisión probada: `89e97e2e9175f9fb4c167dbab790c083667a3b8b`.
- PR 30, integrada mediante `6ff150024c5c75fbeca130b5cd9d8cab0b620936`.
- Compilación Android/pruebas unitarias y reglas/web: Actions `45491750411`, correcto.
- Interfaz/mapa: Actions `45491731866`, 17 pruebas correctas, incluidas 2 nuevas: mantener pantalla/navegar/cerrar con Atrás; letra grande sin Voz y acceso a Perfil.
- Voz: Actions `45491731881`, 11 pruebas correctas.
- Publicación privada: Actions `45491731780`, exige las comprobaciones de esta misma revisión.
- APK `Nosotros-v1.17.2.apk`: versión 1.17.2, código 37, 74.394.211 bytes.
- SHA-256: `809ebfbc143e736c00fd04c54070ad4f8a9e6420d6c63239be8a501c5cdf1dae`.

Las comprobaciones son automáticas; queda pendiente la prueba física del dueño. No se aprueba esa prueba, no se modifica `testVersion`/`relVersion` y no se libera a Kelin. La candidata permanece privada y su liberación se decide desde Administración.

Publicación terminada correctamente: el registro de Actions confirmó `Candidata privada 1.17.2 verificada: 74394211 bytes en 142 fragmentos` el 24-11-2026 a las 16:42:40 de Colombia. El IPA 1.17.2 sin firma también se generó correctamente en Actions `45492913348` y se adjuntó a la misma candidata privada. Ambos artefactos permanecen en el borrador v1.17.2; no se modificó la liberación a la pareja.
