# WCAG 2.1 AA — Checklist de Evaluación Visual

Lista de criterios verificables visualmente durante una auditoría. Organizada por principio POUR
(Perceptible, Operable, Comprensible, Robusto). Incluye solo criterios evaluables sin código
— para auditoría visual desde screenshots.

---

## PERCEPTIBLE — La información debe ser presentable a los sentidos

### Texto y contraste

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 1.4.3 Contraste (AA) | Texto normal: ratio ≥4.5:1 | Estimar visualmente. Si el gris parece claro sobre blanco → sospechoso |
| 1.4.3 Contraste (AA) | Texto grande (≥18pt o ≥14pt bold): ratio ≥3:1 | Encabezados grandes generalmente pasan |
| 1.4.4 Cambiar tamaño texto | Texto legible hasta 200% zoom | Fuentes < 14px son problemáticas |
| 1.4.11 Contraste no textual | Iconos, bordes de inputs, indicadores: ≥3:1 vs fondo | Inputs con borde muy claro son problema |

**Colores que típicamente fallan:**
- Texto gris claro `#999`, `#aaa`, `#ccc` sobre fondo blanco
- Texto blanco sobre colores pastel o medio
- Placeholders en inputs (casi siempre demasiado claros)
- Labels desactivados (`disabled`) sin alternativa

### Imágenes y medios

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 1.1.1 Contenido no textual | Imágenes con información necesitan alt text | Verificar con código; en screenshot buscar imágenes "decorativas vs informativas" |
| 1.4.5 Imágenes de texto | No usar imagen para mostrar texto importante | Capturas de pantalla de texto, logos con texto |

---

## OPERABLE — Los componentes de UI deben ser navegables

### Navegación y foco

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 2.4.3 Orden del foco | Orden lógico de navegación por teclado | Inferir del layout visual |
| 2.4.7 Foco visible | El elemento enfocado debe ser visualmente distinguible | Verificar en screenshots si hay indicador de foco visible |
| 2.4.4 Propósito del link | Links descriptivos, no "haz clic aquí" | Leer el texto de los links en el screenshot |
| 2.4.6 Encabezados y labels | Encabezados descriptivos que organizan el contenido | Verificar jerarquía visual de títulos |

### Tamaño y espacio (crítico en mobile)

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 2.5.5 Tamaño del target (AA) | Áreas táctiles ≥44×44px | En mobile: estimar si botones son del tamaño de un dedo adulto |
| 2.5.8 Tamaño del target mínimo (2.2) | ≥24×24px como absoluto mínimo | Botones de icono pequeños sin área de tap expandida |

**Señales de problema en mobile:**
- Iconos pequeños sin padding (< 32px de tap area)
- Links en medio de texto denso (difícil de tocar con precisión)
- Elementos interactivos muy juntos (< 8px entre targets)

### Tiempo y movimiento

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 2.2.1 Ajuste de tiempo | Sesiones con timeout deben avisarlo | Verificar si hay timers o sessiones con expiración |
| 2.3.1 Tres destellos | Sin contenido que destelle >3 veces/segundo | Para auditorías estáticas: no aplica |

---

## COMPRENSIBLE — La información y el UI deben ser entendibles

### Texto y legibilidad

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 3.1.1 Idioma de la página | Idioma declarado en HTML | Verificar si el contenido está en el idioma correcto visualmente |
| 3.2.1 Al enfocar | Enfocar un elemento no cambia el contexto | Verificar en live URL |

### Formularios e inputs

| Criterio | Requisito | Cómo verificar |
|----------|-----------|----------------|
| 3.3.1 Identificación de errores | Los errores se describen en texto | Buscar mensajes de error en screenshots |
| 3.3.2 Labels o instrucciones | Campos con labels visibles o instrucciones | Inputs sin label visible son Crítico |
| 3.3.3 Sugerencia de error | Si el error es conocido, sugerir corrección | "Formato inválido" vs "Ingresa formato DD/MM/AAAA" |
| 3.3.4 Prevención de errores | Para acciones importantes: revisar, confirmar, deshacer | Eliminar, pagos, envíos importantes |

**Señales de problema en formularios:**
- Placeholder como único label (desaparece al escribir)
- Campo con asterisco (*) sin explicar que * = requerido
- Validación solo al submit sin feedback inline
- Mensaje de error que solo usa color rojo sin texto

---

## ROBUSTO — El contenido debe ser interpretado por tecnologías de asistencia

Estos criterios son principalmente verificables con código/herramientas (Axe, Lighthouse), no
visualmente. En auditoría visual, se pueden inferir señales:

| Señal visible | Problema probable |
|---------------|-------------------|
| Botones que son divs con onclick | Sin semántica de `<button>` |
| Imágenes informativas sin caption | Probablemente sin alt |
| Tablas sin encabezados claros | Sin `<th>` para screen readers |
| Modales que aparecen sin mover el foco | Trap de foco posiblemente ausente |

---

## Quick reference — Lo que más falla en la práctica

1. **Contraste insuficiente** (texto gris claro, placeholders, texto sobre imagen)
2. **Inputs sin label visible** (solo placeholder)
3. **Touch targets pequeños** en mobile (iconos de acción < 44px)
4. **Mensajes de error solo en color** sin texto descriptivo
5. **Foco no visible** (outline: none sin alternativa)
6. **Links genéricos** ("Ver más", "Haz clic aquí", "Aquí")
7. **Botones desactivados** sin explicación de por qué ni cuándo se activan
