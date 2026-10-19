## Description

Brief summary of the changes made and the motivation behind them:

Fixes #(issue) or Closes #(issue)

---

## Type of Change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactoring / Performance improvement
- [ ] CI / Build / Infrastructure
- [ ] Documentation update

---

## Components Affected

- [ ] Android App UI / Jetpack Compose
- [ ] Market Snapshot Widget (3×2)
- [ ] Market Intelligence Widget (4×3)
- [ ] Domain / Market Calendar / Calculation
- [ ] Cloudflare Worker Edge Service
- [ ] Repository / CI / Automation

---

## Verification & Testing Performed

Commands executed locally to validate changes:

### Android:
```bash
./gradlew --no-configuration-cache test --rerun-tasks
./gradlew --no-configuration-cache lintDebug
./gradlew --no-configuration-cache assembleDebug
```

### Cloudflare Worker:
```bash
cd backend/worker
npm ci
npm run typecheck
npm test
npm audit
npm run build
```

---

## User-Visible Impact / Screenshots

If applicable, describe user-visible behavior changes or attach before/after screenshots:

---

## Contributor Security Checklist

- [ ] I have read the [CONTRIBUTING.md](file:///CONTRIBUTING.md) and [SECURITY.md](file:///SECURITY.md) guidelines.
- [ ] My changes do **NOT** contain API keys, `.dev.vars`, `.env`, tokens, passwords, keystores, or private secrets.
- [ ] I have added deterministic unit tests for any new behavior or bug fixes.
- [ ] All new and existing tests pass locally without warnings or failures.
- [ ] No extraneous build outputs, temporary files, or local paths are included in this PR.
