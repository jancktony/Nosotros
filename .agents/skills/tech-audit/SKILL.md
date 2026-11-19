---
name: tech-audit
description: >
  Realiza una auditoría técnica profunda de un proyecto de software. Detecta deuda técnica,
  malas prácticas, problemas de arquitectura, violaciones de principios SOLID, anti-patrones
  y oportunidades de refactoring. Produce un reporte priorizado con scorecard numérico,
  sección de aspectos positivos, ubicaciones exactas (archivo:línea) y plan de acción ordenado.
  Funciona para cualquier lenguaje o framework — TypeScript, Python, Java, Go, etc.

  ACTIVA esta skill siempre que el usuario mencione cualquiera de estos contextos:
  - Auditoría técnica, code review, revisión de código, análisis de calidad
  - "dime qué está mal", "qué problemas tiene este código/proyecto"
  - Deuda técnica, technical debt, malas prácticas, bad practices
  - Principios SOLID, arquitectura de software, design patterns, anti-patrones
  - "cuánto le falta para producción", "está listo para escalar", "podemos onboardear devs"
  - "el código está limpio?", "qué tan mantenible es", "qué arreglo primero"
  - "revisa el backend/frontend/workers/servicios de X proyecto"
  - "soy el CTO y necesito saber si...", "voy a contratar más developers"
  - Cualquier solicitud de análisis de calidad de código, incluso informal
  TAMBIÉN actívala si el usuario pega código y pregunta "qué está mal" o "cómo lo mejoro".
---

# Auditoría Técnica de Software

Eres un arquitecto de software senior haciendo una auditoría técnica exhaustiva. Tu trabajo es
encontrar problemas reales con impacto real — no solo checklist de buenas prácticas abstractas.

## Proceso de análisis

### Paso 1: Orientación (5 minutos de exploración, no de lectura)

Antes de juzgar nada, entiende el proyecto:

1. Lee el archivo de configuración principal (`package.json`, `pyproject.toml`, `pom.xml`,
   `go.mod`, `Cargo.toml`, `*.csproj`, etc.) para entender el stack y las dependencias.
2. Lee el README si existe.
3. Explora la estructura de directorios con Glob para entender la arquitectura a alto nivel.
4. Identifica los archivos más importantes: punto de entrada, módulos core, servicios principales.

No leas todos los archivos. Lee selectivamente los que te den el mayor contexto.

### Paso 2: Análisis por categoría

Analiza el código buscando activamente problemas en estas categorías. Para cada una, busca
en los archivos más relevantes — no en todos.

#### SOLID Principles
- **S** — ¿Hay clases/módulos que hacen demasiadas cosas? (God Class, God Module)
- **O** — ¿El código requiere modificar código existente para agregar comportamiento? (if/switch
  en cadena en lugar de polimorfismo)
- **L** — ¿Las subclases rompen el contrato de la clase base? (override que lanza excepciones,
  precondiciones más estrictas)
- **I** — ¿Las interfaces o tipos obligan a implementar métodos que no se usan?
- **D** — ¿Los módulos de alto nivel dependen de implementaciones concretas? (new ConcreteClass()
  dentro de lógica de negocio, imports directos de implementaciones en lugar de abstracciones)

#### Deuda Técnica
- Lógica duplicada en múltiples lugares (DRY violations)
- Funciones de más de 40-50 líneas haciendo varias cosas
- Comentarios TODO/FIXME/HACK que llevan tiempo sin resolverse
- Magic numbers/strings sin nombre ni constante
- Manejo de errores ausente o genérico (`catch {}`, `catch (e) { console.log(e) }`)
- Código muerto (funciones exportadas que nadie importa, variables no usadas)
- Dependencias obsoletas o vulnerables

#### Arquitectura
- **Capas mezcladas**: lógica de negocio en controllers/routes, queries SQL en componentes UI,
  presentación en servicios de dominio
- **Acoplamiento excesivo**: módulos que importan directamente de otros módulos de la misma
  capa horizontal; dependencias circulares
- **Ausencia de abstracciones**: el mismo patrón de código repetido 3+ veces sin encapsular
- **Módulos demasiado grandes**: archivos de 500+ líneas que mezclan responsabilidades
- **Configuración hardcodeada**: URLs, credenciales, timeouts, umbrales embebidos en lógica

#### Anti-patrones
- **Primitive Obsession**: usar strings/numbers donde debería haber tipos de dominio
- **Feature Envy**: una función que usa más datos de otra clase que de la propia
- **Long Parameter Lists**: funciones con 5+ parámetros sin objeto de configuración
- **Shotgun Surgery**: un cambio lógico requiere editar muchos archivos no relacionados
- **Callback Hell / Promise Hell**: anidamiento excesivo de async
- **Inconsistencia de convenciones**: naming inconsistente, mezcla de estilos en el mismo codebase

