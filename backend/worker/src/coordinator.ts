import { DurableObject } from "cloudflare:workers";
import { AccountPoolManager, QuarantineReason } from "./account-pool";
import { CacheManager } from "./cache";
import {
  OilPriceProvider,
  ProviderAuthError,
  ProviderQuotaExhaustedError,
  ProviderRateLimitedError,
  ProviderServerError,
  ProviderValidationError,
} from "./provider";
import { Env, HawklyxPriceResponse, HealthStatusResponse, ValidatedObservation } from "./types";

export class HawklyxCoordinator extends DurableObject<Env> {
  private accountPool: AccountPoolManager;
  private cacheManager: CacheManager;
  private provider: OilPriceProvider;
  private inFlightFetchPromise: Promise<{ body: HawklyxPriceResponse; status: number }> | null = null;
  private initialized: Promise<void> | null = null;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.accountPool = new AccountPoolManager(env);

    const freshnessMin = parseInt(env.DEFAULT_UPSTREAM_INTERVAL_MINUTES || "15", 10);
    const cooldownSec = parseInt(env.MANUAL_REFRESH_COOLDOWN_SECONDS || "180", 10);
    this.cacheManager = new CacheManager({
      freshnessIntervalMinutes: freshnessMin,
      cooldownSeconds: cooldownSec,
    });

    const sanityMin = parseFloat(env.PRICE_SANITY_MIN || "10");
    const sanityMax = parseFloat(env.PRICE_SANITY_MAX || "250");
    this.provider = new OilPriceProvider({
      sanityMinPrice: sanityMin,
      sanityMaxPrice: sanityMax,
      timeoutMs: 8000,
    });

