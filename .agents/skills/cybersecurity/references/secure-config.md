# Security Checklist — Configuración Segura

Para cada control: ✅ cumple / ❌ no cumple (incluir como hallazgo) / ⚠️ cumple parcialmente.

---

## HTTP Security Headers

Verificar en la respuesta del servidor (usar `curl -I https://tudominio.com` o DevTools → Network).

| Header | Valor recomendado | Riesgo si falta |
|--------|-------------------|-----------------|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'nonce-{random}'` | XSS |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Downgrade a HTTP |
| `X-Frame-Options` | `DENY` o `SAMEORIGIN` | Clickjacking |
| `X-Content-Type-Options` | `nosniff` | MIME type sniffing |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Leak de URLs internas |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | Acceso a hardware sin necesidad |

**Implementar en NestJS:**
```ts
// main.ts
import helmet from 'helmet';
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"], // ajustar según el framework de CSS
    },
  },
  hsts: { maxAge: 31536000, includeSubDomains: true },
}));
```

---

## CORS

```ts
// ❌ Abierto — permite requests de cualquier origen
app.enableCors({ origin: '*', credentials: true });
// Nota: origin: '*' con credentials: true falla en browsers por spec CORS,
// pero igual es un indicador de configuración incorrecta

// ✅ Whitelist explícita
const ALLOWED = (process.env.ALLOWED_ORIGINS ?? '').split(',').filter(Boolean);
app.enableCors({
  origin: (origin, callback) => {
    if (!origin || ALLOWED.includes(origin)) callback(null, true);
    else callback(new Error('CORS: origin not allowed'));
  },
  credentials: true,
  methods: ['GET', 'POST', 'PATCH', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization'],
});
```

---

## Rate Limiting

Endpoints que requieren rate limiting obligatorio:

| Endpoint | Límite recomendado | Motivo |
|----------|--------------------|--------|
| `POST /auth/login` | 5 req/min por IP | Brute force de passwords |
| `POST /auth/register` | 3 req/min por IP | Spam de cuentas |
| `POST /auth/forgot-password` | 3 req/min por IP | Enumeración de emails |
| `POST /orders` | 30 req/min por usuario | Abuso de creación de pedidos |
| API pública (`/api/*`) | 100 req/min por IP | DoS |

**Implementar con @nestjs/throttler:**
```ts
// app.module.ts
ThrottlerModule.forRoot([{ ttl: 60000, limit: 100 }])

// En el controller de auth (más restrictivo)
@UseGuards(ThrottlerGuard)
@Throttle({ default: { limit: 5, ttl: 60000 } })
@Post('login')
```

---

## JWT Configuration

| Control | Estado ideal |
|---------|-------------|
| Algoritmo | HS256 mínimo; RS256 preferido para multi-servicio |
| Expiración access token | 15 minutos |
| Expiración refresh token | 7-30 días |
| Almacenamiento en cliente | httpOnly cookie (no localStorage) |
| Secret strength | Mínimo 32 bytes aleatorios |
| Validar `alg` en verify | Sí — previene downgrade a `alg: none` |

```ts
// ❌ Configuración insegura
jwt.sign(payload, 'secret', { expiresIn: '30d' }); // secret débil, expiración larga
jwt.verify(token, secret); // no especifica algoritmo

// ✅ Configuración segura
const ACCESS_TOKEN_EXPIRY = '15m';
const REFRESH_TOKEN_EXPIRY = '7d';

jwt.sign(payload, process.env.JWT_SECRET, {
  algorithm: 'HS256',
  expiresIn: ACCESS_TOKEN_EXPIRY,
  issuer: 'putchi-api',
});

jwt.verify(token, process.env.JWT_SECRET, {
  algorithms: ['HS256'],
  issuer: 'putchi-api',
});
```

**JWT Secret generation:**
```bash
# Generar secret seguro
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

---

## Secrets y Variables de Entorno

**Checklist:**
- [ ] `.env` está en `.gitignore` (verificar con `git check-ignore .env`)
- [ ] No hay secrets hardcodeados en el código (`grep -r "password\|secret\|api_key" --include="*.ts" --include="*.js" src/`)
- [ ] Los secrets de producción están en el gestor de secrets del proveedor (Supabase Vault, GitHub Actions Secrets, etc.)
- [ ] El `.env.example` no contiene valores reales, solo placeholders
- [ ] Las variables de entorno tienen validación al inicio de la app (fallar rápido si falta algo crítico)

```ts
// ✅ Validación de env al inicio (NestJS con class-validator)
// env.validation.ts
class EnvironmentVariables {
  @IsNotEmpty() JWT_SECRET: string;
  @IsNotEmpty() SUPABASE_URL: string;
  @IsNotEmpty() SUPABASE_SERVICE_KEY: string;
}

// app.module.ts
ConfigModule.forRoot({
  validate: (config) => plainToClass(EnvironmentVariables, config),
})
```

---

## Database / Supabase RLS

| Control | Estado ideal |
|---------|-------------|
| RLS habilitado en todas las tablas con datos de tenants | Sí |
| `SUPABASE_SERVICE_KEY` nunca expuesta al cliente | Solo en backend/server |
| `VITE_SUPABASE_ANON_KEY` en frontend | Solo el anon key (limitado por RLS) |
| Políticas RLS verificadas para cada tabla | Sí |
| Acceso directo a DB sin pasar por API | Solo para scripts de migración |

**Verificar exposición del service key:**
```bash
# En el frontend, buscar que SUPABASE_SERVICE_KEY nunca aparezca
grep -r "SUPABASE_SERVICE_KEY\|service_role" frontend/src/
# Si hay resultados → hallazgo crítico
```

**Políticas RLS mínimas para tablas multi-tenant:**
```sql
-- Ejemplo: tabla orders debe tener RLS que filtre por tenant
CREATE POLICY "tenant_isolation" ON orders
  USING (tenant_id = get_tenant_id()); -- función que extrae tenant del JWT

-- Verificar que RLS está habilitado
SELECT schemaname, tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public' AND rowsecurity = FALSE;
-- Si devuelve tablas con datos sensibles → hallazgo
```

---

## Error Handling

```ts
// ❌ Stack trace expuesto en producción
app.useGlobalFilters(new HttpExceptionFilter()); // si el filtro devuelve e.stack

// ❌ Error detallado que revela estructura interna
throw new Error(`Database query failed: ${e.message} at table ${tableName}`);

// ✅ Error genérico al usuario, detalle en logs internos
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const isProduction = process.env.NODE_ENV === 'production';
    const message = isProduction ? 'Internal server error' : String(exception);
    response.status(500).json({ statusCode: 500, message });
    this.logger.error(exception); // detalle solo en logs
  }
}
```

---

## Logging — Qué sí y qué no loguear

**Loguear (✅):**
- Login exitoso: `uid=X tenant=Y ip=Z`
- Login fallido: `email=hash(email) reason=invalid_password`
- Acciones críticas: creación/cancelación de pedidos, cambios de configuración
- Cambios de permisos: `user X granted role Y by admin Z`
- Errores de autorización: `403 attempt uid=X endpoint=Y`

**No loguear (❌):**
- Passwords o hashes de passwords
- Tokens JWT completos (loguear solo el `jti` o primeros 8 chars)
- Datos de tarjeta de crédito (PCI DSS)
- Datos PII completos (teléfonos, emails como valores — usar hash o parcial)
- Query strings completos si contienen tokens (ej. `/reset?token=full_token`)

```ts
// ❌ Loguear datos sensibles
this.logger.log('Login attempt', { user }); // incluye passwordHash, etc.

// ✅ Loguear solo lo necesario
this.logger.log(`Auth: login_success uid=${user.id} ip=${ip}`);
```
