# Changelog

All notable changes to the **HAWKLYX** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned for [1.0.0] — Initial Stable Release

#### Added
- **Dual Home-Screen Widgets**:
  - *Market Snapshot Widget (3×2)*: Ultra-low-overhead glanceable widget showing dominant Brent price, currency denomination, timestamp (`as_of`), freshness status badge (`LIVE`, `STALE`, `SYNCING`, `OFFLINE`, `QUOTA LIMIT`), manual refresh button, and one-tap app launch.
  - *Market Intelligence Widget (4×3)*: Enhanced widget with live bitmap sparkline mini-chart, 24-hour price change metric, session progress, and session baseline price.
- **Interactive Jetpack Compose Application**:
  - *Hero Market Screen*: Real-time Brent crude quote with high-contrast luxury dark theme (`#101311` charcoal background, `#62E59A` emerald accents, `#F4F6F4` typography).
  - *Market History Chart*: Interactive historical chart supporting multi-range selections (`1D`, `1W`, `1M`, `1Y`, `ALL`) with smooth quadratic Bezier curve rendering, touch indicator, min/max guides, and range-specific downsampling.
  - *Market Session Calendar*: Real-time tracking of ICE London Brent trading sessions (open, closed, weekend, pre-market) with local device time presentation.
  - *Price Change Calculations*: Verified 24-hour change and market session baseline delta calculations with directional color coding (emerald for gains, ruby for declines, platinum for flat).
  - *About & Transparency Screen*: Complete architectural disclosure, quota transparency, developer portfolio and GitHub links, and legal attributions.
- **Cloudflare Worker Edge Coordination Engine (`backend/worker/`)**:
  - *SQLite Durable Object Singleton*: Cross-isolate coordinator (`HawklyxCoordinator`) preventing duplicate upstream requests and enforcing global quotas across worldwide edge datacenters.
  - *Multi-Account Allowance Pooling*: Rotates across up to 5 authorized OilPriceAPI accounts using least-used selection to maintain free-tier quota compliance.
  - *Request Coalescing*: In-flight deduplication combining concurrent client refreshes into a single upstream fetch.
  - *Persistent State*: Survives edge isolate recycles, maintaining quota usage counters, account quarantine states, and the last-known-good price observation.
  - *Sanitized Health Endpoint*: `/health` route reporting operational metrics without exposing API keys or token fragments.
- **Offline & Low-Connectivity Resilience**:
  - Local SQLite and SharedPreferences caching (`HawklyxPreferences`) ensuring offline usability without zeroing out prices or showing blank screens.
  - 3-minute cooldown safeguard enforced both on client and server to prevent accidental request exhaustion.
  - Android WorkManager periodic background sync (`HawklyxSyncWorker`) maintaining freshness at a 15-minute cadence.

#### Changed
- Upgraded edge daily budget architecture to 250 requests/day, divided into 110 requests reserved for automated cron collection and 140 requests for manual on-demand refreshes.
- Optimized sparkline rendering to generate cached bitmaps without re-measuring on every frame.
- Refined widget layout typography and touch targets for Android 8.0 (API 26) through Android 15 (API 37).

#### Fixed
- Fixed race condition where concurrent widget refresh clicks could trigger multiple simultaneous network requests.
- Fixed timezone offset handling when displaying ICE exchange session start/end times in local device time.
- Preserved last-known-good valid price observation when upstream returns synthetic or stale markers during weekend market closures.

#### Security
- Zero upstream API keys or secrets in Android client application, APK, or repository code.
- Strict price bounds anomaly validation ($10.00 to $250.00 / bbl) discarding corrupt upstream payloads before client delivery.
- Android `network_security_config.xml` prohibiting cleartext HTTP traffic across all environments.
- Sanitized edge logging and health endpoints preventing credential leakage in operational monitoring.

#### Infrastructure
- Configured automated GitHub Actions continuous integration pipeline for both Android and Cloudflare Worker.
- Added Dependabot automated dependency scanning and updates for GitHub Actions, npm, and Gradle.
- Established repository health assets, including issue templates, pull request template, security policy, and contributing guidelines.
- Created local build and test verification pipeline covering 127 Android unit tests and 69 Worker tests.

---

[Unreleased]: https://github.com/Greenhawk5/HAWKLYX/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Greenhawk5/HAWKLYX/releases/tag/v1.0.0
