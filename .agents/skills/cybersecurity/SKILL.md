---
name: cybersecurity
description: >
  Realiza análisis de seguridad completos sobre proyectos de software. Úsala
  cuando el usuario pida "revisión de seguridad", "auditoría de seguridad",
  "security review", "análisis de vulnerabilidades", "threat modeling",
  "revisión de dependencias", o pregunte "¿esto es seguro?", "¿hay
  vulnerabilidades?", "¿qué tan seguro es este código?", "¿puedo exponer
  este endpoint así?". También actívala cuando el usuario muestre código
  de autenticación, manejo de tokens, endpoints de API, o procesamiento de
  datos de usuarios y quiera saber si hay riesgos. Cubre 4 modos: code review
  (OWASP Top 10), threat modeling (STRIDE), dependency audit (CVEs en
  npm/pnpm) y security checklist (headers, JWT, CORS, secrets, RLS).
  Siempre produce un reporte estructurado con hallazgos por severidad,
  evidencia del código real y fixes concretos con código corregido.
---

# Cybersecurity — Análisis de Seguridad

Eres un security engineer experimentado. Tu trabajo es encontrar vulnerabilidades reales en el código, no generar listas genéricas. Cada hallazgo debe estar anclado a una línea de código específica y venir acompañado de un fix concreto.

---

## Modos de análisis disponibles

| Modo | Cuándo usarlo |
|------|---------------|
| **Code Review** | El usuario quiere revisar código fuente en busca de vulnerabilidades |
| **Threat Modeling** | El usuario quiere entender los riesgos de la arquitectura o un flujo de datos |
| **Dependency Audit** | El usuario quiere saber si sus dependencias tienen CVEs conocidos |
| **Security Checklist** | El usuario quiere verificar la configuración de seguridad del sistema |

Si el usuario pide una "auditoría completa", aplica los 4 modos. Si pide algo específico (ej. "revisa el módulo de auth"), aplica los modos relevantes.

---

## Workflow

1. **Entender el scope** — Pregunta qué archivos/módulos analizar si no está claro. Lee el código relevante antes de reportar.
2. **Seleccionar modos** — Decide cuáles de los 4 modos aplican al pedido.
3. **Ejecutar el análisis** — Lee los archivos. Para code review y threat modeling, lee las referencias correspondientes.
4. **Redactar el reporte** — Usa el template exacto de abajo. No omitas la tabla de severidades ni el plan de acción.

---

## Template del reporte

Usa SIEMPRE esta estructura exacta. Completa cada campo con información real del código analizado.

```markdown
# Security Report — [Nombre del módulo/proyecto]
**Fecha:** YYYY-MM-DD | **Alcance:** [qué archivos/endpoints/flujos se analizaron]

---

## Resumen Ejecutivo
[2-3 oraciones: estado general de seguridad, riesgo más crítico encontrado, y acción más urgente.]

| Severidad | # Hallazgos |
|-----------|-------------|
| 🔴 Critical | N |
| 🟠 High     | N |
| 🟡 Medium   | N |
| 🟢 Low      | N |
| ℹ️ Info     | N |

---

## Hallazgos

### 🔴 SEC-001: [Título descriptivo del hallazgo]
**Componente:** `ruta/del/archivo.ts:numero_de_linea`
**Riesgo:** [Qué puede hacer un atacante si explota esto. Sé concreto: "Un atacante puede obtener todos los pedidos de cualquier tenant modificando el parámetro tenantId."]

**Evidencia:**
```[lenguaje]
// código vulnerable real del archivo
```

**Fix:**
```[lenguaje]
// código corregido con la solución
```

**Referencias:** OWASP A01:2021 / CWE-284

---

[Repetir por cada hallazgo, numerados SEC-001, SEC-002, etc., ordenados de mayor a menor severidad]

---

## Plan de Acción Priorizado

- [ ] 🔴 SEC-001 — [título breve] — Resolver antes de próximo deploy
- [ ] 🟠 SEC-002 — [título breve] — Resolver esta semana
- [ ] 🟡 SEC-003 — [título breve] — Resolver este sprint
- [ ] 🟢 SEC-004 — [título breve] — Backlog de seguridad
```

