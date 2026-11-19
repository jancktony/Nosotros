# OWASP Top 10 (2021) — Checklist para TypeScript / JavaScript / NestJS

Para cada categoría: qué buscar, señal de alerta en código, ejemplo vulnerable y fix.

---

## A01: Broken Access Control

**Qué buscar:**
- Rutas de API que no tienen guard de autorización (`@UseGuards`)
- Consultas que usan IDs del cliente sin verificar que le pertenecen (IDOR)
- Path traversal en rutas de archivos
- Cambio de roles/permisos aceptado desde el body del request

**Señales:**
```ts
// ❌ IDOR — cualquier usuario puede ver pedidos de otro pasando un orderId ajeno
@Get(':orderId')
getOrder(@Param('orderId') orderId: string) {
  return this.ordersService.findById(orderId); // no verifica que sea del usuario autenticado
}

// ❌ Missing guard — endpoint sin protección
@Post('admin/users/:id/promote')
promoteUser(@Param('id') id: string) { ... }
```

**Fix:**
```ts
// ✅ Verificar ownership
@Get(':orderId')
@UseGuards(JwtAuthGuard)
async getOrder(@Param('orderId') orderId: string, @Request() req) {
  const order = await this.ordersService.findById(orderId);
  if (order.tenantId !== req.user.tenantId) throw new ForbiddenException();
  return order;
}
```

---

## A02: Cryptographic Failures

**Qué buscar:**
- Passwords hasheados con MD5, SHA1, SHA256 sin salt
- Datos sensibles (emails, teléfonos, tokens) almacenados en texto plano
- Secrets transmitidos por HTTP en lugar de HTTPS
- Claves criptográficas hardcodeadas o demasiado cortas

**Señales:**
```ts
// ❌ MD5 para passwords
import { createHash } from 'crypto';
const hash = createHash('md5').update(password).digest('hex');

// ❌ JWT secret débil
jwt.sign(payload, 'secret123');

// ❌ Token en query param (queda en logs de servidor)
GET /api/reset-password?token=abc123
```

**Fix:**
```ts
// ✅ bcrypt para passwords
import * as bcrypt from 'bcrypt';
const hash = await bcrypt.hash(password, 12);

// ✅ JWT con secret fuerte desde env
jwt.sign(payload, process.env.JWT_SECRET, { algorithm: 'HS256', expiresIn: '15m' });
// Mejor: RS256 con keypair para entornos multi-servicio
```

---

## A03: Injection

**Qué buscar:**
- String interpolation en queries SQL o NoSQL
- `exec()`, `spawn()` con input del usuario sin sanitizar
- Queries dinámicas con valores del request sin parámetros preparados

**Señales:**
```ts
// ❌ SQL Injection — Supabase / TypeORM raw query
const result = await supabase.rpc('get_orders', {
  query: `SELECT * FROM orders WHERE tenant_id = '${tenantId}'`
});

// ❌ SQL Injection — TypeORM
const orders = await repo.query(
  `SELECT * FROM orders WHERE status = '${status}'`
);

// ❌ Command injection
import { exec } from 'child_process';
exec(`convert ${req.body.filename} output.pdf`);
```

**Fix:**
```ts
// ✅ Parámetros preparados en TypeORM
const orders = await repo.query(
  'SELECT * FROM orders WHERE status = $1 AND tenant_id = $2',
  [status, tenantId]
);

// ✅ Supabase — usar métodos del cliente, no raw SQL
const { data } = await supabase
  .from('orders')
  .select('*')
  .eq('tenant_id', tenantId);

// ✅ Command injection — evitar exec con input usuario; si es necesario, whitelist estricta
const ALLOWED = ['pdf', 'png', 'jpg'];
if (!ALLOWED.includes(ext)) throw new BadRequestException();
```

---

## A04: Insecure Design

**Qué buscar:**
- Flujos de negocio que no validan estado (ej. pagar un pedido ya cancelado)
- Resets de password sin verificar identidad adecuadamente
- Lógica de precios/descuentos calculada en el cliente y aceptada por el servidor

**Señales:**
```ts
// ❌ El precio lo envía el cliente — puede manipularlo
@Post('checkout')
checkout(@Body() dto: { items: CartItem[]; totalAmount: number }) {
  return this.ordersService.create({ ...dto }); // usa el total del cliente
}
```

**Fix:**
```ts
// ✅ El servidor calcula el precio
@Post('checkout')
checkout(@Body() dto: { items: CartItem[] }) {
  const total = await this.catalogService.calculateTotal(dto.items);
  return this.ordersService.create({ items: dto.items, totalAmount: total });
}
```

---

## A05: Security Misconfiguration

**Qué buscar:**
- CORS con `origin: '*'` en producción
- Stack traces expuestos en errores de producción
- Swagger/OpenAPI expuesto en producción sin autenticación
- Variables de entorno con valores por defecto inseguros

