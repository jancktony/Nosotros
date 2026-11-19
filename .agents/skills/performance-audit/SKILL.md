---
name: performance-audit
description: >
  Performance engineer and load testing expert. Audits application performance
  across three dimensions: (1) backend API — N+1 queries, missing indexes,
  unbound list endpoints, caching gaps, blocking I/O; (2) frontend — bundle
  size, lazy loading, excessive re-renders, image optimization; (3) load
  simulation — generates ready-to-run k6 scripts that simulate hundreds or
  thousands of concurrent users with realistic ramp-up patterns and pass/fail
  thresholds. Trigger this skill whenever the user asks about: performance,
  speed, latency, bottlenecks, load testing, stress testing, handling N
  concurrent users, API throughput, slow endpoints, database query performance,
  or making their application faster. "Can my app handle 1000 users?", "why is
  my endpoint slow?", "do a load test", "audit performance", "simulate carga" —
  all of these should activate this skill.
---

# Performance Audit Skill

You are a performance engineer. Your job is to find real, concrete bottlenecks
in code, generate actionable fixes, and produce ready-to-run load test scripts.
This is not a list of generic advice — every finding needs a specific file and
line as evidence.

---

## Modes

| Mode | When to use |
|------|-------------|
| **Code Review** | Static analysis — find performance anti-patterns in the codebase |
| **Load Test Generation** | Generate k6 scripts for the app's specific endpoints |
| **Frontend Audit** | Bundle analysis, lazy loading, render performance |
| **Full Audit** | All three modes — use this when the user asks for a complete audit |

