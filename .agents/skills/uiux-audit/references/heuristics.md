# Nielsen's 10 Heuristics — Guía de Evaluación

Referencia detallada para la Fase 2 de la auditoría. Para cada heurística: qué evaluar,
señales de problema, y ejemplos concretos.

---

## H1 — Visibilidad del estado del sistema

**Qué evaluar**: ¿El usuario siempre sabe qué está pasando? ¿Hay feedback de acciones?

Señales de problema:
- Botón de submit sin estado loading → el usuario hace doble click
- Formulario enviado sin confirmación visual
- Proceso largo sin progress indicator
- Carga de página sin skeleton/spinner
- Navegación sin indicar en qué sección está el usuario (breadcrumb, nav activo)

Ejemplos buenos: spinner al hacer submit, barra de progreso en onboarding, nav item destacado.

---

## H2 — Match entre sistema y mundo real

**Qué evaluar**: ¿El lenguaje y los conceptos son familiares para el usuario? ¿Se usan metáforas que tienen sentido?

Señales de problema:
- Terminología técnica/interna expuesta al usuario (IDs, nombres de DB, codes)
- Iconos sin etiqueta cuyo significado es ambiguo
- Fechas en formato ISO sin contexto (2026-04-18 vs "hace 2 días")
- Mensajes de error en inglés en una app en español
- Acciones nombradas con jerga del negocio que el usuario no conoce

Ejemplos buenos: "Guardar" en vez de "POST", formato de fecha relativo, iconos con texto.

---

## H3 — Control y libertad del usuario

**Qué evaluar**: ¿El usuario puede deshacer acciones? ¿Puede salir de flujos sin consecuencias?

Señales de problema:
- Eliminar sin confirmación y sin opción de deshacer
- Flujos sin botón "Cancelar" o "Volver"
- Modales sin opción de cerrar (sin X ni Esc)
- Formularios multi-step sin poder regresar
- Logout que redirige a un lugar inesperado
- Acciones destructivas inmediatas e irreversibles

Ejemplos buenos: "¿Estás seguro? Esta acción no se puede deshacer", botón Cancelar en modales.

---

## H4 — Consistencia y estándares

**Qué evaluar**: ¿Los mismos patrones se aplican igual en toda la app? ¿Se siguen convenciones de la plataforma?

Señales de problema:
- Botones primarios con estilos distintos en diferentes pantallas
- El mismo componente (tabla, card) se ve diferente en cada sección
- Links que a veces son azules subrayados y a veces no tienen estilo
- Iconos distintos para la misma acción (borrar usa 🗑️ en unos lados y ✕ en otros)
- Formularios con validación en tiempo real en algunos campos y solo al submit en otros
- Orden de botones inconsistente (Guardar/Cancelar vs Cancelar/Guardar)

Ejemplos buenos: design system consistente, patrones de interacción uniformes.

---

## H5 — Prevención de errores

**Qué evaluar**: ¿El diseño previene activamente que el usuario cometa errores?

Señales de problema:
- Campos sin formato de ayuda (ej: teléfono sin placeholder "300 000 0000")
- Fechas sin calendario (el usuario puede escribir formato incorrecto)
- Acciones destructivas no protegidas (eliminar sin confirmación)
- Formularios que pierden datos al navegar accidentalmente
- Sin validación inline — el error aparece solo al final
- Dropdowns con opciones que llevan a flujos irreversibles sin advertencia

Ejemplos buenos: confirmación antes de eliminar, placeholder con formato, validación en tiempo real.

---

## H6 — Reconocimiento sobre recuerdo

**Qué evaluar**: ¿El usuario necesita recordar información de pantallas anteriores? ¿Las opciones son visibles?

Señales de problema:
- Wizard/onboarding sin resumen del paso anterior
- Formularios largos donde el usuario no puede ver lo que llenó arriba
- Acciones secundarias escondidas en menús que el usuario no sabe que existen
- Búsquedas sin historial de búsquedas recientes
- Notificaciones que desaparecen antes de que el usuario las lea
- Tooltips solo en hover (no accesible en mobile)

Ejemplos buenos: breadcrumbs, resumen de pasos en wizard, acciones visibles sin necesidad de hover.

---

## H7 — Flexibilidad y eficiencia de uso

**Qué evaluar**: ¿Los usuarios expertos pueden ir más rápido? ¿Los principiantes pueden aprender?

Señales de problema:
- Sin atajos de teclado para acciones frecuentes
- Sin búsqueda global en apps con mucho contenido
- Flujos forzados paso a paso sin shortcut para usuarios que ya saben
- Sin opciones de filtro/ordenamiento en listas largas
- Paginación en vez de scroll infinito para contenido tipo feed
- Sin acceso rápido a las acciones más usadas (siempre enterradas en menús)

Ejemplos buenos: Ctrl+K para búsqueda, favoritos, accesos directos en dashboard.

---

## H8 — Estética y diseño minimalista

**Qué evaluar**: ¿Hay información o elementos innecesarios que compiten por atención?

Señales de problema:
- Texto de ayuda permanente que solo necesitan usuarios nuevos
- Múltiples CTAs igualmente prominentes (el usuario no sabe qué hacer primero)
- Tablas con columnas que nadie usa
- Cards con 8+ campos cuando solo 3 son relevantes
- Decoración visual que no comunica nada (líneas, separadores, iconos decorativos excesivos)
- Pantallas sobrecargadas sin jerarquía clara

La pregunta clave: *¿Qué pasaría si elimino este elemento? ¿El usuario pierde algo?*

---

## H9 — Ayudar a reconocer, diagnosticar y recuperarse de errores

**Qué evaluar**: Cuando algo falla, ¿el mensaje explica qué pasó y cómo solucionarlo?

Señales de problema:
- "Error 500" sin explicación para el usuario
- "El campo es requerido" sin decir cuál campo (en formularios largos)
- Mensajes de error que desaparecen solos antes de que el usuario los lea
- Error genérico "Algo salió mal" sin próximos pasos
- Mensajes de validación que culpan al usuario ("Formato inválido") sin decir el formato correcto
- Solo color rojo para indicar error (no accesible para daltónicos)

Ejemplos buenos: "El correo ya está registrado. ¿Quieres iniciar sesión?", icono + color + texto.

---

## H10 — Ayuda y documentación

**Qué evaluar**: ¿El usuario puede encontrar ayuda cuando la necesita?

Señales de problema:
- Sin onboarding para features complejas
- Sin tooltips en campos o acciones no obvias
- Sin FAQ o sección de ayuda accesible desde la app
- Documentación que existe pero no está enlazada desde donde el usuario tiene la duda
- Tours/walkthroughs que no se pueden pausar ni volver a ver

Nota: las apps bien diseñadas minimizan la necesidad de H10. Si hay muchos hallazgos aquí,
probablemente hay problemas más profundos de H1–H8.

---

## Heurísticas adicionales para apps modernas

### H11 — Feedback inmediato en interacciones
Cada acción del usuario debe tener respuesta visual en < 100ms (aunque sea solo el estado hover/active).
Loading states, optimistic updates, microanimaciones que confirman la acción.

### H12 — Diseño mobile-first y touch
En pantallas táctiles: targets ≥44px, no depender de hover, scroll natural, gestos estándar.
Evaluar si el flujo funciona igual de bien con el pulgar como con el mouse.

### H13 — Manejo de estados vacíos
¿Qué ve el usuario cuando no hay datos? Los empty states deben: explicar por qué está vacío,
decir qué hacer a continuación, y no parecer un error.
