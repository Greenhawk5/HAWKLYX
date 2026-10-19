# Support for HAWKLYX

Welcome to the **HAWKLYX** project support guide. Please use the designated channels below to ensure your request is addressed appropriately.

---

## Channels Overview

| Request Type | Channel | Description |
|:---|:---|:---|
| **Bug Reports** | [GitHub Issues (Bug Report)](https://github.com/Greenhawk5/HAWKLYX/issues/new?template=bug_report.yml) | For unexpected crashes, widget rendering bugs, parsing errors, or build failures. |
| **Feature Requests** | [GitHub Issues (Feature Request)](https://github.com/Greenhawk5/HAWKLYX/issues/new?template=feature_request.yml) | For proposing UI improvements, new widget layouts, or data export capabilities. |
| **General Questions** | [GitHub Issues](https://github.com/Greenhawk5/HAWKLYX/issues) (or Discussions if enabled) | For setup questions, local build troubleshooting, or architectural discussions. |
| **Security Reports** | [Private Security Advisory](https://github.com/Greenhawk5/HAWKLYX/security/advisories/new) (if enabled) or [Email](mailto:ali.faniani@gmail.com) | For confidential vulnerability disclosures (see [SECURITY.md](file:///SECURITY.md)). |

---

## Before Asking for Help

To help resolve issues faster, please check the following before submitting a ticket:
1. **Search Existing Issues**: Check both open and closed issues on [GitHub Issues](https://github.com/Greenhawk5/HAWKLYX/issues) to see if the issue or question has already been addressed.
2. **Consult Documentation**: Read the project [README.md](file:///README.md) and [CONTRIBUTING.md](file:///CONTRIBUTING.md) for local environment setup instructions.
3. **Check Market Hours**: Brent crude trading on ICE London follows specific trading hours (Mon 01:00 UTC – Fri 22:00 UTC). Quotes outside market hours or during weekends remain static with a `STALE` or `WEEKEND` indicator by design.
4. **Enforce Refresh Cooldown**: Both the app and widget enforce a 3-minute cooldown between manual refreshes to protect upstream allowances.

---

## Support Expectations

HAWKLYX is an open-source, non-commercial software project developed and maintained by an independent creator on personal time.
- There are **no commercial service-level agreements (SLAs)**, guaranteed response windows, or 24/7 on-call commitments.
- Inquiries and contributions will be reviewed and answered on a best-effort basis.
- Community pull requests, bug fixes, and documentation improvements are warmly welcomed!
