::: {align="center"}
# HAWKLYX

### Brent Crude Monitor · Android Widgets · Cloudflare Edge

**A focused, privacy-conscious way to follow Brent crude oil prices ---
right from your Android home screen.**

```{=html}
<p>
```
`<a href="https://github.com/Greenhawk5/HAWKLYX">`{=html}Repository`</a>`{=html}
· `<a href="#-features">`{=html}Features`</a>`{=html} ·
`<a href="#-screenshots">`{=html}Screenshots`</a>`{=html} ·
`<a href="#-getting-started">`{=html}Getting started`</a>`{=html} ·
`<a href="#-security--privacy">`{=html}Security`</a>`{=html}
```{=html}
</p>
```
```{=html}
<p>
```
`<a href="https://github.com/Greenhawk5/HAWKLYX/actions/workflows/ci.yml">`{=html}`<img src="https://github.com/Greenhawk5/HAWKLYX/actions/workflows/ci.yml/badge.svg" alt="CI status" />`{=html}`</a>`{=html}
`<img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" />`{=html}
`<img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />`{=html}
`<img src="https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />`{=html}
`<img src="https://img.shields.io/badge/Cloudflare-Workers%20%2B%20Durable%20Objects-F38020?style=for-the-badge&logo=cloudflare&logoColor=white" alt="Cloudflare" />`{=html}
`<a href="LICENSE">`{=html}`<img src="https://img.shields.io/badge/License-MIT-F5C542?style=for-the-badge" alt="MIT License" />`{=html}`</a>`{=html}
```{=html}
</p>
```
```{=html}
<p>
```
`<img src="https://img.shields.io/badge/Instrument-BRENT_CRUDE_USD-1B3025?style=flat-square" alt="Brent crude" />`{=html}
`<img src="https://img.shields.io/badge/Architecture-Edge--coordinated-263B32?style=flat-square" alt="Edge-coordinated architecture" />`{=html}
`<img src="https://img.shields.io/badge/Secrets-Server--side_only-263B32?style=flat-square" alt="Server-side secrets" />`{=html}
```{=html}
</p>
```
:::

------------------------------------------------------------------------

## ✦ About

**HAWKLYX** is an Android application for monitoring Brent crude oil
prices (`BRENT_CRUDE_USD`). It combines a premium dark market interface,
two Android home-screen widgets, interactive historical charts, and a
Cloudflare Worker backend that coordinates upstream requests and
preserves the last verified quote when the provider is unavailable.

The guiding principle is simple: **market data should be easy to glance
at, honest about its freshness, and resilient when connectivity or
upstream services fail.**

> A clear market snapshot at a glance. Deeper historical context when
> you need it.

## ✦ At a glance

  ----------------------------------- -----------------------------------
  **Instrument**                      Brent crude oil, USD per barrel

  **Android app**                     Kotlin, Jetpack Compose, Android
                                      home-screen widgets

  **Minimum Android version**         Android 8.0 (API 26)

  **Backend**                         Cloudflare Workers + SQLite-backed
                                      Durable Objects

  **Upstream data**                   OilPriceAPI

  **History**                         Verified hourly observations and
                                      daily rollups

  **License**                         MIT --- see [`LICENSE`](LICENSE)

  **Release status**                  Preparing the first stable
                                      `v1.0.0`; check GitHub Releases for
                                      published builds
  ----------------------------------- -----------------------------------

## ✦ Features

```{=html}
<table>
```
```{=html}
<tr>
```
```{=html}
<td width="50%" valign="top">
```
### 📊 Market Snapshot widget

-   Compact home-screen quote card.

-   Prominent Brent price and `USD / barrel` denomination.

-   Market and freshness status.

-   Observation timestamp.

-   Dedicated manual refresh control.

-   One-tap launch to the app.

    ```{=html}
    </td>
    ```
    ```{=html}
    <td width="50%" valign="top">
    ```

### 📈 Market Intelligence widget

-   Larger quote-and-chart layout.

-   Mini-chart rendered from available observations.

-   Price-change metrics.

-   Market state and freshness context.

-   Manual refresh action.

-   Authentic data only --- no generated chart points.

    ```{=html}
    </td>
    ```
    ```{=html}
    </tr>
    ```
    ```{=html}
    <tr>
    ```
    ```{=html}
    <td width="50%" valign="top">
    ```

### 🧭 Interactive market history

-   `1D`, `1W`, `1M`, `6M`, and `1Y` ranges.

-   Touch inspection for individual observations.

-   Range-specific summaries and high/low context.

