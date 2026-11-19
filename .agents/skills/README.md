# Skills de Nosotros

Guías importadas del ZIP que Jancktony adjuntó en este chat. Se instalan en el repositorio para futuras tareas con acceso al proyecto; no modifican el perfil global de ChatGPT. Los originales se conservan con sus recursos y textos. La adaptación siguiente y `../../AGENTS.md` (en la raíz del repositorio) determinan cómo aplicarlos a Nosotros.

## Selección por tarea

| Tarea | Skill | Aplicación en Nosotros |
| --- | --- | --- |
| Diseñar o mejorar pantallas | [impeccable](impeccable/SKILL.md) | Guía principal de diseño para Compose y web; conservar la identidad aprobada. |
| Animaciones y respuesta de controles | [emil-design-eng](emil-design-eng/SKILL.md) | Movimiento breve, accesible y acorde con batería y rendimiento del mapa. |
| Vista previa antes de implementar | [maqueta-viva](maqueta-viva/SKILL.md) | Código interactivo, datos ficticios etiquetados, sin acciones sobre teléfonos reales. |
| Acceso, permisos y datos privados | [cybersecurity](cybersecurity/SKILL.md) | Auth, reglas Firebase, consentimiento, voz y distribución privada. |
| Lentitud, batería y conexión | [performance-audit](performance-audit/SKILL.md) | Medir render, red, recursos de audio y consultas; distinguir análisis estático de medición física. |
| Usabilidad y accesibilidad | [uiux-audit](uiux-audit/SKILL.md) | Tamaños móviles, plegable y web; letra grande, foco, contraste y controles táctiles. |
| Calidad y mantenimiento | [tech-audit](tech-audit/SKILL.md) | Revisar Kotlin, JavaScript, reglas y workflows con evidencia concreta. |
| Funciones que existen pero no llegan al usuario | [deuda-invisible](deuda-invisible/SKILL.md) | Seguir el circuito dato → servicio → pantalla; incluir flags y versiones instaladas. |
| Plan de una función compleja | [spec](spec/SKILL.md) | Contrato breve, fallos y verificación; continuar lo ya autorizado sin otra aprobación de trámite. |
| Revisar una implementación | [review](review/SKILL.md) | Comparar con la petición, spec si existe, privacidad y pruebas. No bloquear una corrección por falta de una spec previa. |
| Decisiones técnicas relevantes | [adr](adr/SKILL.md) | Registrar alternativas, límites gratuitos y decisión real; no inventar aceptación del dueño. |
| Reorganizar código | [refactor](refactor/SKILL.md) | Pasos pequeños con comprobaciones proporcionales; conservar cambios ajenos y el comportamiento. |
| Revisión específica de la web | [web-design-guidelines](web-design-guidelines/SKILL.md) | Accesibilidad y controles web; no imponer tecnologías nuevas. |

## Adaptación al proyecto

- Leer `AGENTS.md` de la raíz antes de usar una guía. Sus reglas y la petición concreta tienen prioridad sobre los ejemplos y procedimientos de los originales.
- Elegir una guía principal y solo las referencias necesarias. No ejecutar 13 auditorías ni cargar todos los documentos por una corrección pequeña.
- Las menciones a `CLAUDE.md`, `.claude/rules`, herramientas de Claude o rutas de otros proyectos se traducen al contexto y las herramientas reales disponibles. La instalación no activa hooks ni descarga ejecutables.
- Las indicaciones de pedir credenciales de `uiux-audit` se sustituyen por sesión autorizada o intervención local del dueño; nunca contraseñas en el chat.
- Los ejemplos SQL de `deuda-invisible` y backend de `performance-audit` se aplican conceptualmente a colecciones, listeners, reglas y servicios de Nosotros. No se ejecutan comandos de proyectos ajenos.
- Las aprobaciones de plantillas (`spec`, `refactor`) no anulan autorizaciones existentes. Un informe `APPROVE` de `review` es una valoración técnica; nunca aprueba la prueba del administrador ni libera una versión a Kelin.
- El presupuesto sigue siendo gratuito y la aplicación personal para dos personas. No ejecutar estrés en producción, contratar plataformas creativas ni enviar datos a terceros como efecto de una guía.
- Mantener las vistas previas solicitadas en código, no enviar imágenes salvo petición. Si se usan capturas internas para verificar, no presentarlas como la vista previa pedida.
- Esta instalación no incluye una auditoría general del producto, una corrección del servidor del walkie ni una nueva versión de la aplicación.

## Procedencia e inventario

`docs/skills-import.json` registra las 82 entradas, las 13 seleccionadas y el SHA-256 del ZIP y de cada archivo instalado. Las otras 69 no se activaron: incluyen duplicados, marcas de terceros, documentos/ofimática, marketing, creación de video, herramientas exclusivas de Claude y arranque de stacks distintos. Su presencia en el inventario no implica que estén instaladas ni probadas.

Para ampliar esta selección, revisar la skill solicitada y sus dependencias contra las reglas del proyecto. No importar toda la colección por defecto.