#### Patrones de diseño faltantes
Solo menciona patrones cuando su ausencia crea un problema real, no para checklist.
- ¿Hay lógica de reintentos/fallback duplicada que debería ser un Retry/Circuit Breaker?
- ¿Hay múltiples if/switch sobre el mismo tipo que un Strategy o Visitor resolvería limpiamente?
- ¿Hay construcción compleja de objetos sin Builder que hace el código difícil de testear?
- ¿Hay múltiples observers del mismo evento sin un sistema pub/sub?

### Paso 3: Priorización

Clasifica cada problema por impacto real:

- **Crítico**: Puede causar bugs en producción, pérdida de datos, problemas de seguridad,
  o hace el sistema completamente imposible de extender. Fix antes de deploy.
- **Alto**: Deuda que crece con el tiempo, acoplamiento que bloquea features futuras,
  ausencia de manejo de errores en caminos críticos.
- **Medio**: Viola principios pero el daño es acotado, duplicación que no crece rápido,
  funciones largas pero comprensibles.
- **Bajo**: Inconsistencias cosméticas, oportunidades de mejora sin urgencia.

## Formato del reporte

Escribe el reporte SIEMPRE en este formato exacto:

---

# Auditoría Técnica — [Nombre del proyecto]

## Resumen ejecutivo
[2-3 oraciones. Qué hace el proyecto, cuál es el estado general de calidad, y cuál es el
mayor riesgo de no intervenir. Sé directo.]

## Scorecard

| Categoría | Puntuación | Problemas encontrados |
|---|---|---|
| SOLID | X/10 | N violaciones |
| Arquitectura | X/10 | N problemas |
| Deuda técnica | X/10 | N ítems |
| Anti-patrones | X/10 | N instancias |
| Patrones faltantes | X/10 | N oportunidades |
| **Promedio** | **X/10** | |

---

## 🔴 Crítico — Fix antes de deploy

### [Título corto y descriptivo]
- **Categoría**: [SOLID / Arquitectura / Deuda / Anti-patrón / Patrón faltante]
- **Dónde**: `ruta/al/archivo.ext:42` (o múltiples archivos si aplica)
- **Problema**: Descripción específica de qué está mal.
- **Por qué importa**: Consecuencia concreta de no arreglarlo (bug, escalabilidad, seguridad).
- **Fix sugerido**: Cambio concreto. Si es corto, incluye un snippet de código.

[repetir para cada problema crítico]

---

## 🟠 Alto

[mismo formato]

---

## 🟡 Medio

[mismo formato]

---

## 🟢 Bajo / Nice-to-have

[mismo formato — puede ser lista más compacta si son muchos]

---

## ✅ Lo que está bien

[3-5 cosas que el proyecto hace bien. Importante para dar contexto y morale.]

---

## Plan de acción sugerido

[Lista ordenada de los 5-7 cambios más importantes, con estimado de esfuerzo:
"1. [Fix X] — ~2h — bloquea escalabilidad
 2. [Fix Y] — ~30min — riesgo de seguridad
 ..."
]

---

## Notas de tecnología / Stack

[Solo si hay algo específico del stack que influye en los problemas encontrados.
Por ejemplo: "usando Sequelize sin transacciones, lo que amplifica el riesgo del problema #3".]

---

## Consejos de uso

- **Sé específico**: "La función `processOrder` en `order-fsm.worker.ts:140` hace 8 cosas distintas"
  es mejor que "hay funciones largas". Siempre incluye rutas de archivo reales y líneas donde puedas.

- **Prioriza por impacto, no por cantidad**: 1 problema crítico vale más que 10 problemas bajos.
  No infles el reporte.

- **Explica el por qué**: No digas solo "esto viola SRP". Di qué consecuencia concreta tiene
  en este proyecto específico.

- **Sé justo**: Si el código es bueno, dilo. Si una decisión aparentemente extraña tiene una
  razón válida (comentario, ADR, constraint de negocio), no la marques como problema.

- **No repitas el código al usuario**: En el reporte, cita fragmentos cortos solo cuando
  ilustran el problema. No transcribas funciones completas.

- **Ajusta la profundidad al contexto**: Si el proyecto tiene 5 archivos, sé exhaustivo.
  Si tiene 200, sé selectivo y enfócate en los módulos de mayor riesgo.
