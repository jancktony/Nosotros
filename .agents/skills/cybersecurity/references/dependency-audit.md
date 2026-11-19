# Dependency Audit — CVEs en npm/pnpm

## Comandos para ejecutar

```bash
# Con pnpm (proyecto usa pnpm)
pnpm audit

# Con npm
npm audit

# Output JSON para procesar
npm audit --json

# Solo reportar critical y high (útil en CI)
npx audit-ci --moderate

# Ver detalle de un paquete específico
npm info <paquete> | grep -i vulnerabilities
```

---

## Cómo interpretar los resultados

npm/pnpm audit clasifica vulnerabilidades en 4 niveles:

| Nivel | Qué significa | Acción |
|-------|---------------|--------|
| **critical** | Vulnerabilidad explotable remotamente, impacto severo | Parchear inmediatamente antes de próximo deploy |
| **high** | Explotable con ciertas condiciones, impacto significativo | Parchear esta semana |
| **moderate** | Explotable con requisitos adicionales | Parchear este sprint |
| **low** | Impacto limitado o difícil de explotar | Backlog de seguridad |

**Ejemplo de output:**
```
┌─────────────────────────────────────────────────────────┐
│                    npm audit report                     │
│                                                         │
│ jsonwebtoken  <9.0.0                                    │
│ Severity: high                                          │
│ jsonwebtoken vulnerable to signature validation bypass  │
│ fix available via `npm audit fix`                       │
│ node_modules/jsonwebtoken                               │
└─────────────────────────────────────────────────────────┘
```

---

## Cómo resolver cada nivel

### Critical / High — parchear inmediatamente
```bash
# Intentar fix automático (puede romper cosas)
npm audit fix

# Fix forzado (actualiza major versions — revisar breaking changes)
npm audit fix --force

# Fix manual — identificar paquete y actualizar
npm install jsonwebtoken@^9.0.0

# Si es una dependencia transitiva (no directa)
# Agregar override en package.json:
{
  "overrides": {
    "jsonwebtoken": "^9.0.0"
  }
}
```

### Moderate / Low — evaluar y planificar
1. Revisar si el paquete se usa en código accesible externamente
2. Si solo se usa en scripts de build (devDependency), el riesgo es menor
3. Documentar la decisión de diferirlo si aplica

---

## Red flags en package.json

### Versiones permisivas en deps de seguridad crítica
```json
{
  "dependencies": {
    "jsonwebtoken": "^8.5.0",  // ❌ permite actualización minor/patch automática
    "bcrypt": "*",              // ❌ cualquier versión
    "passport": ">=0.4.0"      // ❌ acepta versiones muy antiguas
  }
}
```

Fijar versiones exactas para deps críticos de seguridad:
```json
{
  "dependencies": {
    "jsonwebtoken": "9.0.2",   // ✅ versión exacta
    "bcrypt": "5.1.1"          // ✅ versión exacta
  }
}
```

### devDependencies en producción
```json
// ❌ Si el runtime usa esto pero está en devDependencies
{
  "devDependencies": {
    "dotenv": "^16.0.0"  // riesgo: no se instala en producción
  }
}
```

### Packages conocidos como problemáticos
```json
// ❌ Evitar completamente
"node-serialize": "*",      // deserialización insegura con RCE conocida
"serialize-javascript": "<3.1.0",  // XSS en versiones antiguas
"lodash": "<4.17.21",      // prototype pollution (CVE-2020-8203)
"axios": "<1.6.0",         // CSRF y SSRF vulnerabilities
"express": "<4.19.2"       // path traversal (CVE-2024-29041)
```

---

## Buscar CVEs manualmente

Cuando `npm audit` no encuentra nada pero quieres verificar un paquete específico:

1. **OSV.dev** (Open Source Vulnerabilities): `https://osv.dev/list?ecosystem=npm&q=<paquete>`
2. **Snyk Vulnerability DB**: `https://snyk.io/vuln/npm:<paquete>`
3. **NVD NIST**: `https://nvd.nist.gov/vuln/search?query=<paquete>&form_type=Basic`
4. **GitHub Advisory**: `https://github.com/advisories?query=<paquete>`

---

## Formato para reportar en el Security Report

```markdown
### 🟠 SEC-00X: Dependencia con CVE conocido — [nombre-paquete]
**Componente:** `package.json` (o `frontend/package.json`, `backend/package.json`)
**Riesgo:** [copiar descripción del CVE y qué permite hacer al atacante]

**Evidencia:**
```json
"[paquete]": "^[versión vulnerable]"  // CVE-XXXX-XXXXX
```

**Fix:**
```bash
npm install [paquete]@[versión segura]
```
```json
// package.json actualizado
"[paquete]": "[versión segura]"
```

**Ref:** CVE-XXXX-XXXXX / GHSA-XXXX-XXXX-XXXX
```

---

## Automatizar en CI/CD

Para prevenir que nuevas vulnerabilidades lleguen a producción:

```yaml
# .github/workflows/security.yml
- name: Security audit
  run: |
    cd backend && npm audit --audit-level=high
    cd frontend && npm audit --audit-level=high
```

Si el audit encuentra vulnerabilidades de nivel `high` o superior, el pipeline falla.
