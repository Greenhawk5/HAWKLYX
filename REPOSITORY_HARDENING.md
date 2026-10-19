# HAWKLYX Repository Hardening & Security Architecture

This document outlines the real security controls, architectural defenses, defense-in-depth measures, and operational limitations implemented across the **HAWKLYX** repository, Android application, and Cloudflare Worker edge service.

---

## 1. Zero-Secret Client Architecture

- **No Upstream Keys in APK**: The Android application and home-screen widgets contain zero API keys, authorization tokens, database strings, or third-party secrets. Decompiling the APK yields no upstream provider credentials.
- **Client-to-Worker Decoupling**: All market observations and historical chart queries route strictly through the self-hosted Cloudflare Worker edge endpoint (`https://hawklyx-backend.alifaniani.workers.dev/api/v1/...`).
- **Network Security Configuration**: Android enforces TLS 1.2+ encrypted communication and explicitly denies cleartext HTTP (`android:cleartextTrafficPermitted="false"` in `app/src/main/res/xml/network_security_config.xml`).

---

## 2. Cloudflare Worker Edge Hardening

- **Durable Object SQLite Singleton**: Incoming requests are coordinated by a centralized singleton Durable Object (`HawklyxCoordinator`). This prevents edge isolate race conditions and guarantees that global daily quotas (250 requests/day total: 110 reserved for automated cron collection, 140 for manual refreshes) cannot be exceeded by concurrency across multiple edge datacenters.
- **Atomic Server-Side Cooldown**: Both manual and automated requests are subject to an atomic 3-minute cooldown timer stored in Durable Object SQLite storage, stopping spam refresh abuse at the edge.
- **Request Coalescing**: If dozens of client widgets or apps trigger a refresh simultaneously when the cache expires, all concurrent requests await a single in-flight upstream fetch, preventing thundering herds.
- **Provider Fail-Closed Validation**: The Worker strictly checks upstream prices against realistic commodity boundaries ($10.00 to $250.00 per barrel). If an upstream payload contains anomalies, out-of-bounds quotes, or HTTP errors, the Worker discards the payload and serves the preserved last-known-good observation with a `stale: true` flag.
- **Multi-Account Allowance Rotation & Quarantine**: The edge service rotates up to 5 authorized OilPriceAPI free-tier accounts based on least-used daily counts. Accounts returning HTTP 401, 402, or 429 are immediately quarantined until UTC midnight, preventing consecutive failed queries.
- **Sanitized Edge Telemetry**: The `/health` endpoint outputs only aggregate quota counts and quarantine flags (`configured: true`, `quarantined: false`), stripping API keys and token fragments.

---

## 3. Repository and CI/CD Hardening

- **Strict Git Ignore Rules**: `.gitignore` comprehensively ignores local environment secrets (`.dev.vars`, `.env`), sensitive developer notes (`/note/`), local credential lists (`**/keys.txt`), Android release keystores (`*.keystore`, `*.jks`), local SDK configurations (`local.properties`), and build directories (`build/`, `.gradle/`, `.wrangler/`, `node_modules/`).
- **Least-Privilege GitHub Actions Permissions**: All CI workflows declare explicit top-level read-only permissions (`permissions: contents: read`). Workflows have no write access to code, releases, or pull requests.
- **Non-Deploying CI Validation**: The CI pipeline executes `npm run build` (`wrangler deploy --dry-run`) and Gradle unit tests. CI never provisions production secrets or performs automated deployments.
- **Dependabot Automation**: Weekly automated dependency scanning covers GitHub Actions, npm dependencies, and Gradle build plugins to identify and prompt updates for known vulnerabilities.

---

## 4. Known Threat Model Limitations

HAWKLYX is designed for non-commercial, personal, and educational monitoring. Contributors and users should be aware of the following architectural and operational constraints:

1. **Upstream Provider Dependency**: HAWKLYX relies on the OilPriceAPI free tier under confirmed educational/non-commercial allowances. Upstream API downtime, schema changes, or quota alterations may affect data freshness.
2. **Cloudflare Durable Object Requirements**: Deploying the Cloudflare Worker backend requires a Cloudflare Workers Paid plan to provision SQLite Durable Objects in production. Local testing using `wrangler dev` simulates Durable Objects locally without costs.
3. **Public Edge Endpoint Access**: The Worker `/api/v1/price/latest` route is publicly queryable to allow the Android widget to fetch updates without embedded client API keys. Excessive external traffic is bounded by the Worker's SQLite rate-limiting and quota exhaustion safeguards.
4. **Offline Cache Age**: While the Android client cleanly falls back to cached prices during network outages, users must rely on the displayed `as_of` timestamp to assess quote freshness. Quotes are not guaranteed exchange-grade tick data.
