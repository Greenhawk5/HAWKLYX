# HAWKLYX — Brent Crude Oil Monitor & Android Widget

**HAWKLYX** is a lightweight, financial product designed for personal, educational, and non-commercial monitoring of Brent crude oil prices (`BRENT_CRUDE_USD`). Combining **Minimal Precision** and **Premium Finance**, the Android home-screen widget serves as the primary product experience, supported by a modern Jetpack Compose application and a Cloudflare Worker edge service.

---

## Architecture and Data Flow

```text
Upstream: OilPriceAPI
    │ Authenticated requests via multi-account key pool (Slots 1-5)
    ▼
Edge Service: HAWKLYX Cloudflare Worker (`backend/worker/`)
    ├── Provider adapter & strict anomaly validation ($10 - $250/bbl)
    ├── Account pool manager (least-used rotation, quota tracking, 401/402/429 quarantine)
    ├── Request coalescing & cooldown engine (15-min upstream cadence, 3-min manual cooldown)
    ├── Last-known-good cache of valid observations
    └── Stable public JSON contract (/api/v1/price/latest, /health)
    │
    ▼
Client: HAWKLYX Android Application & Home-Screen Widget (`app/`)
    ├── AppWidgetProvider (`HawklyxWidgetProvider`) with instant glanceability
    ├── Local persistence (`HawklyxPreferences`) preserving last-known-good price offline
    ├── Repository layer (`HawklyxRepository`) with reactive StateFlow & cooldown tracking
    ├── Background periodic sync (`HawklyxSyncWorker` via Android WorkManager)
    └── Luxury dark UI (`#101311` charcoal, `#62E59A` emerald, `#F4F6F4` typography)
```

---

## Key Features

1. **Glanceable Home-Screen Widget**:
   - High-contrast, dark luxury card (`#181C19`) with 16dp rounded corners and subtle border.
   - Dominant, readable Brent price numeral (`$82.45`) with `USD / barrel` denomination.
   - Real-time freshness status badge: `LIVE` (green), `STALE` (amber), `SYNCING` (gray), `OFFLINE` (amber), `QUOTA LIMIT` (red).
   - Source observation timestamp (`as_of`) distinguishing market observation time from local fetch time.
   - Interactive manual refresh button with 3-minute cooldown enforcement.
   - One-tap launch to the full app.

2. **Resilient Edge Worker Backend**:
   - Zero API credentials in the Android app or APK.
   - Server-side account rotation across up to 5 authorized OilPriceAPI accounts (within provider-confirmed non-commercial/educational scope).
   - In-flight request coalescing prevents duplicate upstream calls.
   - Never fabricates prices or zeroes out values upon upstream failures; always serves last-known-good observation with appropriate freshness metadata.

3. **Android Application**:
   - **Home Screen**: Hero Brent price card, market provenance details, edge synchronization breakdown, and widget setup guide.
   - **About Screen**: Architectural transparency, quota policies, developer contact (`Ali Faniani`), versioning, and legal attribution.

---

## Project Structure

```text
HAWKLYX/
├── app/                              # Android application & widget module
│   ├── src/main/java/ir/alifaniani/hawklyx/
│   │   ├── data/
│   │   │   ├── local/HawklyxPreferences.kt
│   │   │   ├── model/BrentPriceObservation.kt
│   │   │   ├── remote/HawklyxApiClient.kt
│   │   │   └── repository/HawklyxRepository.kt
│   │   ├── ui/
│   │   │   ├── screens/HomeScreen.kt
│   │   │   ├── screens/AboutScreen.kt
│   │   │   └── theme/Color.kt, Theme.kt, Type.kt
│   │   ├── widget/
│   │   │   ├── HawklyxWidgetProvider.kt
│   │   │   └── HawklyxSyncWorker.kt
│   │   └── MainActivity.kt
│   └── src/main/res/
│       ├── layout/hawklyx_widget_layout.xml
│       ├── xml/hawklyx_widget_info.xml
│       └── xml/network_security_config.xml
├── backend/worker/                   # Cloudflare Worker edge service
│   ├── src/
│   │   ├── account-pool.ts           # Multi-account rotation & quota manager
│   │   ├── cache.ts                  # Cache, coalescing & cooldown engine
│   │   ├── index.ts                  # Worker fetch router (/api/v1/price/latest, /health)
│   │   ├── provider.ts               # OilPriceAPI adapter & validation
│   │   └── types.ts                  # Upstream & client data contracts
│   ├── test/                         # Comprehensive Vitest unit & integration tests
│   ├── wrangler.jsonc                # Cloudflare Worker configuration
│   └── .dev.vars.example             # Local secrets template (placeholders only)
└── README.md
```

---

## Setup & Development Guide

### Prerequisites
- **Android Development**: Android Studio, JDK 17+, Android SDK API 26-37.
- **Backend Development**: Node.js v20+, npm v10+.

### 1. Cloudflare Worker Edge Service

#### Install Dependencies
```bash
cd backend/worker
npm install
```

#### Run Backend Unit Tests
```bash
npm test
```

#### Run Local Development Server
Copy `.dev.vars.example` to `.dev.vars` and add your authorized OilPriceAPI keys:
```bash
cp .dev.vars.example .dev.vars
npm run dev
```

#### Deploy to Cloudflare
Configure production secrets using Wrangler (never commit secrets to Git):
```bash
npx wrangler secret put OILPRICEAPI_KEY_1
npx wrangler secret put OILPRICEAPI_KEY_2
# Repeat for up to 5 authorized accounts
npm run deploy
```

---

### 2. Android Application & Widget

#### Run Unit Tests
```bash
./gradlew test
```

#### Assemble Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

#### Install onto Device or Emulator
```bash
./gradlew installDebug
```

---

## Security & Quota Compliance

- **No Secrets in APK**: All upstream OilPriceAPI keys reside exclusively in server-side Cloudflare Worker secrets.
- **Provider Clarification**: The provider support team confirmed that multi-account allowance pooling is acceptable for this personal, educational, testing, and non-commercial app.
- **Budget Enforced**: 50 requests/account daily limit with a global safety budget of 200 requests/day, ensuring capacity remains for retries and manual refreshes without unexpected exhaustion.
- **Cooldown Safeguard**: 3-minute cooldown enforced both on the server and in the client UI.

---

## License & Attribution

- **Developer**: Ali Faniani (`ali.faniani@gmail.com`)
- **Product**: HAWKLYX v1.0
- **Attribution**: Market data sourced from OilPriceAPI. Brent crude is a benchmark pricing specification for global crude oil purchases.