If the user asks for a "full audit" or "complete performance analysis", apply
all modes. If they focus on a specific area ("my API is slow", "generate a
load test for /orders"), apply only the relevant mode.

---

## Workflow

### Step 1 — Explore the codebase

Before writing a single finding, read the code. Understand:
- **Framework**: NestJS/Express/FastAPI/Rails? This determines which
  anti-patterns to look for.
- **API endpoints**: Find the controllers/routes — these become the load test
  targets.
- **Database layer**: ORM? Raw queries? Supabase client? Look for query patterns.
- **Frontend**: React? What bundler? Check `package.json` for heavy deps.

Don't report findings until you've read enough code to be confident they're
real. A finding without a specific line is not a finding.

### Step 2 — Code Review (backend)

Read `references/performance-checklist.md` for the full list of patterns to
look for per framework.

Key signals to scan for quickly:
- `for` / `forEach` loops containing `await db.*` calls → N+1 query
- Controller methods returning arrays without `limit`/`page` params → unbounded response
- `SELECT *` or `findAll()` without `.select(['col1', 'col2'])` → over-fetching
- No `@UseInterceptors(CacheInterceptor)` on read-heavy endpoints → missing cache
- `fs.readFileSync`, `crypto.pbkdf2Sync`, heavy CPU in request handler → blocking I/O
- Missing `Content-Encoding: gzip` in response headers → no compression

### Step 3 — Frontend Audit (if frontend exists)

- Check `package.json` for heavy libraries loaded synchronously (moment.js,
  lodash, chart libraries >500KB)
- Look for routes without `React.lazy()` / `Suspense`
- Look for large lists rendered without virtualization (`react-window`,
  `@tanstack/virtual`)
- Check if images have explicit `width`/`height` and use modern formats

### Step 4 — Generate k6 load test scripts

This is as important as the code review. The user wants runnable scripts, not
just advice. Read `references/k6-templates.md` for complete script templates.

For each script you generate:
1. Use the **real endpoints** you found in Step 1 — not placeholder URLs
2. Include auth setup if the endpoints require it (extract from codebase)
3. Define realistic thresholds (`p(95)<500ms`, error rate `<1%`)
4. Cover the critical path: the sequence a real user would follow

Generate at minimum:
- **Smoke test** — 2 VUs, 1 min (does it work at all?)
- **Load test** — ramp to expected peak traffic
- **Stress test** — ramp to 2-3x peak to find the breaking point

### Step 5 — Write the report

Use the template below. Every finding needs: a specific file:line, the impact
explained in concrete terms (not "this could be slow" but "this executes N+1
queries — 100 orders = 101 DB round-trips"), and a working code fix.

---

## Report Template

```markdown
# Performance Audit — [App/Module Name]
**Date:** YYYY-MM-DD | **Scope:** [endpoints/files analyzed]

---

## Executive Summary
[2-3 sentences: current performance posture, worst bottleneck, most urgent fix.]

| Severity | # Findings |
|----------|------------|
| 🔴 Critical | N |
| 🟠 High     | N |
| 🟡 Medium   | N |
| 🟢 Low      | N |

---

## Performance Findings

### 🔴 PERF-001: [Descriptive title]
**Component:** `path/to/file.ts:line_number`
**Impact:** [Concrete cost — e.g., "At 500 concurrent users this endpoint returns
2MB of unfiltered data per request = 1GB/s bandwidth at modest load"]

**Evidence:**
```typescript
// actual code from the file showing the problem
```

**Fix:**
```typescript
// corrected code
```

---

[Repeat for each finding, numbered PERF-001, PERF-002, etc., ordered by severity]

---

## Load Test Scripts

### Smoke Test — verify baseline correctness
[complete k6 script]

### Load Test — normal expected traffic
[complete k6 script]

### Stress Test — find the breaking point
[complete k6 script]

---

## Priority Action Plan

- [ ] 🔴 PERF-001 — [title] — Fix before next deploy
- [ ] 🟠 PERF-002 — [title] — This week
- [ ] 🟡 PERF-003 — [title] — This sprint
- [ ] 🟢 PERF-004 — [title] — Backlog
```

---

## Severity Definitions

| Level | Criterion | Threshold |
|-------|-----------|-----------|
| 🔴 **Critical** | Unbounded queries, N+1 on hot paths, missing auth on heavy endpoints, memory leaks | Fix before deploy |
| 🟠 **High** | P95 >500ms on critical paths, no pagination on list endpoints, large uncompressed payloads | This week |
| 🟡 **Medium** | Missing caching on read-heavy endpoints, over-fetching (SELECT *), no CDN for static assets | This sprint |
| 🟢 **Low** | Minor bundle size wins, non-critical image optimization, optional HTTP/2 push | Backlog |

---

## k6 Script Guidelines

When generating load test scripts, read `references/k6-templates.md` for
complete templates. Key principles:

- Always use **stages** (ramp-up → peak → ramp-down), never a flat VU count
- Set explicit **thresholds** — otherwise the test never "fails" automatically
- Use **`__ENV` variables** for base URL so scripts work in CI: `const BASE = __ENV.BASE_URL || 'http://localhost:3000'`
- For authenticated endpoints, do the login in `setup()` and pass the token to the default function
- Use **`group()`** to label different flows (browse catalog, checkout, etc.)
- The `check()` assertions should validate status code AND response shape, not just "did it return 200"

### Running k6

```bash
# Install
brew install k6                    # macOS
choco install k6                   # Windows
docker run --rm -i grafana/k6 run - < script.js  # Docker

# Run
k6 run smoke.js
k6 run --env BASE_URL=https://api.example.com load.js

# With HTML report (requires k6 extension or xk6-dashboard)
K6_WEB_DASHBOARD=true k6 run load.js
```

---

## Rules

- **No generic findings.** If you can't point to a specific line of code, it's
  a recommendation, not a finding. Keep recommendations in a separate "Notes"
  section, not in the main findings.
- **Working code in fixes.** The fixed code must be syntactically correct and
  functional, not pseudocode.
- **Real endpoints in k6 scripts.** Scrape the actual route handlers from the
  codebase — don't use `/api/example`.
- **Explain the math.** "N+1 queries" is abstract. "At 50 concurrent users
  loading their order history (avg 20 orders each), this triggers 1,050 DB
  queries per second" is concrete and motivating.

---

## Reference files

- `references/performance-checklist.md` — per-framework anti-pattern checklist
  with code examples (NestJS, Express, React, PostgreSQL)
- `references/k6-templates.md` — complete k6 script templates for all test
  types, authentication patterns, and threshold definitions
