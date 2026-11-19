# k6 Load Test Templates

## Table of Contents
1. [Smoke Test](#smoke-test)
2. [Load Test (ramp-up)](#load-test)
3. [Stress Test](#stress-test)
4. [Spike Test](#spike-test)
5. [Soak Test](#soak-test)
6. [Authenticated Flows](#authenticated-flows)
7. [Threshold Reference](#threshold-reference)

---

## Smoke Test

Purpose: verify the system works at all before running heavier tests. Run this
first — if it fails, don't bother with load or stress.

```javascript
// smoke.js — 2 VUs, 1 minute
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  vus: 2,
  duration: '1m',
  thresholds: {
    http_req_failed:   ['rate<0.01'],       // <1% errors
    http_req_duration: ['p(95)<2000'],      // lenient for smoke
  },
};

export default function () {
  const res = http.get(`${BASE}/api/v1/health`);
  check(res, {
    'status 200':        (r) => r.status === 200,
    'response not empty': (r) => r.body.length > 0,
  });
  sleep(1);
}
```

---

## Load Test

Purpose: simulate the normal expected peak traffic. Replace the endpoint list
and VU targets with what's appropriate for the specific application.

```javascript
// load.js — ramp to target concurrent users, hold, ramp down
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '2m',  target: 50  },  // ramp up to 50 VUs over 2 min
    { duration: '5m',  target: 50  },  // stay at 50 VUs for 5 min
    { duration: '2m',  target: 100 },  // ramp up to 100 VUs
    { duration: '5m',  target: 100 },  // hold at peak
    { duration: '1m',  target: 0   },  // ramp down
  ],
  thresholds: {
    http_req_failed:        ['rate<0.01'],   // <1% errors
    http_req_duration:      ['p(95)<500'],   // 95th percentile <500ms
    'http_req_duration{endpoint:catalog}': ['p(95)<300'],
    'http_req_duration{endpoint:orders}':  ['p(95)<500'],
  },
};

export default function () {
  group('browse catalog', () => {
    const r = http.get(`${BASE}/api/v1/catalog`, {
      tags: { endpoint: 'catalog' },
    });
    check(r, {
      'catalog 200':      (r) => r.status === 200,
      'catalog has items': (r) => JSON.parse(r.body).length > 0,
    });
    sleep(1);
  });

  group('view order', () => {
    // Use a real order ID from your test data
    const r = http.get(`${BASE}/api/v1/orders/TEST_ORDER_ID`, {
      tags: { endpoint: 'orders' },
    });
    check(r, { 'order 200': (r) => r.status === 200 });
    sleep(2);
  });

  sleep(Math.random() * 2); // simulate think time between actions
}
```

---

## Stress Test

Purpose: find the breaking point. Start where the load test ended and push
beyond. Watch where errors spike and latency degrades.

```javascript
// stress.js — push past normal capacity to find limits
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '2m',  target: 100 },  // normal load
    { duration: '5m',  target: 100 },  // hold
    { duration: '2m',  target: 200 },  // start stressing
    { duration: '5m',  target: 200 },  // hold at stress
    { duration: '2m',  target: 300 },  // push harder
    { duration: '5m',  target: 300 },  // hold — watch error rate here
    { duration: '5m',  target: 0   },  // recovery — does it recover?
  ],
  thresholds: {
    // For stress tests, we expect some degradation — so thresholds are
    // informational (abortOnFail: false) rather than gates
    http_req_failed:   [{ threshold: 'rate<0.10', abortOnFail: false }],
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: false }],
  },
};

export default function () {
  const r = http.get(`${BASE}/api/v1/catalog`);
  check(r, { 'status ok': (r) => r.status < 500 });
  sleep(1);
}
```

---

## Spike Test

Purpose: simulate a sudden burst (flash sale, viral post, marketing email sent).
No ramp-up — jump straight to 10x normal traffic.

```javascript
// spike.js — sudden surge of users
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '10s', target: 10  },  // baseline
    { duration: '1m',  target: 10  },  // hold baseline
    { duration: '10s', target: 500 },  // SPIKE — instantaneous burst
    { duration: '3m',  target: 500 },  // hold the spike
    { duration: '10s', target: 10  },  // drop back
    { duration: '3m',  target: 10  },  // verify recovery
    { duration: '10s', target: 0   },
  ],
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: false }],
  },
};

export default function () {
  const r = http.get(`${BASE}/api/v1/catalog`);
  check(r, { 'status ok': (r) => r.status < 500 });
  sleep(1);
}
```

---

## Soak Test

Purpose: detect memory leaks and resource degradation over time. Same load as
normal, but run for 1-2 hours. If P95 latency creeps up over time, there's
a leak or a resource exhaustion.

```javascript
// soak.js — sustained load to detect leaks (run for 1-2 hours)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '5m',  target: 50 },   // ramp up
    { duration: '1h',  target: 50 },   // hold — watch for P95 drift upward
    { duration: '5m',  target: 0  },   // ramp down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const r = http.get(`${BASE}/api/v1/catalog`);
  check(r, { 'status 200': (r) => r.status === 200 });
  sleep(2);
}
```

---

## Authenticated Flows

Most real-world APIs require auth. Use `setup()` to log in once and share the
token with all VUs. This is more realistic than logging in per request.

### Cookie-based auth (httpOnly cookie — like Putchi's customer flow)

```javascript
// auth-cookie.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CookieJar } from 'k6/http';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 50 },
    { duration: '1m', target: 0  },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  // Authenticate and capture the session cookie
  const jar = new CookieJar();
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: __ENV.TEST_EMAIL, password: __ENV.TEST_PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      cookieJar: jar,
    },
  );
  check(res, { 'login ok': (r) => r.status === 200 });
  // Return cookies as string for VUs to reuse
  return { cookies: jar.cookiesForURL(`${BASE}/`) };
}

export default function (data) {
  const jar = new CookieJar();
  // Re-inject the session cookie from setup
  data.cookies.forEach((c) => {
    jar.set(`${BASE}/`, c.name, c.value);
  });

  const res = http.get(`${BASE}/api/v1/customers/me`, { cookieJar: jar });
  check(res, { 'profile 200': (r) => r.status === 200 });
  sleep(1);
}
```

### JWT Bearer token auth

```javascript
// auth-jwt.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:3000';

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 50 },
    { duration: '1m', target: 0  },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: __ENV.TEST_EMAIL, password: __ENV.TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'login ok': (r) => r.status === 200 });
  const { access_token } = JSON.parse(res.body);
  return { token: access_token };
}

export default function (data) {
  const headers = {
    'Authorization': `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  const r = http.get(`${BASE}/api/v1/orders`, { headers });
  check(r, {
    'orders 200':        (r) => r.status === 200,
    'orders is array':   (r) => Array.isArray(JSON.parse(r.body)),
  });
  sleep(1);
}
```

---

## Threshold Reference

```javascript
// Recommended thresholds per endpoint criticality

// Critical path (checkout, auth):
'http_req_duration{endpoint:checkout}': ['p(95)<1000', 'p(99)<2000'],

// Standard CRUD:
'http_req_duration{endpoint:orders}': ['p(95)<500'],

// Read-heavy (catalog, search):
'http_req_duration{endpoint:catalog}': ['p(95)<300'],

// Error rates:
http_req_failed: ['rate<0.01'],  // <1% errors is the gold standard
                                  // <0.1% for financial transactions
                                  // <5% is acceptable during stress tests

// Throughput (if you know your RPS target):
http_reqs: ['rate>100'],  // must achieve at least 100 RPS
```

### Running with env vars

```bash
k6 run \
  --env BASE_URL=https://staging.myapp.com \
  --env TEST_EMAIL=loadtest@myapp.com \
  --env TEST_PASSWORD=testpass123 \
  load.js
```

### Interpreting results

k6 prints a summary at the end. Key things to read:
- `http_req_duration` — look at `p(90)` and `p(95)`. If avg is 100ms but p(95)
  is 2000ms, you have tail latency spikes (usually DB lock contention or GC).
- `http_req_failed` — should be near 0%. Any errors above 0.1% warrant
  investigation.
- `iterations` — total completed requests. Divide by duration for RPS.
- `vus_max` — confirms you actually reached the target concurrency.
- If thresholds are printed in red with `✗`, the test "failed" — your
  performance goals were not met.