    if (this.ctx && typeof this.ctx.blockConcurrencyWhile === "function") {
      this.initialized = this.ctx.blockConcurrencyWhile(async () => {
        this.initStorage();
      });
    } else {
      this.initStorage();
      this.initialized = Promise.resolve();
    }
  }

  private initStorage(): void {
    if (!this.ctx?.storage?.sql) {
      return;
    }
    const sql = this.ctx.storage.sql;
    sql.exec(`
      CREATE TABLE IF NOT EXISTS system_state (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS account_usage (
        slot INTEGER PRIMARY KEY,
        used_today INTEGER NOT NULL,
        last_used_at INTEGER,
        quarantined_until INTEGER,
        quarantine_reason TEXT
      )
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS observations (
        key TEXT PRIMARY KEY,
        data TEXT NOT NULL,
        updated_at INTEGER NOT NULL
      )
    `);

    this.loadPersistedState();
  }

  private loadPersistedState(): void {
    if (!this.ctx?.storage?.sql) return;
    const sql = this.ctx.storage.sql;

    // 1. Read system_state
    const systemRows = sql.exec<{ key: string; value: string }>(
      "SELECT key, value FROM system_state"
    ).toArray();
    const systemMap = new Map<string, string>();
    for (const row of systemRows) {
      systemMap.set(row.key, row.value);
    }

    const lastResetDay = systemMap.get("last_reset_day") || "";
    const lastSuccessfulFetchMs = systemMap.get("last_successful_fetch_ms")
      ? parseInt(systemMap.get("last_successful_fetch_ms")!, 10)
      : null;
    const lastUpstreamAttemptMs = systemMap.get("last_upstream_attempt_ms")
      ? parseInt(systemMap.get("last_upstream_attempt_ms")!, 10)
      : 0;

    // 2. Read observations
    const obsRows = sql.exec<{ key: string; data: string; updated_at: number }>(
      "SELECT key, data, updated_at FROM observations"
    ).toArray();
    let lastObs: ValidatedObservation | null = null;
    let lastGood: ValidatedObservation | null = null;
    for (const row of obsRows) {
      try {
        const parsed = JSON.parse(row.data) as ValidatedObservation;
        if (row.key === "last_observation") lastObs = parsed;
        if (row.key === "last_known_good") lastGood = parsed;
      } catch {
        // Discard corrupt record
      }
    }

    this.cacheManager.hydrate({
      lastObservation: lastObs,
      lastKnownGood: lastGood,
      lastSuccessfulFetchMs,
      lastUpstreamAttemptMs,
    });

    // 3. Read account_usage
    const accountRows = sql.exec<{
      slot: number;
      used_today: number;
      last_used_at: number | null;
      quarantined_until: number | null;
      quarantine_reason: string | null;
    }>("SELECT slot, used_today, last_used_at, quarantined_until, quarantine_reason FROM account_usage").toArray();

    this.accountPool.hydrate(
      accountRows.map((r) => ({
        slot: r.slot,
        usedToday: r.used_today,
        lastUsedAt: r.last_used_at,
        quarantinedUntil: r.quarantined_until,
        quarantineReason: r.quarantine_reason as QuarantineReason | undefined,
      })),
      lastResetDay
    );

    this.checkAndPersistDayReset(Date.now());
  }

  private checkAndPersistDayReset(nowMs: number = Date.now()): void {
    const currentDay = new Date(nowMs).toISOString().slice(0, 10);
    const previousDay = this.accountPool.getLastResetDay();

    if (previousDay !== currentDay) {
      this.accountPool.checkDayReset(new Date(nowMs));
      this.accountPool.setLastResetDay(currentDay);

      if (this.ctx?.storage?.sql) {
        const sql = this.ctx.storage.sql;
        sql.exec(
          "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
          "last_reset_day",
          currentDay
        );
        sql.exec(`
          UPDATE account_usage
          SET used_today = 0,
              quarantined_until = CASE WHEN quarantine_reason = 'EXHAUSTED' THEN NULL ELSE quarantined_until END,
              quarantine_reason = CASE WHEN quarantine_reason = 'EXHAUSTED' THEN NULL ELSE quarantine_reason END
        `);
      }
    }
  }

  private persistAccountUpdate(slot: number): void {
    if (!this.ctx?.storage?.sql) return;
    const accounts = this.accountPool.dump().accounts;
    const acc = accounts.find((a) => a.slot === slot);
    if (!acc) return;

    this.ctx.storage.sql.exec(
      "INSERT OR REPLACE INTO account_usage (slot, used_today, last_used_at, quarantined_until, quarantine_reason) VALUES (?, ?, ?, ?, ?)",
      acc.slot,
      acc.usedToday,
      acc.lastUsedAt,
      acc.quarantinedUntil,
      acc.quarantineReason || null
    );
  }

  private persistCacheState(): void {
    if (!this.ctx?.storage?.sql) return;
    const sql = this.ctx.storage.sql;
    const dump = this.cacheManager.dump();

    sql.exec(
      "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
      "last_upstream_attempt_ms",
      String(dump.lastUpstreamAttemptMs)
    );
    if (dump.lastSuccessfulFetchMs !== null) {
      sql.exec(
        "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
        "last_successful_fetch_ms",
        String(dump.lastSuccessfulFetchMs)
      );
    }
    if (dump.lastObservation) {
      sql.exec(
        "INSERT OR REPLACE INTO observations (key, data, updated_at) VALUES (?, ?, ?)",
        "last_observation",
        JSON.stringify(dump.lastObservation),
        dump.lastUpstreamAttemptMs
      );
    }
    if (dump.lastKnownGood) {
      sql.exec(
        "INSERT OR REPLACE INTO observations (key, data, updated_at) VALUES (?, ?, ?)",
        "last_known_good",
        JSON.stringify(dump.lastKnownGood),
        dump.lastSuccessfulFetchMs || dump.lastUpstreamAttemptMs
      );
    }
  }

  /**
   * Handles price endpoint requests with atomic cross-isolate coordination,
   * quota enforcement, and request coalescing.
   */
  public async handlePriceRequest(options: { force?: boolean } = {}): Promise<{
    body: HawklyxPriceResponse;
    status: number;
  }> {
    if (this.initialized) {
      await this.initialized;
    }

    const nowMs = Date.now();
    this.checkAndPersistDayReset(nowMs);

    const isForced = Boolean(options.force);
    const cachedKnownGood = this.cacheManager.getLastKnownGood();
    const cachedAny = this.cacheManager.getObservation();
    const isFresh = this.cacheManager.isFresh(nowMs);
    const isInCooldown = this.cacheManager.isInCooldown(nowMs);

    // Scenario 1: Cache is fresh and request was not forced
    if (isFresh && !isForced && cachedKnownGood) {
      return {
        body: {
          status: "fresh",
          data: cachedKnownGood,
          message: "Brent crude price observation is up to date.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
        },
        status: 200,
      };
    }

    // Scenario 2: Forced refresh requested, but manual refresh cooldown is active
    if (isForced && isInCooldown && cachedAny) {
      return {
        body: {
          status: isFresh ? "fresh" : "stale",
          data: cachedKnownGood || cachedAny,
          message: "Manual refresh is cooling down. Showing latest available price.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
        },
        status: 200,
      };
    }

    // Scenario 3: Non-forced request, but upstream attempt is currently in cooldown
    // Ordinary requests must not bypass upstream-fetch limits when the cache expires
    if (!isForced && isInCooldown && cachedAny) {
      return {
        body: {
          status: isFresh ? "fresh" : "stale",
          data: cachedKnownGood || cachedAny,
          message: "Recent upstream attempt is cooling down. Showing latest available price.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
        },
        status: 200,
      };
    }

    // Scenario 4: Upstream fetch needed. Coalesce concurrent requests.
    if (this.inFlightFetchPromise) {
      return await this.inFlightFetchPromise;
    }

    this.inFlightFetchPromise = this.performCoalescedFetch(isForced);
    try {
      return await this.inFlightFetchPromise;
    } finally {
      this.inFlightFetchPromise = null;
    }
  }

  private async performCoalescedFetch(isForced: boolean): Promise<{
    body: HawklyxPriceResponse;
    status: number;
  }> {
    const coalescedNow = Date.now();
    this.checkAndPersistDayReset(coalescedNow);

    // Re-check freshness in case a concurrent coalesced call just completed
    if (this.cacheManager.isFresh(coalescedNow) && !isForced) {
      const freshCached = this.cacheManager.getLastKnownGood()!;
      return {
        body: {
          status: "fresh",
          data: freshCached,
          message: "Brent crude price observation is up to date.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(coalescedNow),
          server_time: new Date(coalescedNow).toISOString(),
        },
        status: 200,
      };
    }

    // Check global daily budget
    if (this.accountPool.isGlobalBudgetExhausted()) {
      return this.createFallbackResponse(
        "quota_exhausted",
        "Price updates are temporarily unavailable due to daily quota. Showing last valid price."
      );
    }

    const activeCount = this.accountPool.getActiveAccountsCount(coalescedNow);
    if (activeCount === 0) {
      return this.createFallbackResponse(
        "quota_exhausted",
        "Price updates are temporarily unavailable due to daily quota. Showing last valid price."
      );
    }

    let lastError: Error | null = null;
    let attempts = 0;
    const maxAttempts = Math.max(1, activeCount);

    while (attempts < maxAttempts) {
      const tickNow = Date.now();
      this.checkAndPersistDayReset(tickNow);

      if (this.accountPool.isGlobalBudgetExhausted()) {
        break;
      }

      const account = this.accountPool.selectEligibleAccount(tickNow);
      if (!account) {
        break;
      }

      attempts++;
      this.accountPool.recordRequest(account.slot, tickNow);
      this.persistAccountUpdate(account.slot);

      this.cacheManager.recordUpstreamAttempt(tickNow);
      this.persistCacheState();

      try {
        const observation = await this.provider.fetchLatestBrentPrice(account.key);

        // Explicit and safe handling for stale/synthetic responses:
        // Do NOT overwrite last known-good valid observation!
        if (observation.is_stale || observation.is_synthetic) {
          this.cacheManager.storeObservation(observation, Date.now());
          this.persistCacheState();

          const dataToServe = this.cacheManager.getLastKnownGood() || observation;
          return {
            body: {
              status: "stale",
              data: dataToServe,
              message: "Provider returned stale/synthetic market observation. Preserving last known valid price.",
              next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(Date.now()),
              server_time: new Date().toISOString(),
            },
            status: 200,
          };
        }

        // Fresh, non-stale, non-synthetic observation
        this.cacheManager.storeObservation(observation, Date.now());
        this.persistCacheState();

        return {
          body: {
            status: "fresh",
            data: observation,
            message: "Brent crude price updated successfully from provider.",
            next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(Date.now()),
            server_time: new Date().toISOString(),
          },
          status: 200,
        };
      } catch (err: unknown) {
        lastError = err instanceof Error ? err : new Error(String(err));

        if (err instanceof ProviderAuthError) {
          this.accountPool.markInvalidAuth(account.slot);
          this.persistAccountUpdate(account.slot);
        } else if (err instanceof ProviderQuotaExhaustedError) {
          this.accountPool.markExhausted(account.slot, err.resetTimeMs);
          this.persistAccountUpdate(account.slot);
        } else if (err instanceof ProviderRateLimitedError) {
          this.accountPool.markRateLimited(account.slot, err.retryAfterSeconds);
          this.persistAccountUpdate(account.slot);
        } else if (err instanceof ProviderServerError || err instanceof ProviderValidationError) {
          // Server or validation error: stop rotation for this tick
          break;
        }
      }
    }

    // Upstream fetch failed
    let status: HawklyxPriceResponse["status"] = "stale";
    let message = "Price updates are temporarily unavailable. Showing last known valid price.";

    const fallbackNow = Date.now();
    if (this.accountPool.isGlobalBudgetExhausted() || this.accountPool.getActiveAccountsCount(fallbackNow) === 0) {
      status = "quota_exhausted";
      message = "Price updates are temporarily unavailable due to daily quota. Showing last valid price.";
    } else if (lastError instanceof ProviderServerError) {
      status = "provider_unavailable";
      message = "Upstream market data provider is temporarily unavailable. Showing last valid price.";
    } else if (lastError instanceof ProviderValidationError) {
      status = "invalid_provider_response";
      message = "Received unexpected data from provider. Showing last valid price.";
    }

    return this.createFallbackResponse(status, message);
  }

  private createFallbackResponse(
    status: HawklyxPriceResponse["status"],
    message: string
  ): { body: HawklyxPriceResponse; status: number } {
    const fallbackNow = Date.now();
    const cached = this.cacheManager.getLastKnownGood() || this.cacheManager.getObservation();

    if (cached) {
      return {
        body: {
          status,
          data: cached,
          message,
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(fallbackNow),
          server_time: new Date(fallbackNow).toISOString(),
        },
        status: 200,
      };
    }

    return {
      body: {
        status: "no_data",
        data: null,
        message: "Price unavailable — we could not retrieve a valid Brent price yet. Please try again later.",
        next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(fallbackNow),
        server_time: new Date(fallbackNow).toISOString(),
      },
      status: 503,
    };
  }

  /**
   * Returns sanitized system and account health metrics without exposing secrets.
   */
  public async getHealth(): Promise<HealthStatusResponse> {
    if (this.initialized) {
      await this.initialized;
    }
    const nowMs = Date.now();
    this.checkAndPersistDayReset(nowMs);

    const activeCount = this.accountPool.getActiveAccountsCount(nowMs);
    const configuredCount = this.accountPool.getAccountsCount();
    const isExhausted =
      this.accountPool.isGlobalBudgetExhausted() || (configuredCount > 0 && activeCount === 0);

    const lastGood = this.cacheManager.getLastKnownGood();
    const lastObs = this.cacheManager.getObservation();
    const effectiveCached = lastGood || lastObs;

    return {
      status: isExhausted ? "exhausted" : activeCount < configuredCount ? "degraded" : "ok",
      service: "HAWKLYX Edge Service",
      version: "1.0.0",
      configured_accounts: configuredCount,
      active_accounts: activeCount,
      total_requests_today: this.accountPool.getTotalUsedToday(),
      daily_budget: parseInt(this.env.GLOBAL_DAILY_BUDGET || "200", 10),
      has_cached_observation: effectiveCached !== null,
      last_successful_fetch: this.cacheManager.getLastSuccessfulFetchMs()
        ? new Date(this.cacheManager.getLastSuccessfulFetchMs()!).toISOString()
        : null,
      cache_age_seconds: this.cacheManager.getCacheAgeSeconds(nowMs),
      accounts: this.accountPool.getSanitizedHealth(nowMs),
    };
  }
}
