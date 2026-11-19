# Performance Anti-Pattern Checklist

## Table of Contents
1. [NestJS / Node.js Backend](#nestjs--nodejs-backend)
2. [React / Vite Frontend](#react--vite-frontend)
3. [PostgreSQL / Supabase Database](#postgresql--supabase-database)
4. [HTTP / Network Layer](#http--network-layer)

---

## NestJS / Node.js Backend

### N+1 Query Problem
The most common and impactful backend performance issue. Happens when a
loop sends one DB query per iteration instead of a single batched query.

```typescript
// BAD — N+1: one query per order item
async getOrders(userId: string) {
  const orders = await this.orderRepo.find({ where: { userId } });
  for (const order of orders) {
    order.items = await this.itemRepo.find({ where: { orderId: order.id } });
  }
  return orders;
}

// GOOD — single query with JOIN
async getOrders(userId: string) {
  return this.orderRepo.find({
    where: { userId },
    relations: ['items'],    // TypeORM eager load
  });
}

// GOOD — Supabase equivalent
const { data } = await supabase
  .from('orders')
  .select('*, items(*)')
  .eq('user_id', userId);
```

**How to detect:** Look for `await db.*` inside `for`, `forEach`, or `map`
inside a service method that's called for each item in a list.

---

### Unbounded List Endpoints
Returning all rows from a table is a ticking time bomb. At 10,000 rows it
causes 10s+ response times and OOM errors.

```typescript
// BAD — returns entire table
async findAll() {
  return this.orderRepo.find();
}

// GOOD — cursor-based or offset pagination
async findAll(page = 1, limit = 20) {
  return this.orderRepo.find({
    skip: (page - 1) * limit,
    take: limit,
    order: { createdAt: 'DESC' },
  });
}
```

**How to detect:** Service methods named `findAll`, `getAll`, `list*` that
return arrays without `take`/`limit`/`top` parameters.

---

### Over-Fetching (SELECT *)
Fetching all columns when only a few are needed wastes DB I/O and network
bandwidth, especially on tables with large JSON or text columns.

```typescript
// BAD — fetches all columns including large blob fields
const orders = await this.orderRepo.find({ where: { userId } });

// GOOD — select only what you need
const orders = await this.orderRepo.find({
  where: { userId },
  select: ['id', 'status', 'totalAmount', 'createdAt'],
});
```

---

### Missing Response Caching
High-read, low-write data (catalogs, settings, public profiles) is perfect
for caching. Without it, the same DB query runs on every request.

```typescript
// BAD — DB hit on every catalog request
@Get('catalog')
async getCatalog(@Param('tenantId') tenantId: string) {
  return this.catalogService.findAll(tenantId);
}

// GOOD — NestJS Cache with TTL
@Get('catalog')
@UseInterceptors(CacheInterceptor)
@CacheTTL(60_000) // 60 seconds
async getCatalog(@Param('tenantId') tenantId: string) {
  return this.catalogService.findAll(tenantId);
}
```

---

### Blocking I/O in Request Handler
`Sync` variants of Node.js APIs block the event loop for ALL concurrent
requests. Even one call per request kills throughput.

```typescript
// BAD — blocks event loop
import { readFileSync } from 'fs';
async processUpload(file: string) {
  const content = readFileSync(file, 'utf8'); // BLOCKS ALL REQUESTS
  return this.parse(content);
}

// GOOD — async
import { readFile } from 'fs/promises';
async processUpload(file: string) {
  const content = await readFile(file, 'utf8');
  return this.parse(content);
}
```

Also watch for: `JSON.parse` on very large payloads in hot paths, bcrypt with
high rounds in non-auth endpoints, synchronous regex on large strings.

---

### Missing Compression
Without gzip/brotli, JSON responses can be 5-10x larger than necessary.
A 200KB JSON response compresses to ~20KB.

```typescript
// main.ts — add compression middleware
import * as compression from 'compression';

app.use(compression());
// Install: pnpm add compression @types/compression
```

---

### Missing Database Connection Pool Sizing
TypeORM's default pool is 10 connections. Under load, requests queue waiting
for a free connection. This manifests as latency spikes at moderate concurrency.

```typescript
// ormconfig or TypeOrmModule options
{
  type: 'postgres',
  // ...
  extra: {
    max: 20,          // pool size — tune based on DB server limits
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 2_000,
  },
}
```

---

## React / Vite Frontend

### No Route-Level Code Splitting
Loading the entire app bundle on first load adds 100-500ms to Time to
Interactive. Each route should be a separate chunk loaded on demand.

```typescript
// BAD — all routes in one bundle
import { OrdersPage }   from './features/orders/OrdersPage';
import { CatalogPage }  from './features/catalog/CatalogPage';
import { SettingsPage } from './features/settings/SettingsPage';

// GOOD — lazy loaded splits
const OrdersPage   = React.lazy(() => import('./features/orders/OrdersPage'));
const CatalogPage  = React.lazy(() => import('./features/catalog/CatalogPage'));
const SettingsPage = React.lazy(() => import('./features/settings/SettingsPage'));

// Wrap router with Suspense:
<Suspense fallback={<PageSpinner />}>
  <Routes>...</Routes>
</Suspense>
```

---

### Heavy Libraries Not Tree-Shaken or Split
Some libraries are very large. Check with `vite build --report` or
`npx vite-bundle-analyzer`.

Common offenders:
- `moment.js` — 290KB. Replace with `date-fns` (tree-shakable) or `dayjs`
- `lodash` — 70KB full. Use `lodash-es` with named imports
- Chart libraries loaded globally — load only on the chart page
- `@supabase/supabase-js` — generally fine but ensure it's not duplicated

```typescript
// BAD
import _ from 'lodash';
const sorted = _.sortBy(items, 'name');

// GOOD — tree-shaken, only imports sortBy (~3KB)
import { sortBy } from 'lodash-es';
const sorted = sortBy(items, 'name');
```

---

### Unvirtualized Long Lists
Rendering 500+ items in a list creates 500+ DOM nodes, causing jank.
Use virtualization to render only the visible items.

```typescript
// BAD — renders all 500 orders
{orders.map((o) => <OrderCard key={o.id} order={o} />)}

// GOOD — @tanstack/virtual renders only ~10 visible at a time
import { useVirtualizer } from '@tanstack/react-virtual';

const virtualizer = useVirtualizer({
  count: orders.length,
  getScrollElement: () => parentRef.current,
  estimateSize: () => 80,
});
```

---

### Missing Image Optimization
Unoptimized images are often the largest assets on a page.

```html
<!-- BAD -->
<img src="photo.jpg" />

<!-- GOOD — explicit dimensions prevent layout shift, lazy load off-screen -->
<img
  src="photo.webp"
  width="400"
  height="300"
  loading="lazy"
  decoding="async"
  alt="Product photo"
/>
```

---

## PostgreSQL / Supabase Database

### Missing Indexes on Filtered Columns
A table with 100K rows and no index on the WHERE column does a full
sequential scan — this scales as O(n) with table size.

```sql
-- Check for sequential scans on large tables:
EXPLAIN ANALYZE
SELECT * FROM orders WHERE tenant_id = 'abc' ORDER BY created_at DESC LIMIT 20;

-- If output shows "Seq Scan" on a large table, add an index:
CREATE INDEX CONCURRENTLY idx_orders_tenant_created
  ON orders (tenant_id, created_at DESC);
-- CONCURRENTLY avoids locking the table during creation
```

**Common columns that almost always need indexes:**
- `tenant_id` (multi-tenant apps — every query filters by this)
- `user_id` / `customer_id`
- `status` (if frequently filtered)
- `created_at` (if ORDER BY createdAt is common)
- Foreign keys that are used in JOINs

---

### N+1 in Supabase RLS Policies
Row-Level Security policies that call a function per row create N function
calls. This is often invisible in development (small data) but catastrophic
in production.

```sql
-- BAD — function called once per row
CREATE POLICY "tenant_isolation" ON orders
  USING (tenant_id = get_tenant_id_from_session());

-- GOOD — inline expression or use auth.jwt() which is evaluated once
CREATE POLICY "tenant_isolation" ON orders
  USING (tenant_id = (auth.jwt() ->> 'tenant_id'));
```

---

### Missing `LIMIT` in Raw Queries
Even if the application layer paginates, always add LIMIT in the DB query
itself as a safety net.

```sql
-- BAD
SELECT * FROM orders WHERE tenant_id = $1 ORDER BY created_at DESC;

-- GOOD
SELECT id, status, total_amount, created_at
FROM orders
WHERE tenant_id = $1
ORDER BY created_at DESC
LIMIT 50;
```

---

## HTTP / Network Layer

### Missing Compression
Ensure the server returns `Content-Encoding: gzip` or `br` for JSON responses.
Test with: `curl -H "Accept-Encoding: gzip" -I https://api.example.com/v1/catalog`

### No Cache-Control Headers on Static Assets
Vite bundles assets with content hashes — these can be cached forever.

```nginx
# nginx config
location /assets/ {
  add_header Cache-Control "public, max-age=31536000, immutable";
}
```

### API Responses Missing ETags
For resources that don't change often, ETags allow conditional GET requests
that return `304 Not Modified` with no body, saving bandwidth.

```typescript
// NestJS — add ETag support
import { CacheModule } from '@nestjs/cache-manager';
// Or manually set ETag header based on content hash
res.setHeader('ETag', `"${hashContent(responseBody)}"`);
```