-   Hourly observations and daily rollups for longer ranges.

-   Partial-history indicators when a range is not fully populated.

    ```{=html}
    </td>
    ```
    ```{=html}
    <td width="50%" valign="top">
    ```

### 🎨 Market-aware visual language

-   Emerald for positive movement.

-   Ruby/red for negative movement.

-   Amber for a closed or paused market.

-   Explicit freshness and error states.

-   Consistent visual semantics across the app and widgets.

    ```{=html}
    </td>
    ```
    ```{=html}
    </tr>
    ```
    ```{=html}
    </table>
    ```

### Reliability by design

-   **No upstream API keys in the Android client.** Provider credentials
    stay on the Worker.
-   **Coordinated upstream requests.** A Durable Object centralizes
    shared limits and prevents duplicate concurrent fetches.
-   **Request coalescing.** Concurrent refreshes can share one in-flight
    provider request.
-   **Last-known-good preservation.** Provider failures do not replace a
    valid quote with a fabricated value or zero.
-   **Sanity checks.** Implausible quotes are rejected.
-   **Controlled refreshes.** Shared request budgets and cooldowns help
    protect upstream allowances.
-   **Background collection.** Scheduled Worker execution can collect
    observations independently of the Android app.

------------------------------------------------------------------------

## ✦ Screenshots

The gallery is reserved for screenshots from the running app and
widgets. Add real captures under `docs/screenshots/`, then uncomment and
update the image paths below. This keeps the README honest: mockups are
not presented as product screenshots.

```{=html}
<!--
<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/screenshots/home-market.png" alt="HAWKLYX home screen showing the current Brent quote" width="100%" />
      <br /><sub><b>Market overview</b> · Quote and market status</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/screenshots/market-history-1d.png" alt="HAWKLYX one-day market history chart" width="100%" />
      <br /><sub><b>Market history</b> · Inspect verified observations</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/screenshots/widget-snapshot.png" alt="HAWKLYX Market Snapshot Android widget" width="100%" />
      <br /><sub><b>Market Snapshot</b> · A quick glance from the home screen</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/screenshots/widget-intelligence.png" alt="HAWKLYX Market Intelligence widget with chart" width="100%" />
      <br /><sub><b>Market Intelligence</b> · Quote and chart together</sub>
    </td>
  </tr>
</table>
-->
```
**Suggested gallery:** Home screen · 1D history · Market Snapshot widget
· Market Intelligence widget.

------------------------------------------------------------------------

## ✦ How it works

``` text
┌──────────────────────┐
│      OilPriceAPI     │
│   Upstream quote     │
└──────────┬───────────┘
           │ Server-side authenticated request
           ▼
┌──────────────────────────────────────────┐
│       HAWKLYX Cloudflare Worker           │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ HawklyxCoordinator                 │  │
│  │ Durable Object + SQLite storage    │  │
│  └────────────────────────────────────┘  │
│    • Shared request budget               │
│    • Account allowance rotation          │
│    • Cooldown and request coalescing     │
│    • Quote validation and cache          │
│    • Hourly observations / daily rollups │
└───────────────────┬──────────────────────┘
                    │ JSON API
                    ▼
┌──────────────────────────────────────────┐
│          HAWKLYX Android app             │
│                                          │
│  Compose UI · Historical chart            │
│  Repository · Local last-good cache       │
│  WorkManager · Two home-screen widgets    │
└──────────────────────────────────────────┘
```

### Data flow

1.  The Worker requests a quote when cache state, market status, and
    request policy allow it.
2.  The coordinator validates the result and preserves the last known
    valid quote if an upstream request fails.
3.  Valid observations are recorded for historical charting; chart reads
    do not need to trigger extra upstream quote requests.
4.  The Android repository exposes data to the app and widgets, while
    local persistence can retain the last known quote for offline
    display.
5.  The UI communicates price movement and data freshness so cached data
    is not mistaken for a newly observed quote.

### Backend API

  -----------------------------------------------------------------------
  Endpoint                            Purpose
  ----------------------------------- -----------------------------------
  `GET /api/v1/price/latest`          Latest available quote and
                                      freshness metadata

  `GET /api/v1/price/history`         Historical observations for
                                      supported ranges

  `GET /health`                       Sanitized operational health
                                      information
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## ✦ Technology stack

