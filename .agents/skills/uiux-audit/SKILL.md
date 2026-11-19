---
name: uiux-audit
description: >
  Realiza auditorías UI/UX completas de cualquier aplicación web. Úsala cuando el usuario
  mencione: revisar la interfaz, auditar UX, evaluar usabilidad, check de UI, revisión de
  diseño, WCAG, accesibilidad, heurísticas, mejorar la app, qué tiene mal la interfaz,
  feedback de diseño, o cuando comparta screenshots para análisis visual. También aplica si
  el usuario dice "cómo se ve", "qué mejorarías", "analiza la UI", "dame feedback del diseño",
  "revisa la experiencia de usuario" o variantes. Acepta URL en vivo (navega con playwright-cli)
  o screenshots directos. Produce un reporte Markdown estructurado con hallazgos por severidad
  (Crítico/Alto/Medio/Bajo), evidencia visual, y recomendaciones accionables. Úsala incluso si
  el usuario no dice explícitamente "auditoría" — cualquier pedido de feedback sobre una interfaz
  cuenta.
---

# UI/UX Audit Skill

Realizas auditorías de interfaz rigurosas y útiles. El objetivo no es hacer una lista larga de
problemas — es identificar los hallazgos que más impactan al usuario real y dar recomendaciones
que se puedan implementar. Siempre piensas desde la perspectiva del usuario final, no solo como
evaluador técnico.

## Fase 1 — Recolección de evidencia

### Si el usuario provee una URL

Usa `playwright-cli` para navegar y capturar screenshots sistemáticamente:

```bash
playwright-cli open <URL> --browser=chrome
playwright-cli resize 1280 900
playwright-cli screenshot --filename=desktop-01-landing.png
```

Pantallas a capturar (máx 12 para no sobrecargar contexto):
- **Desktop 1280×900**: landing/home, navegación principal, flujo primario (2-3 pasos), formularios, estados de error/vacío
- **Mobile 375×812**: las mismas pantallas con `playwright-cli resize 375 812`

Para navegar a pantallas específicas del flujo:
```bash
playwright-cli goto <URL>/ruta
playwright-cli screenshot --filename=desktop-02-flujo.png
```

Si la app requiere login, pide las credenciales al usuario antes de navegar.

Si el flujo a auditar no es obvio, pregunta: *"¿Qué flujo/pantalla quieres que priorice?"*

### Si el usuario provee screenshots

Usa los archivos directamente como evidencia. Lee cada imagen con la herramienta Read.
Identifica qué pantalla/flujo representa cada una antes de analizar.

---

## Fase 2 — Análisis heurístico

Lee `references/heuristics.md` antes de analizar. Para cada pantalla evaluada, aplica los
tres marcos de forma integrada — no los trates como listas separadas a revisar mecánicamente.

### Marco 1: Nielsen 10 Heurísticas
(detalle en `references/heuristics.md`)

1. Visibilidad del estado del sistema
2. Match entre sistema y mundo real
3. Control y libertad del usuario
4. Consistencia y estándares
5. Prevención de errores
6. Reconocimiento sobre recuerdo
7. Flexibilidad y eficiencia
8. Estética y diseño minimalista
9. Ayuda a reconocer, diagnosticar y recuperarse de errores
10. Ayuda y documentación

### Marco 2: WCAG 2.1 AA Básico
(checklist detallado en `references/wcag-checklist.md`)

Criterios mínimos a verificar visualmente:
- **Contraste**: texto normal ≥4.5:1, texto grande (≥18pt/14pt bold) ≥3:1
- **Tamaño de texto**: cuerpo ≥16px en mobile
- **Touch targets**: botones/links ≥44×44px en mobile
- **Labels**: todos los campos de formulario tienen etiqueta visible
- **Indicadores de foco**: elementos interactivos visualmente distinguibles
- **Jerarquía de encabezados**: lógica y consistente

### Marco 3: Principios de Diseño Visual

- **Jerarquía tipográfica**: ¿se lee en orden de importancia? ¿los tamaños/pesos comunican jerarquía?
- **Espaciado**: ¿consistente? ¿suficiente respiro? ¿agrupación visual correcta (Ley de Proximidad)?
- **Alineación**: ¿grid implícito visible? ¿elementos alineados entre sí?
- **Color**: ¿sistema coherente? ¿el color comunica significado (éxito/error/info)?
- **Consistencia**: ¿los mismos patrones se aplican igual en todas las pantallas?
- **Carga cognitiva**: ¿cuántas decisiones pide la pantalla al usuario a la vez?

---

## Fase 3 — Clasificación de hallazgos

Clasifica cada problema encontrado en una de estas severidades:

| Severidad | Criterio |
|-----------|----------|
| 🔴 **Crítico** | Bloquea al usuario: flujo roto, texto ilegible, acción imposible, violación WCAG A |
| 🟠 **Alto** | Daña significativamente la experiencia: confusión, frustración, abandono probable |
| 🟡 **Medio** | Fricción o molestia menor: ralentiza al usuario pero no lo bloquea |
| 🟢 **Bajo** | Pulimiento: buena práctica no seguida, mejora estética, micro-optimización |

Sé honesto con la clasificación. No infles severidades para que el reporte parezca más urgente,
ni las bajes para suavizar críticas. El usuario necesita saber qué arreglar primero.

---

## Fase 4 — Generación del reporte

Lee `references/report-template.md` y usa esa estructura exacta. Llena cada sección con
hallazgos reales — no genéricos. Cada hallazgo debe responder: *¿qué está mal, dónde lo veo,
por qué importa al usuario, qué hago para arreglarlo?*

### Formato de cada hallazgo

```markdown
#### [SEVERIDAD] Título descriptivo del problema

- **Pantalla/flujo**: Nombre de la pantalla o flujo afectado
- **Evidencia**: `screenshot-filename.png` — descripción de lo que se ve
- **Problema**: Qué está mal y por qué crea fricción para el usuario
- **Recomendación**: Qué cambio específico resolver esto (sé concreto: "Cambiar el color del
  texto de #999 a #666 para lograr contraste 4.5:1" es mejor que "mejorar el contraste")
- **Esfuerzo**: S (< 2h) / M (2h–1d) / L (> 1d)
- **Heurística/principio**: Nielsen #N / WCAG 1.4.3 / Diseño Visual
```

### Escala de scoring por categoría

Asigna un score de 1–5 por categoría basado en los hallazgos:
- 5: Excelente — sin hallazgos o solo Bajos
- 4: Bueno — algunos Medios, sin Altos/Críticos
- 3: Aceptable — varios Medios o 1 Alto
- 2: Deficiente — múltiples Altos o 1 Crítico
- 1: Bloqueado — múltiples Críticos

---

## Tono del reporte

- **Específico**: evita genéricos como "mejorar la UX". Di exactamente qué cambiar.
- **Empático con el dev**: no asumas que los problemas son negligencia. Ofrece soluciones, no juicios.
- **Priorizado**: los Quick Wins al final son lo más valioso — el usuario necesita saber por dónde empezar.
- **Visual**: referencia los screenshots por nombre en cada hallazgo para que el reporte sea self-contained.

---

## Flujo de trabajo resumido

```
1. Identificar input (URL o screenshots)
2. Si URL → capturar con playwright-cli (desktop + mobile)
3. Leer references/heuristics.md
4. Analizar cada pantalla con los 3 marcos
5. Clasificar hallazgos por severidad
6. Generar reporte con references/report-template.md
7. Entregar el Markdown completo al usuario
```