**Señales:**
```ts
// ❌ CORS abierto
app.enableCors({ origin: '*', credentials: true });

// ❌ Swagger en producción
SwaggerModule.setup('api', app, document); // sin protección
```

**Fix:**
```ts
// ✅ CORS con whitelist
app.enableCors({
  origin: process.env.ALLOWED_ORIGINS?.split(',') ?? [],
  credentials: true,
});

// ✅ Swagger solo en desarrollo
if (process.env.NODE_ENV !== 'production') {
  SwaggerModule.setup('api', app, document);
}
```

---

## A06: Vulnerable Components

Ver `references/dependency-audit.md` para el proceso completo de auditoría de dependencias.

Señales rápidas en `package.json`:
```json
// ❌ Versiones permisivas en deps de seguridad crítica
"jsonwebtoken": "^8.0.0",  // permite actualización mayor automática
"bcrypt": "*",             // cualquier versión

// ❌ Paquetes conocidos como problemáticos
"node-serialize": "*",     // deserialización insegura conocida
"eval": "*"                // eval dinámico
```

---

## A07: Identification and Authentication Failures

**Qué buscar:**
- JWT verificado sin especificar `algorithms` (permite downgrade a `none`)
- No hay límite de intentos de login (brute force)
- Tokens sin expiración o con expiración muy larga
- Refresh tokens que no se invalidan al hacer logout

**Señales:**
```ts
// ❌ No especifica algoritmo — permite "alg: none" attack
jwt.verify(token, secret); // acepta cualquier algoritmo

// ❌ Token nunca expira
jwt.sign(payload, secret); // sin expiresIn

// ❌ No hay rate limiting en login
@Post('login')
login(@Body() dto: LoginDto) { ... } // sin throttling
```

**Fix:**
```ts
// ✅ Especificar algoritmo explícitamente
jwt.verify(token, secret, { algorithms: ['HS256'] });

// ✅ Token con expiración corta
jwt.sign(payload, secret, { expiresIn: '15m' });

// ✅ Rate limiting con @nestjs/throttler
@UseGuards(ThrottlerGuard)
@Throttle({ default: { limit: 5, ttl: 60000 } })
@Post('login')
login(@Body() dto: LoginDto) { ... }
```

---

## A08: Software and Data Integrity Failures

**Qué buscar:**
- Deserialización de datos no confiables sin validación
- Scripts cargados desde CDNs externos sin SRI (Subresource Integrity)
- Dependencias descargadas sin lock file

**Señales:**
```ts
// ❌ Deserialización insegura
const obj = JSON.parse(req.body.data); // sin validación de schema
processObject(obj); // puede recibir cualquier forma
```

**Fix:**
```ts
// ✅ Validar con class-validator antes de usar
@Post()
create(@Body() dto: CreateOrderDto) { // ValidationPipe transforma y valida
  return this.service.create(dto);
}
// En main.ts: app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true }));
```

---

## A09: Security Logging and Monitoring Failures

**Qué buscar:**
- Ausencia de logs en acciones críticas (login, cambio de password, acceso a datos sensibles)
- Logs que incluyen passwords, tokens o PII
- No hay alertas para anomalías (múltiples fallos de login, acceso masivo a datos)

**Señales:**
```ts
// ❌ Loguear datos sensibles
console.log('User logged in:', user); // incluye password hash, email
console.log('Token:', jwtToken);

// ❌ Sin log de acciones críticas
async deleteAccount(userId: string) {
  await this.usersRepo.delete(userId); // sin traza de quién y cuándo
}
```

**Fix:**
```ts
// ✅ Loguear evento sin datos sensibles
this.logger.log(`Auth: login success uid=${user.id} tenant=${user.tenantId}`);

// ✅ Audit trail para acciones críticas
async deleteAccount(userId: string, requesterId: string) {
  await this.auditLog.record({ action: 'DELETE_ACCOUNT', targetId: userId, actorId: requesterId });
  await this.usersRepo.delete(userId);
}
```

---

## A10: Server-Side Request Forgery (SSRF)

**Qué buscar:**
- `fetch`, `axios`, o `http.get` con URLs construidas desde input del usuario
- Webhooks que permiten cualquier URL de destino
- Import de archivos desde URLs externas sin validación

**Señales:**
```ts
// ❌ SSRF — el usuario controla la URL
@Post('webhook/test')
async testWebhook(@Body() dto: { url: string }) {
  const result = await axios.get(dto.url); // puede apuntar a metadata de cloud (169.254.169.254)
  return result.data;
}
```

**Fix:**
```ts
// ✅ Whitelist de dominios permitidos
const ALLOWED_DOMAINS = ['api.stripe.com', 'hooks.slack.com'];
const parsed = new URL(dto.url);
if (!ALLOWED_DOMAINS.includes(parsed.hostname)) throw new BadRequestException('URL no permitida');
const result = await axios.get(dto.url);
```