```{=html}
<p>
```
`<img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />`{=html}
`<img src="https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />`{=html}
`<img src="https://img.shields.io/badge/Android_Widgets-RemoteViews-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android widgets" />`{=html}
`<img src="https://img.shields.io/badge/Cloudflare_Workers-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Cloudflare Workers" />`{=html}
`<img src="https://img.shields.io/badge/Durable_Objects-SQLite-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Durable Objects and SQLite" />`{=html}
`<img src="https://img.shields.io/badge/TypeScript-5.7%2B-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript" />`{=html}
`<img src="https://img.shields.io/badge/Gradle-9.6-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle" />`{=html}
`<img src="https://img.shields.io/badge/Vitest-Tests-6E9F18?style=flat-square&logo=vitest&logoColor=white" alt="Vitest" />`{=html}
```{=html}
</p>
```
  Area                           Technology
  ------------------------------ -----------------------------------------
  Android UI                     Jetpack Compose + Material 3
  Android widgets                AppWidgetProvider + RemoteViews
  Language                       Kotlin
  Background sync                Android WorkManager
  Local state                    Preferences-backed last-known-good data
  Backend runtime                Cloudflare Workers
  Coordination and persistence   Durable Objects with SQLite storage
  Backend language               TypeScript
  Backend tests                  Vitest
  Android tests                  JUnit
  Build                          Gradle Wrapper

------------------------------------------------------------------------

## ✦ Repository structure

``` text
HAWKLYX/
├── .github/
│   ├── ISSUE_TEMPLATE/           # Bug reports and feature requests
│   ├── workflows/                # GitHub Actions CI
│   ├── CODEOWNERS
│   ├── dependabot.yml
│   └── PULL_REQUEST_TEMPLATE.md
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/ir/alifaniani/hawklyx/
│       │   │   ├── data/          # API, models, preferences, repository
│       │   │   ├── domain/        # Market calendar and price calculations
│       │   │   ├── ui/            # Screens, chart, components and theme
│       │   │   ├── util/          # Time and formatting helpers
│       │   │   └── widget/        # Providers, coordinator, renderer, worker
│       │   └── res/               # Layouts, drawables, widget metadata
│       └── test/                  # Android unit tests
├── backend/
│   └── worker/
│       ├── src/                   # Worker, coordinator and provider logic
│       ├── test/                  # Backend tests
│       ├── wrangler.jsonc         # Worker configuration and bindings
│       └── package.json
├── gradle/                        # Wrapper and version catalog
├── docs/
│   └── screenshots/               # Optional real product screenshots
├── CHANGELOG.md
├── CITATION.cff
├── CONTRIBUTING.md
├── LICENSE
├── NOTICE.md
├── REPOSITORY_HARDENING.md
├── SECURITY.md
├── SUPPORT.md
└── README.md
```

------------------------------------------------------------------------

## ✦ Getting started

### Requirements

-   Android Studio with a compatible Android SDK.
-   Android SDK Platform 37, as configured by the current project.
-   A supported JDK; Android Studio's bundled JBR is recommended.
-   Node.js 20+ and npm for backend development.
-   A Cloudflare account and correctly configured Worker bindings for
    backend development.

### 1. Clone the repository

``` bash
git clone https://github.com/Greenhawk5/HAWKLYX.git
cd HAWKLYX
```

### 2. Build and test Android

On Windows PowerShell:

``` powershell
# If needed, set JAVA_HOME to Android Studio's bundled JBR first.
.\gradlew.bat --no-configuration-cache test --rerun-tasks
.\gradlew.bat --no-configuration-cache lintDebug
.\gradlew.bat --no-configuration-cache assembleDebug
```

On macOS/Linux:

``` bash
./gradlew --no-configuration-cache test --rerun-tasks
./gradlew --no-configuration-cache lintDebug
./gradlew --no-configuration-cache assembleDebug
```

The debug APK is generated at:

