# Contributing to HAWKLYX

Thank you for your interest in contributing to **HAWKLYX**! HAWKLYX is an open-source, non-commercial Brent crude oil monitor designed with a focus on minimal precision, edge resilience, and high-performance Android widgets.

Please take a moment to review this guide before submitting code, filing issues, or opening pull requests.

---

## Code of Conduct

All contributors and maintainers are expected to adhere to our [Code of Conduct](file:///CODE_OF_CONDUCT.md). Please report unacceptable behavior to [ali.faniani@gmail.com](mailto:ali.faniani@gmail.com).

---

## Repository Layout

```text
HAWKLYX/
├── app/                  # Android application & widget module (Kotlin, Jetpack Compose)
│   ├── src/main/java/    # Application source code (data, domain, ui, util, widget)
│   ├── src/main/res/     # Layouts, drawables, XML widget configurations
│   └── src/test/java/    # Android unit test suites (127 unit tests)
├── backend/worker/       # Cloudflare Worker edge service (TypeScript, Wrangler, Vitest)
│   ├── src/              # Worker source (coordinator, account pool, cache, calendar, provider)
│   └── test/             # Vitest unit & integration tests (69 tests)
├── .github/              # GitHub Actions workflows, issue forms, and templates
└── gradle/               # Gradle wrapper and version catalogs
```

---

## Local Development Prerequisites

### Android Environment
- **Java Development Kit**: JDK 17+ (JDK 21 or Android Studio JBR recommended).
- **Android SDK**: Compile SDK `release(37)` (Android 15+ platform), Target SDK `37`, Min SDK `26` (Android 8.0+).
- **Gradle**: Uses the Gradle Wrapper (`gradle-9.6.0`).
- **Android Studio**: Android Studio Ladybug / Meerkat or later.

If running on Windows PowerShell and using Android Studio's bundled JDK:
```powershell
$env:JAVA_HOME = "<path-to-android-studio>\jbr" # e.g. C:\Program Files\Android\Android Studio\jbr
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### Backend Environment
- **Node.js**: v20.x or higher LTS.
- **npm**: v10.x or higher.
- **Wrangler**: Cloudflare Wrangler CLI (bundled as a devDependency in `backend/worker/package.json`).

---

## Build, Test, and Validation Commands

Before submitting any changes, verify that all checks pass cleanly locally.

### 1. Android Application & Widgets

Run all checks from the root directory:

```bash
# Run all unit tests with freshly executed tasks
./gradlew --no-configuration-cache test --rerun-tasks

# Run Android Lint analysis
./gradlew --no-configuration-cache lintDebug

# Assemble the debug APK
./gradlew --no-configuration-cache assembleDebug
```

On Windows Command Prompt or PowerShell, use `.\gradlew.bat`.

> **Note**: The debug APK is built for local verification. Official releases and signing keys are managed exclusively by repository maintainers. Never commit signing keystores or passwords.

### 2. Cloudflare Worker Edge Service

Navigate to `backend/worker/` and run the validation suite:

```bash
cd backend/worker

# 1. Clean installation of locked dependencies
npm ci

# 2. TypeScript typecheck
npm run typecheck

# 3. Vitest test suites (all 69 unit and integration tests)
npm test

# 4. Dependency security audit
npm audit

# 5. Non-deploying dry-run build (validates Wrangler bindings and bundling)
npm run build
```

> **Warning**: **Never** execute `npm run deploy` in development or pull request environments. Production deployments must only be executed by repository owners using authorized Cloudflare credentials.

---

## Secrets and Sensitive Files Policy

HAWKLYX enforces strict secret isolation:
- **No client-side secrets**: The Android client and widget must **never** contain API keys, access tokens, or backend credentials.
- **Local Worker testing**: To run `wrangler dev` locally, copy `.dev.vars.example` to `.dev.vars` (which is git-ignored) and insert test keys.
- **Never commit**:
  - `.dev.vars` or `.env` files
  - `keys.txt` or personal credential notes
  - `local.properties` (machine-specific SDK paths)
  - Release keystores (`*.jks`, `*.keystore`)
  - Real OilPriceAPI production tokens

If an API key is accidentally committed or pushed, notify the maintainer immediately at [ali.faniani@gmail.com](mailto:ali.faniani@gmail.com) and follow responsible revocation procedures.

---

## Code Quality and Testing Expectations

1. **Deterministic Tests**: All new features, bug fixes, or behavioral adjustments must include deterministic unit tests. Tests must not rely on live upstream network calls or real API keys.
2. **Architecture Integrity**:
   - Keep domain business logic (e.g., market session tracking, price calculations, color resolution) independent of Android UI framework dependencies where possible.
   - Preserve Cloudflare Durable Object SQLite coordination semantics when altering backend cache, quota, or rate-limiting logic.
3. **No Gratuitous Upgrades**: Do not upgrade dependencies or tools solely to make version numbers higher. Changes should serve a functional, security, or bugfix purpose.
4. **Style Consistency**: Maintain existing Kotlin, TypeScript, and JSON formatting. Ensure code is readable and free of unnecessary noise.

---

## Pull Request Guidelines

1. **Branch Naming**: Use descriptive branch names like `fix/widget-refresh-race` or `feat/market-range-export`.
2. **Atomic Commits**: Keep commits focused and logically organized with clear commit messages following Conventional Commits (e.g., `fix:`, `feat:`, `docs:`, `test:`, `ci:`).
3. **Fill Out the PR Template**: Describe the change, the problem solved, testing verification commands executed, and attach screenshots for UI modifications.
4. **CI Passing**: Pull requests must pass all automated CI checks (Android build, lint, tests, and Worker typecheck, tests, audit, build). Maintainers will review failed runs and provide constructive feedback.

---

## Questions and Support

For questions or suggestions, please open a discussion or issue on [GitHub Issues](https://github.com/Greenhawk5/HAWKLYX/issues).
