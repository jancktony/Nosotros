# Plantilla de Reporte — Auditoría UI/UX

Usa esta estructura exacta. Reemplaza todo lo que está en `[corchetes]`.
No omitas secciones — si no hay hallazgos en una severidad, escribe "Sin hallazgos en esta categoría."

---

```markdown
# Auditoría UI/UX — [Nombre de la App] — [Fecha]

**Scope**: [URLs / pantallas / flujos auditados]  
**Método**: [URL en vivo con playwright-cli / Screenshots provistos]  
**Viewports**: [Desktop 1280×900 / Mobile 375×812 / Solo desktop / Solo screenshots]

---

## Resumen ejecutivo

[3-5 líneas. Qué hace la app, cuál es el estado general de la UX, cuál es el hallazgo más importante,
y el tono general (ej: "La app tiene una base sólida pero hay N problemas de accesibilidad críticos
que deben resolverse antes del lanzamiento").]

---

## Score por categoría

| Categoría | Score (1–5) | Estado |
|-----------|-------------|--------|
| Usabilidad (Nielsen) | X/5 | 🔴/🟠/🟡/🟢 |
| Accesibilidad (WCAG) | X/5 | 🔴/🟠/🟡/🟢 |
| Diseño Visual | X/5 | 🔴/🟠/🟡/🟢 |
| Experiencia Mobile | X/5 | 🔴/🟠/🟡/🟢 |
| Consistencia | X/5 | 🔴/🟠/🟡/🟢 |
| **PROMEDIO** | **X/5** | |

Escala: 5=Excelente · 4=Bueno · 3=Aceptable · 2=Deficiente · 1=Bloqueado

---

## Hallazgos

### 🔴 Críticos — [N hallazgos]

> Bloquean al usuario o crean barreras de accesibilidad graves. Prioridad máxima.

---

#### 🔴 [Título descriptivo del problema]

- **Pantalla/flujo**: [Nombre de pantalla o flujo]
- **Evidencia**: `[nombre-screenshot.png]` — [descripción de lo que se ve en el screenshot]
- **Problema**: [Descripción clara del problema y por qué afecta al usuario]
- **Recomendación**: [Acción concreta y específica]
- **Esfuerzo**: S / M / L
- **Principio**: Nielsen #N — [nombre] / WCAG [criterio] / Diseño Visual

---

[Repetir para cada hallazgo crítico]

---

### 🟠 Altos — [N hallazgos]

> Dañan significativamente la experiencia. Resolver en el próximo sprint.

---

#### 🟠 [Título descriptivo]

- **Pantalla/flujo**: [...]
- **Evidencia**: `[screenshot]` — [descripción]
- **Problema**: [...]
- **Recomendación**: [...]
- **Esfuerzo**: S / M / L
- **Principio**: [...]

---

[Repetir para cada hallazgo alto]

---

### 🟡 Medios — [N hallazgos]

> Generan fricción o confusión. Resolver en las próximas 2-3 semanas.

---

#### 🟡 [Título descriptivo]

- **Pantalla/flujo**: [...]
- **Evidencia**: `[screenshot]` — [descripción]
- **Problema**: [...]
- **Recomendación**: [...]
- **Esfuerzo**: S / M / L
- **Principio**: [...]

---

[Repetir para cada hallazgo medio]

---

### 🟢 Bajos — [N hallazgos]

> Mejoras de pulimiento. Resolver cuando haya capacidad.

---

#### 🟢 [Título descriptivo]

- **Pantalla/flujo**: [...]
- **Evidencia**: `[screenshot]` — [descripción]
- **Problema**: [...]
- **Recomendación**: [...]
- **Esfuerzo**: S / M / L
- **Principio**: [...]

---

[Repetir para cada hallazgo bajo]

---

## Quick Wins — Top 3 cambios de mayor impacto

Los 3 cambios que más mejorarán la experiencia con el menor esfuerzo posible:

1. **[Título]** — [Una línea describiendo el cambio y su impacto] · Esfuerzo: S
2. **[Título]** — [Una línea describiendo el cambio y su impacto] · Esfuerzo: S/M
3. **[Título]** — [Una línea describiendo el cambio y su impacto] · Esfuerzo: M

---

## Resumen de hallazgos

| Severidad | Cantidad | Esfuerzo total estimado |
|-----------|----------|------------------------|
| 🔴 Críticos | N | [estimado total] |
| 🟠 Altos | N | [estimado total] |
| 🟡 Medios | N | [estimado total] |
| 🟢 Bajos | N | [estimado total] |
| **Total** | **N** | |

---

## Próximos pasos sugeridos

1. [Acción concreta, quién la hace, cuándo]
2. [Acción concreta]
3. [Acción concreta]

[Cierre opcional: una línea de contexto o motivación para el equipo.]
```