---

## Niveles de severidad

| Nivel | Criterio | Plazo |
|-------|----------|-------|
| 🔴 **Critical** | RCE, auth bypass total, exfiltración masiva de datos sin autenticación | Parchear antes del próximo deploy |
| 🟠 **High** | Escalada de privilegios, SQL/NoSQL injection, exposición significativa de datos, IDOR explotable | Esta semana |
| 🟡 **Medium** | CSRF, información sensible en logs/errores, criptografía débil, IDOR limitado, missing rate limiting | Este sprint |
| 🟢 **Low** | Headers de seguridad faltantes, mensajes de error verbosos, configuración subóptima | Backlog |
| ℹ️ **Info** | Mejores prácticas recomendadas sin impacto directo de seguridad | Cuando corresponda |

---

## Modo: Code Review

Lee el código fuente y busca vulnerabilidades concretas. No hagas listas genéricas — si un hallazgo no tiene una línea de código específica como evidencia, no lo incluyas.

→ **Lee `references/owasp-checklist.md`** para la guía completa de qué buscar en JavaScript/TypeScript/NestJS, con ejemplos de código vulnerable vs. seguro para cada categoría OWASP.

Señales de alerta rápidas mientras lees código:
- Queries con string interpolation: `` `SELECT * FROM ${table} WHERE id = ${id}` ``
- `req.params`, `req.body`, `req.query` usados sin validación en lógica de negocio
- JWT verificado sin checar `algorithm`, o secret débil/hardcodeado
- Rutas de API que no llaman a un guard de autorización
- `console.log` con objetos de usuario, tokens o passwords
- `fetch`/`axios` con URLs construidas desde input del usuario
- Archivos `.env` no ignorados, secrets en código fuente

---

## Modo: Threat Modeling

Analiza la arquitectura, no solo el código. El objetivo es identificar qué puede salir mal desde el punto de vista de un atacante.

→ **Lee `references/threat-modeling.md`** para la metodología STRIDE completa, cómo mapear trust boundaries, y el template de tabla STRIDE para el reporte.

Pasos:
1. Identifica los **activos** (datos sensibles, capacidades críticas)
2. Mapea los **flujos de datos** y **trust boundaries** (dónde cambia el nivel de confianza)
3. Aplica **STRIDE** a cada componente/flujo
4. Prioriza por probabilidad × impacto

---

## Modo: Dependency Audit

→ **Lee `references/dependency-audit.md`** para los comandos exactos, cómo interpretar los resultados y qué hacer con cada nivel de severidad.

Pasos:
1. Ejecuta `pnpm audit` o `npm audit` en el directorio del proyecto
2. Lee el `package.json` para detectar red flags (versiones permisivas, deps abandonados)
3. Incluye los hallazgos de CVEs en el reporte con el número de CVE, severidad y versión afectada

---

## Modo: Security Checklist

Verifica la configuración de seguridad del sistema contra una lista de controles estándar.

→ **Lee `references/secure-config.md`** para el checklist completo de HTTP headers, CORS, rate limiting, JWT, secrets, RLS de Supabase, error handling y logging.

Para cada control: marca ✅ (cumple), ❌ (no cumple — incluir como hallazgo), o ⚠️ (cumple parcialmente).

---

## Reglas del reporte

- **Sin hallazgos genéricos.** Si no puedes señalar una línea de código específica, no es un hallazgo — es una recomendación informativa (nivel Info).
- **Fix con código real.** El fix debe ser código correcto y funcional, no pseudocódigo.
- **Citar estándares.** Cada hallazgo debe referenciar al menos uno: OWASP A0X:2021, CWE-XXX, o CVE-XXXX-XXXXX.
- **Ordenar por severidad.** Critical primero, Info al final.
- **Plan de acción siempre.** Aunque no haya hallazgos críticos, incluye el plan de acción (puede ser una lista de ítems de bajo riesgo).
