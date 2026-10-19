# HAWKLYX Edge Service & Coordination Engine

Cloudflare Worker edge service and resilience layer for the HAWKLYX Brent crude oil monitor.

## Architecture & Cross-Isolate Coordination

In a distributed edge environment like Cloudflare Workers, incoming client requests are routed across many independent global edge isolates. In-memory singletons cannot enforce cross-isolate budgets or prevent duplicate upstream queries.

HAWKLYX implements a centralized coordination architecture using a **Cloudflare SQLite Durable Object** (`HawklyxCoordinator`):

```text
Edge Request (Worker Isolate A) ──┐
Edge Request (Worker Isolate B) ──┼──▶ HawklyxCoordinator (Singleton Durable Object)
Edge Request (Worker Isolate C) ──┘        ├── Shared SQLite Persistence (cache, quotas, state)
                                           ├── Atomic 3-minute cooldown enforcement
                                           ├── Global in-flight request coalescing
                                           ├── Multi-account rotation & 401/402/429 quarantine
                                           └── Last-known-good preservation (non-stale/synthetic)
                                                    │ (Only when cache expired & outside cooldown)
                                                    ▼
                                           OilPriceAPI (`GET /prices/latest?by_code=BRENT_CRUDE_USD`)
```

### Key Reliability Guarantees
1. **Cross-Isolate Atomic Quotas**: All Worker isolates route to the deterministic singleton Durable Object (`env.COORDINATOR.getByName("global")`). Quota counts and the global daily budget of 250 requests (110 automatic reserve, 140 manual cap) cannot be exceeded by concurrency across multiple edge datacenters.
2. **Persistent SQLite State**: Quota usage, account quarantine states, cooldown timestamps, and the last known-good price observation survive Worker isolate restarts, redeployments, and edge evictions.
3. **Global Request Coalescing**: If 50 clients tap refresh simultaneously when the cache expires, all 50 await the exact same single in-flight upstream fetch.
4. **Preservation of Last Known-Good Price**: Valid observations with `stale: true` or `synthetic: true` are served with status `stale` but **never overwrite** the last known-good valid observation. If upstream later fails, the last known-good price is preserved.
5. **Enforced Cooldown**: Both forced manual refreshes and ordinary requests respect the 3-minute server-side cooldown.
6. **Zero Secret Leaks**: `/health` outputs sanitized operational health metrics (`slot`, `configured`, `active`, `used_today`, `remaining_estimate`, `quarantine_reason`) without revealing API keys or token fragments.

---

## Configuration & Wrangler Bindings

Configuration is specified in [`wrangler.jsonc`](file:///backend/worker/wrangler.jsonc):

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "hawklyx-backend",
  "main": "src/index.ts",
  "compatibility_date": "2024-10-01",
  "compatibility_flags": ["nodejs_compat"],
  "durable_objects": {
    "bindings": [
      {
        "name": "COORDINATOR",
        "class_name": "HawklyxCoordinator"
      }
    ]
  },
  "migrations": [
    {
      "tag": "v1",
      "new_sqlite_classes": ["HawklyxCoordinator"]
    }
  ],
  "vars": {
    "DEFAULT_UPSTREAM_INTERVAL_MINUTES": "15",
    "MANUAL_REFRESH_COOLDOWN_SECONDS": "180",
    "MAX_DAILY_QUOTA_PER_ACCOUNT": "50",
    "GLOBAL_DAILY_BUDGET": "250",
    "AUTOMATIC_RESERVE_BUDGET": "110",
    "MANUAL_BUDGET_CAP": "140",
    "PRICE_SANITY_MIN": "10",
    "PRICE_SANITY_MAX": "250"
  }
}
```

> **Note on Cloudflare Plan Requirements**: Cloudflare Durable Objects require a Workers Paid subscription on the Cloudflare account when deployed to production.

---

## Secrets Management

Upstream API keys must **NEVER** be committed to Git, embedded in client code, or printed to terminal/chat.

### Development Environment
For local development, copy `.dev.vars.example` to `.dev.vars` (which is git-ignored):
```bash
cp .dev.vars.example .dev.vars
```
Edit `.dev.vars` with your testing keys:
```ini
OILPRICEAPI_KEY_1="your_testing_key_1"
OILPRICEAPI_KEY_2="your_testing_key_2"
```

### Production Deployment Secrets
In production, provision secrets securely using the Wrangler CLI:
```bash
npx wrangler secret put OILPRICEAPI_KEY_1
npx wrangler secret put OILPRICEAPI_KEY_2
npx wrangler secret put OILPRICEAPI_KEY_3
npx wrangler secret put OILPRICEAPI_KEY_4
npx wrangler secret put OILPRICEAPI_KEY_5
```
Wrangler will prompt interactively for each secret value without printing it to shell history.

---

## Local Verification Commands

Run all local validation checks before deploying:

```bash
# 1. Typecheck TypeScript sources
npm run typecheck

# 2. Run all Vitest test suites (69 unit & integration tests)
npm test

# 3. Check dependencies and security vulnerabilities
npm audit

# 4. Validate bundling, Wrangler schema, bindings, and DO migrations
npm run build
```

---

## Production Deployment Command

Once secrets have been set, deploy to Cloudflare:
```bash
npm run deploy
```
This deploys the Worker script along with the `v1` Durable Object SQLite migration.
