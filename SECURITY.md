# Security Policy

The security and privacy of **HAWKLYX** users and infrastructure are taken seriously. As a non-commercial, open-source project, security practices focus on architecture-level defense, secret isolation, and responsible vulnerability disclosure.

---

## Supported Versions

Only the latest code on the default branch and the latest official release receive active security maintenance.

| Version | Supported | Notes |
|:---|:---:|:---|
| `1.1.x` / `v1.1.0` | Yes | Current stable release |
| `1.0.x` / `v1.0.0` | Yes | Previous stable release series |
| `main` | Yes | Active development branch |
| `< 1.0.0` | No | Experimental and pre-release commits are unsupported |

---

## Reporting a Vulnerability

If you discover a potential security vulnerability, credential leak, or architectural weakness in HAWKLYX, please do **NOT** open a public GitHub issue.

### Preferred Reporting Method
Report the vulnerability privately using **GitHub Private Vulnerability Reporting**:
1. Navigate to the HAWKLYX Security tab: [https://github.com/Greenhawk5/HAWKLYX/security/advisories/new](https://github.com/Greenhawk5/HAWKLYX/security/advisories/new)
2. Fill out the report form with full reproduction steps.

### Alternative Reporting Method
If GitHub Private Vulnerability Reporting is unavailable, send an encrypted or direct email to the project maintainer:
- **Contact**: Ali Faniani
- **Email**: [ali.faniani@gmail.com](mailto:ali.faniani@gmail.com)
- **Subject**: `[SECURITY] HAWKLYX Vulnerability Report`

---

## What to Include in Your Report

To help assess and remediate the issue promptly, please include:
- A descriptive summary of the issue.
- The component affected (Android application, Widget, or Cloudflare Worker edge service).
- Step-by-step reproduction instructions or a minimal proof of concept.
- An assessment of the potential impact (e.g., denial of service, information disclosure, quota exhaustion).
- Any recommended remediation or patches if available.

### Maintainer Response Commitment
- As an independent open-source project maintained on personal time, reports will be acknowledged within **48–72 hours**.
- If valid, a remediation plan or patch will be developed and released on GitHub as soon as feasible.

---

## Handling Accidental Credential Exposure

HAWKLYX enforces strict rules against committing credentials to version control.
If you suspect that an API key, private key, keystore, or environment file has been inadvertently tracked:
1. Immediately report the exact commit hash and filename to [ali.faniani@gmail.com](mailto:ali.faniani@gmail.com).
2. Revoke the exposed credential immediately at the upstream provider (e.g., OilPriceAPI console or Cloudflare dashboard).
3. Do not attempt to overwrite Git history on remote branches without maintainer coordination.

---

## Security Architectural Guarantees

1. **Zero Client Secrets**: The Android application and home-screen widgets contain no upstream API tokens, database credentials, or secret keys.
2. **Cleartext Traffic Disabled**: Android builds strictly prohibit cleartext HTTP communication (`android:cleartextTrafficPermitted="false"`).
3. **Fail-Closed Anomaly Detection**: Edge workers reject upstream prices outside the realistic commodity boundary ($10.00 to $250.00 / bbl), serving the last-known-good observation instead.
4. **Least-Privilege Automation**: GitHub Actions workflows run with explicit read-only permissions (`contents: read`) and non-deploying validation builds.
