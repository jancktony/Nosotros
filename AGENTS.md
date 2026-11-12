# Nosotros · Reglas de trabajo

- Antes de implementar o publicar un cambio de interfaz, mostrar a Jancktony una vista previa breve, interactiva y hecha con código. Sin imágenes ni capturas, salvo que él las pida. Esperar su visto bueno; corregir la propuesta si lo solicita. Conservar la misma vista previa para iterar.
- El rediseño actual fue aprobado expresamente con «haz las implementaciones ya», después de revisar https://nosotros-vista-previa.metricpiano32.chatgpt.site/. Esa aprobación cubre las pantallas y funciones solicitadas en esta conversación; no pedirla de nuevo para terminar este trabajo.
- Publicar candidatas para que el dueño instale y pruebe primero. Nunca aprobar una prueba ni liberar una versión a Kelly automáticamente. Solo el dueño decide desde Administración.
- La app y la web son personales: solo las dos cuentas autorizadas y verificadas. El panel de administrador es exclusivo del dueño. Cada persona conserva el consentimiento sobre su actividad y permisos.
- No activar facturación, Blaze ni servicios de pago sin autorización explícita. No enviar mensajes ni correos en nombre del usuario sin instrucciones.
- Comunicar en español, con explicaciones breves y veraces. Distinguir una función probada automáticamente de una prueba física en teléfonos, y un IPA sin firma de uno instalable.

## Guías de trabajo con código

Reglas solicitadas por Jancktony a partir de `karpathy-guidelines` (MIT), aplicables al escribir, revisar o refactorizar código:

- Pensar antes de programar: declarar los supuestos relevantes, explicar las alternativas que cambien el resultado y aclarar las dudas que impidan trabajar correctamente. Proponer una solución más simple cuando exista. Usar criterio en tareas triviales.
- Priorizar la simplicidad: implementar el mínimo código que resuelva lo solicitado, sin funciones especulativas, abstracciones de un solo uso, opciones no pedidas ni manejo de situaciones imposibles. Simplificar si la solución resulta innecesariamente compleja.
- Hacer cambios precisos: cada línea modificada debe responder a la solicitud. Respetar el estilo existente; no reformatear ni refactorizar código ajeno al objetivo. Eliminar imports, variables o funciones que nuestros cambios dejen sin uso; señalar el código muerto preexistente sin borrarlo por iniciativa propia.
- Trabajar con objetivos verificables: definir qué resultado demuestra que la tarea está resuelta. En tareas de varios pasos, indicar un plan breve con su verificación. Reproducir los fallos cuando corresponda, ejecutar comprobaciones proporcionales al cambio e informar de lo verificado y de los límites pendientes.
