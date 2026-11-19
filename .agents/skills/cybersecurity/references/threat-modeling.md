# Threat Modeling — Metodología STRIDE

## Proceso en 4 pasos

### Paso 1: Identificar activos
Qué necesita protección:
- **Datos sensibles**: credentials, tokens JWT, datos PII (emails, teléfonos), datos de pago
- **Capacidades críticas**: autenticación, autorización, pagos, acceso a datos de otros tenants
- **Infraestructura**: base de datos, servicios externos (Supabase, Stripe, WhatsApp API)

### Paso 2: Mapear flujos de datos y trust boundaries
Un trust boundary es donde los datos cruzan entre contextos de confianza distinta:
- Cliente (browser/app) → API backend
- API backend → base de datos
- API backend → servicios externos
- Proceso worker → base de datos

Dibuja o describe los flujos principales:
```
[Usuario] --HTTPS--> [API NestJS] --Supabase client--> [PostgreSQL]
                          |
                     [Supabase Realtime] <-- suscripciones WebSocket
                          |
                     [Servicios externos: Stripe, WhatsApp, Groq]
```

### Paso 3: Aplicar STRIDE por componente

Para cada componente y cada flujo de datos, evalúa las 6 amenazas:

| Amenaza | Pregunta | Ejemplo |
|---------|----------|---------|
| **S**poofing | ¿Puede alguien hacerse pasar por otro usuario/servicio? | JWT robado, impersonación de otro tenant |
| **T**ampering | ¿Puede alguien modificar datos en tránsito o en reposo? | Manipular payload del JWT, cambiar precios en el body |
| **R**epudiation | ¿Puede alguien negar haber realizado una acción? | Sin audit log, imposible saber quién canceló un pedido |
| **I**nformation Disclosure | ¿Puede alguien acceder a datos que no debería ver? | IDOR en pedidos, error messages con stack trace |
| **D**enial of Service | ¿Puede alguien interrumpir el servicio? | Sin rate limiting en endpoints costosos, queries sin límite |
| **E**levation of Privilege | ¿Puede alguien obtener más permisos de los que tiene? | Cambio de rol desde el body, JWT sin validación de role claim |

### Paso 4: Priorizar riesgos

Usa la matriz probabilidad × impacto:

| | Impacto Bajo | Impacto Medio | Impacto Alto |
|---|---|---|---|
| **Probabilidad Alta** | 🟡 Medium | 🟠 High | 🔴 Critical |
| **Probabilidad Media** | 🟢 Low | 🟡 Medium | 🟠 High |
| **Probabilidad Baja** | ℹ️ Info | 🟢 Low | 🟡 Medium |

---

## Template de tabla STRIDE para el reporte

Incluye esta tabla en el reporte cuando hagas threat modeling:

```markdown
## Threat Model — [Componente/Flujo]

| ID | Componente | Amenaza STRIDE | Descripción | Severidad | Control Actual | Recomendación |
|----|------------|----------------|-------------|-----------|----------------|---------------|
| TM-001 | API /auth/login | Spoofing | Brute force de credenciales | 🟠 High | Ninguno | Rate limiting 5 req/min |
| TM-002 | JWT | Tampering | Secret débil permite forjar tokens | 🔴 Critical | Secret en .env | Rotar secret, usar RS256 |
| TM-003 | GET /orders/:id | I. Disclosure | IDOR: acceso a pedidos de otros tenants | 🟠 High | Parcial (tenant en query) | Verificar ownership explícito |
```

---

## Ejemplo aplicado: API REST con JWT + Supabase

### Flujo de autenticación
```
[Browser] --POST /auth/login--> [NestJS] --Supabase Auth--> [PostgreSQL auth.users]
                                    |
                               [JWT generado]
                                    |
[Browser] <-- JWT (localStorage/cookie) <-- [NestJS]
```

**Amenazas en este flujo:**

| Amenaza | Riesgo | Control |
|---------|--------|---------|
| **S** Spoofing | Credentials robadas por phishing | MFA, alerta de nuevo dispositivo |
| **T** Tampering | JWT modificado para cambiar role/tenantId | Firma JWT con secret fuerte, verificar `alg` |
| **I** Info Disclosure | Error de login revela si el email existe | Respuesta genérica "email o password incorrectos" |
| **D** DoS | Flood de requests a /auth/login | Rate limiting + captcha |
| **E** Elevation | Role claim manipulado en payload | Validar role contra DB en cada request crítico |

### Flujo de pedidos multi-tenant
```
[Admin Tenant A] --> GET /orders --> [NestJS] --> SELECT * FROM orders WHERE tenant_id = ?
```

**Amenazas:**

| Amenaza | Riesgo | Control |
|---------|--------|---------|
| **S** Spoofing | Token de otro tenant | JWT incluye tenant_id en claim |
| **T** Tampering | Modificar tenantId en query param | Extraer tenant_id solo del JWT verificado |
| **I** Info Disclosure | IDOR vía orderId sin verificar ownership | Verificar `order.tenantId === jwt.tenantId` |
| **D** DoS | Query sin LIMIT devuelve todos los pedidos | Paginación obligatoria, límite máximo de page size |

---

## Trust Boundaries críticas en proyectos web modernos

1. **Browser → API**: Nunca confiar en datos del cliente. Validar todo en servidor.
2. **API → DB**: Usar mínimo privilegio. El service role de Supabase no debería ir al cliente.
3. **API → Servicios externos**: Timeout en todas las llamadas. No exponer errores internos de terceros.
4. **Variables de entorno**: Separar secrets de configuración. Rotarlos periódicamente.
5. **Realtime/WebSocket**: Verificar autorización en la suscripción, no solo en el handshake inicial.