``` text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open the repository in Android Studio, wait for Gradle
sync, select a connected Android device, and run the `app`
configuration.

### 3. Set up the Cloudflare Worker

``` bash
cd backend/worker
npm ci
npm run typecheck
npm test
npm audit
npm run build
```

For local development, copy `.dev.vars.example` to `.dev.vars` and add
your own development credentials. **Never commit `.dev.vars` or real API
keys.**

The configured `npm run build` performs a Wrangler dry-run build; it
does not deploy the Worker.

------------------------------------------------------------------------

## ✦ Data limits and market behavior

HAWKLYX is designed to operate within constrained upstream API
allowances.

-   **Shared daily request budget:** the coordinator enforces the
    configured global budget.
-   **Automated collection reserve:** part of the budget is reserved for
    scheduled market observations.
-   **Manual refresh cooldown:** repeated manual refreshes are
    rate-limited.
-   **Cache-aware requests:** cached data can be returned without
    contacting the provider.
-   **Historical reads:** chart history is served from stored
    observations and rollups rather than requiring a fresh upstream
    quote for every chart interaction.
-   **Market closure:** prices may remain unchanged while the market is
    closed; the UI communicates market state rather than implying
    movement.
-   **No fabricated history:** chart points represent stored
    observations only. Longer ranges may remain partial until sufficient
    history accumulates.

The configured backend policy is documented as a maximum of **250
upstream requests per UTC day**, with **110 reserved for scheduled
collection** and a **140-request ceiling for manual requests**. These
are implementation settings, not a guarantee of provider availability;
verify the deployed Worker configuration before relying on production
limits.

------------------------------------------------------------------------

## ✦ Security & privacy

-   Provider API credentials must remain server-side and must never be
    embedded in the Android app.
-   Local development secrets belong in ignored local configuration
    files, not source control.
-   The health endpoint should expose only sanitized aggregate
    operational data.
-   The app may retain the last verified quote locally to improve
    resilience during temporary connectivity failures.
-   Market data may be delayed or cached. HAWKLYX is not a
    trade-execution platform and does not provide financial advice.

Please review [`SECURITY.md`](SECURITY.md) before reporting a
vulnerability. Never open public issues containing credentials, tokens,
or private account information.

------------------------------------------------------------------------

## ✦ Troubleshooting

  -----------------------------------------------------------------------
  Symptom                             What to check
  ----------------------------------- -----------------------------------
  The quote appears unchanged         Check market status, observation
                                      timestamp, and freshness state. A
                                      closed market or cached response
                                      can legitimately preserve the same
                                      value.

  A widget does not update            Check connectivity,
  immediately                         WorkManager/background
                                      restrictions, and the widget's
                                      freshness indicator. Android may
                                      defer background work.

  Manual refresh does not trigger a   The shared cooldown, cache, or
  provider request                    global budget may be active. A
                                      refresh action does not guarantee a
                                      new upstream call.

  The chart marks a range as partial  The backend may not yet have
                                      accumulated observations for the
                                      full selected period.

  Worker tests fail                   Use Node.js 20+ and run `npm ci`
                                      from `backend/worker/`.

  Android build cannot find SDK       Install the configured SDK platform
  Platform 37                         through Android Studio's SDK
                                      Manager, then sync Gradle again.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## ✦ Project documentation

  ------------------------------------------------------------------------------------------
  Document                                               Description
  ------------------------------------------------------ -----------------------------------
  [`CHANGELOG.md`](CHANGELOG.md)                         Release notes and change history

  [`CONTRIBUTING.md`](CONTRIBUTING.md)                   Contribution workflow and
                                                         development expectations

  [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)             Community standards

  [`SECURITY.md`](SECURITY.md)                           Security reporting policy

  [`SUPPORT.md`](SUPPORT.md)                             Support channels

  [`REPOSITORY_HARDENING.md`](REPOSITORY_HARDENING.md)   Security architecture and
                                                         repository hardening

  [`CITATION.cff`](CITATION.cff)                         Citation metadata

  [`NOTICE.md`](NOTICE.md)                               Third-party notices and
                                                         attributions

  [`LICENSE`](LICENSE)                                   MIT license text
  ------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## ✦ Release status

HAWKLYX is preparing its first stable `v1.0.0` release. Until a release
is published, the repository source and CI status are the authoritative
references for build readiness.

-   [GitHub Releases](https://github.com/Greenhawk5/HAWKLYX/releases)
-   [CI
    runs](https://github.com/Greenhawk5/HAWKLYX/actions/workflows/ci.yml)
-   [Report a bug](https://github.com/Greenhawk5/HAWKLYX/issues/new)

Release artifacts, signing, and release tags should only be considered
official when published by the repository owner.

------------------------------------------------------------------------

## ✦ License

HAWKLYX is distributed under the [MIT License](LICENSE). See the license
file for the complete terms.

## ✦ Author

**Ali Faniani**

```{=html}
<p>
```
`<a href="https://github.com/Greenhawk5">`{=html}GitHub
profile`</a>`{=html}  · 
`<a href="https://github.com/Greenhawk5/HAWKLYX">`{=html}HAWKLYX
repository`</a>`{=html}
```{=html}
</p>
```
::: {align="center"}

------------------------------------------------------------------------

`<sub>`{=html}Built with Kotlin, Jetpack Compose, Android Widgets,
TypeScript, and Cloudflare Workers.`</sub>`{=html}
:::
