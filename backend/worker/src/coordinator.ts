import { DurableObject } from "cloudflare:workers";
import { AccountPoolManager, QuarantineReason } from "./account-pool";
import { CacheManager } from "./cache";
import { getMarketState } from "./market-calendar";
import {
  OilPriceProvider,
  ProviderAuthError,
  ProviderQuotaExhaustedError,
  ProviderRateLimitedError,
  ProviderServerError,
  ProviderValidationError,
} from "./provider";
import {
  Env,
  HawklyxHistoryResponse,
  HawklyxPriceResponse,
  HealthStatusResponse,
  HistoricalDataPoint,
  HistorySummary,
  MarketStateInfo,
  ValidatedObservation,
} from "./types";

export class HawklyxCoordinator extends DurableObject<Env> {
  private accountPool: AccountPoolManager;
  private cacheManager: CacheManager;
  private provider: OilPriceProvider;
  private inFlightFetchPromise: Promise<{ body: HawklyxPriceResponse; status: number }> | null = null;
  private initialized: Promise<void> | null = null;
  private nowProvider: (() => number) | null = null;

  constructor(ctx: DurableObjectState, env: Env, config: { now?: () => number } = {}) {
    super(ctx, env);
    this.nowProvider = config.now || null;
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

  public setNowProvider(fn: (() => number) | null): void {
    this.nowProvider = fn;
  }

  public getNow(): number {
    return this.nowProvider ? this.nowProvider() : Date.now();
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
    sql.exec(`
      CREATE TABLE IF NOT EXISTS price_observations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        instrument TEXT NOT NULL,
        bucket_hour_utc TEXT NOT NULL,
        price REAL NOT NULL,
        currency TEXT NOT NULL,
        unit TEXT NOT NULL,
        as_of TEXT NOT NULL,
        fetched_at TEXT NOT NULL,
        source TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        UNIQUE(instrument, bucket_hour_utc)
      )
    `);
    sql.exec(`
      CREATE INDEX IF NOT EXISTS idx_price_obs_range 
      ON price_observations (instrument, bucket_hour_utc ASC)
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS price_daily_rollups (
        instrument TEXT NOT NULL,
        trading_date_utc TEXT NOT NULL,
        open REAL NOT NULL,
        high REAL NOT NULL,
        low REAL NOT NULL,
        close REAL NOT NULL,
        first_as_of TEXT NOT NULL,
        last_as_of TEXT NOT NULL,
        sample_count INTEGER NOT NULL,
        is_complete INTEGER NOT NULL DEFAULT 1,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(instrument, trading_date_utc)
      )
    `);
    sql.exec(`
      CREATE INDEX IF NOT EXISTS idx_price_rollups_date 
      ON price_daily_rollups (instrument, trading_date_utc ASC)
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
    const automaticUsedToday = systemMap.get("automatic_used_today")
      ? parseInt(systemMap.get("automatic_used_today")!, 10)
      : 0;
    const manualUsedToday = systemMap.get("manual_used_today")
      ? parseInt(systemMap.get("manual_used_today")!, 10)
      : 0;
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
      lastResetDay,
      {
        automaticUsedToday,
        manualUsedToday,
      }
    );

    this.checkAndPersistDayReset(this.getNow());
    this.generateDailyRollups(this.getNow());
    this.cleanupOldObservations(this.getNow());
    this.scheduleNextMidnightAlarm(this.getNow());
  }

  /**
   * Cloudflare Durable Objects Alarm handler.
   * Runs daily maintenance (day reset, rollup generation, bounded cleanup)
   * even if zero external HTTP requests are received around midnight UTC.
   */
  public async alarm(): Promise<void> {
    const nowMs = this.getNow();
    this.checkAndPersistDayReset(nowMs);
    this.generateDailyRollups(nowMs);
    this.cleanupOldObservations(nowMs);
    this.scheduleNextMidnightAlarm(nowMs);
  }

  private scheduleNextMidnightAlarm(nowMs: number = this.getNow()): void {
    if (!this.ctx?.storage || typeof (this.ctx.storage as any).setAlarm !== "function") return;
    const tomorrow = new Date(nowMs);
    tomorrow.setUTCHours(24, 0, 5, 0); // 5 seconds past next UTC midnight
    (this.ctx.storage as any).setAlarm(tomorrow.getTime()).catch(() => {});
  }

  private checkAndPersistDayReset(nowMs: number = this.getNow()): void {
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
        sql.exec(
          "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
          "automatic_used_today",
          "0"
        );
        sql.exec(
          "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
          "manual_used_today",
          "0"
        );
        sql.exec(`
          UPDATE account_usage
          SET used_today = 0,
              quarantined_until = CASE WHEN quarantine_reason = 'EXHAUSTED' THEN NULL ELSE quarantined_until END,
              quarantine_reason = CASE WHEN quarantine_reason = 'EXHAUSTED' THEN NULL ELSE quarantine_reason END
        `);
        this.generateDailyRollups(nowMs);
        this.cleanupOldObservations(nowMs);
      }
      this.scheduleNextMidnightAlarm(nowMs);
    }
  }

  private persistUsageStats(): void {
    if (!this.ctx?.storage?.sql) return;
    const sql = this.ctx.storage.sql;
    sql.exec(
      "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
      "automatic_used_today",
      String(this.accountPool.getAutomaticUsedToday())
    );
    sql.exec(
      "INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)",
      "manual_used_today",
      String(this.accountPool.getManualUsedToday())
    );
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
   * Persists at most one canonical observation per instrument per UTC hour.
   * Concurrency-safe and strictly rejects stale/synthetic observations.
   * Ensures that unchanged source timestamps or repeated cached responses do NOT create false observations.
   */
  public recordHourlyObservation(observation: ValidatedObservation, nowMs: number = this.getNow()): boolean {
    if (!this.ctx?.storage?.sql) return false;
    if (observation.is_stale || observation.is_synthetic) return false;

    // Validate as_of source timestamp: must be present and parseable
    if (!observation.as_of || typeof observation.as_of !== "string") {
      return false;
    }
    const asOfMs = Date.parse(observation.as_of);
    if (isNaN(asOfMs)) {
      return false;
    }

    // Future guard: reject observations claiming to be > 5 minutes in the future
    if (asOfMs > nowMs + 5 * 60 * 1000) {
      return false;
    }

    // Stale guard: reject observations with as_of older than 7 days as fresh live observations
    if (nowMs - asOfMs > 7 * 24 * 3600 * 1000) {
      return false;
    }

    const sql = this.ctx.storage.sql;
    // Canonical UTC hour bucket is strictly derived from the genuine market observation time (as_of)
    const bucketHourUtc = new Date(asOfMs).toISOString().slice(0, 13) + ":00:00Z";
    const instrument = (observation.code || "BRENT_CRUDE_USD").toUpperCase().trim();

    // Guard: Has this exact source timestamp (as_of) already been recorded?
    // This prevents repeated cached data, unchanged market timestamps across hour transitions,
    // or identical duplicate responses from fabricating observations.
    const existingAsOf = sql.exec<{ id: number }>(
      "SELECT id FROM price_observations WHERE instrument = ? AND as_of = ? LIMIT 1",
      instrument,
      observation.as_of
    ).toArray();

    if (existingAsOf.length > 0) {
      return false;
    }

    // Insert or update within the canonical hourly bucket.
    // If a point already exists for this (instrument, bucket_hour_utc), prefer the latest eligible observation
    // where the source timestamp (as_of) has advanced.
    sql.exec(`
      INSERT INTO price_observations (
        instrument, bucket_hour_utc, price, currency, unit, as_of, fetched_at, source, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(instrument, bucket_hour_utc) DO UPDATE SET
        price = excluded.price,
        currency = excluded.currency,
        unit = excluded.unit,
        as_of = excluded.as_of,
        fetched_at = excluded.fetched_at,
        source = excluded.source,
        created_at = excluded.created_at
      WHERE excluded.as_of > price_observations.as_of
    `,
      instrument,
      bucketHourUtc,
      observation.price,
      observation.currency,
      observation.unit,
      observation.as_of,
      observation.fetched_at,
      observation.source,
      nowMs
    );

    // Track collection start date in system_state if not already recorded
    sql.exec(`
      INSERT OR IGNORE INTO system_state (key, value)
      VALUES ('collection_start_date', ?)
    `, observation.as_of || bucketHourUtc);

    return true;
  }

  /**
   * Aggregates completed past calendar days into verified daily OHLC rollups.
   */
  private generateDailyRollups(nowMs: number = this.getNow()): void {
    if (!this.ctx?.storage?.sql) return;
    const sql = this.ctx.storage.sql;
    const currentDay = new Date(nowMs).toISOString().slice(0, 10);

    // Roll up all completed calendar days strictly prior to the current day
    sql.exec(`
      INSERT OR REPLACE INTO price_daily_rollups (
        instrument, trading_date_utc, open, high, low, close, first_as_of, last_as_of, sample_count, is_complete, updated_at
      )
      SELECT 
        p1.instrument,
        substr(p1.bucket_hour_utc, 1, 10) AS trading_date_utc,
        (SELECT p2.price FROM price_observations p2 WHERE p2.instrument = p1.instrument AND substr(p2.bucket_hour_utc, 1, 10) = substr(p1.bucket_hour_utc, 1, 10) ORDER BY p2.bucket_hour_utc ASC LIMIT 1) AS open,
        MAX(p1.price) AS high,
        MIN(p1.price) AS low,
        (SELECT p3.price FROM price_observations p3 WHERE p3.instrument = p1.instrument AND substr(p3.bucket_hour_utc, 1, 10) = substr(p1.bucket_hour_utc, 1, 10) ORDER BY p3.bucket_hour_utc DESC LIMIT 1) AS close,
        MIN(p1.as_of) AS first_as_of,
        MAX(p1.as_of) AS last_as_of,
        COUNT(*) AS sample_count,
        CASE WHEN COUNT(*) >= 20 THEN 1 ELSE 0 END AS is_complete,
        ? AS updated_at
      FROM price_observations p1
      WHERE substr(p1.bucket_hour_utc, 1, 10) < ?
      GROUP BY p1.instrument, substr(p1.bucket_hour_utc, 1, 10)
    `, nowMs, currentDay);
  }

  /**
   * Performs bounded cleanup of raw hourly observations older than 30 days
   * that have already been aggregated into price_daily_rollups.
   */
  private cleanupOldObservations(nowMs: number = this.getNow()): void {
    if (!this.ctx?.storage?.sql) return;
    const sql = this.ctx.storage.sql;
    const thirtyDaysAgoIso = new Date(nowMs - 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);

    // Bounded cleanup: only delete raw hourly observations older than 30 days
    // that have already been safely aggregated in price_daily_rollups
    sql.exec(`
      DELETE FROM price_observations
      WHERE substr(bucket_hour_utc, 1, 10) < ?
        AND substr(bucket_hour_utc, 1, 10) IN (
          SELECT trading_date_utc FROM price_daily_rollups WHERE price_daily_rollups.instrument = price_observations.instrument
        )
    `, thirtyDaysAgoIso);
  }

  /**
   * Handles price endpoint requests with atomic cross-isolate coordination,
   * quota enforcement, market closure awareness, and request coalescing.
   */
  public async handlePriceRequest(options: { force?: boolean; requestClass?: "automatic" | "manual" } = {}): Promise<{
    body: HawklyxPriceResponse;
    status: number;
  }> {
    if (this.initialized) {
      await this.initialized;
    }

    const nowMs = this.getNow();
    this.checkAndPersistDayReset(nowMs);

    const isForced = Boolean(options.force);
    const requestClass: "automatic" | "manual" = options.requestClass || (isForced ? "manual" : "automatic");
    const cachedKnownGood = this.cacheManager.getLastKnownGood();
    const cachedAny = this.cacheManager.getObservation();
    const isFresh = this.cacheManager.isFresh(nowMs);
    const isInCooldown = this.cacheManager.isInCooldown(nowMs);
    const marketState = getMarketState(nowMs);

    // --- MARKET CLOSURE AWARENESS ---
    if (!marketState.is_open) {
      // Automatic/scheduled request during market closure: DO NOT poll upstream!
      // Strictly preserves quota & the protected 110-request automatic reserve.
      // Handles both warm cache and empty cold-start cache without upstream polling.
      if (requestClass === "automatic") {
        return {
          body: {
            status: "market_closed",
            data: cachedKnownGood || cachedAny,
            message: cachedKnownGood || cachedAny
              ? `${marketState.description} Showing latest available price.`
              : `${marketState.description} Price collection will begin when trading resumes.`,
            next_refresh_allowed_at: marketState.next_open_at || this.cacheManager.getNextRefreshAllowedIso(nowMs),
            server_time: new Date(nowMs).toISOString(),
            market: marketState,
          },
          status: 200,
        };
      }

      // Forced manual refresh during market closure:
      if (isForced && isInCooldown && (cachedKnownGood || cachedAny)) {
        return {
          body: {
            status: "market_closed",
            data: cachedKnownGood || cachedAny,
            message: "Manual refresh is cooling down. Market is closed.",
            next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
            server_time: new Date(nowMs).toISOString(),
            market: marketState,
          },
          status: 200,
        };
      }
    }

    // Scenario 1: Cache is fresh and request was not forced
    if (isFresh && !isForced && cachedKnownGood) {
      return {
        body: {
          status: marketState.is_open ? "fresh" : "market_closed",
          data: cachedKnownGood,
          message: marketState.is_open
            ? "Brent crude price observation is up to date."
            : `${marketState.description} Showing latest available price.`,
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
          market: marketState,
        },
        status: 200,
      };
    }

    // Scenario 2: Forced refresh requested, but manual refresh cooldown is active
    if (isForced && isInCooldown && cachedAny) {
      return {
        body: {
          status: !marketState.is_open ? "market_closed" : (isFresh ? "fresh" : "stale"),
          data: cachedKnownGood || cachedAny,
          message: "Manual refresh is cooling down. Showing latest available price.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
          market: marketState,
        },
        status: 200,
      };
    }

    // Scenario 3: Non-forced request, but upstream attempt is currently in cooldown
    // Ordinary requests must not bypass upstream-fetch limits when the cache expires
    if (!isForced && isInCooldown && cachedAny) {
      return {
        body: {
          status: !marketState.is_open ? "market_closed" : (isFresh ? "fresh" : "stale"),
          data: cachedKnownGood || cachedAny,
          message: "Recent upstream attempt is cooling down. Showing latest available price.",
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
          market: marketState,
        },
        status: 200,
      };
    }

    // Scenario 4: Upstream fetch needed. Coalesce concurrent requests.
    if (this.inFlightFetchPromise) {
      return await this.inFlightFetchPromise;
    }

    this.inFlightFetchPromise = this.performCoalescedFetch(isForced, requestClass, marketState);
    try {
      return await this.inFlightFetchPromise;
    } finally {
      this.inFlightFetchPromise = null;
    }
  }

  private async performCoalescedFetch(
    isForced: boolean,
    requestClass: "automatic" | "manual",
    marketState: MarketStateInfo
  ): Promise<{
    body: HawklyxPriceResponse;
    status: number;
  }> {
    const coalescedNow = this.getNow();
    this.checkAndPersistDayReset(coalescedNow);

    // Re-check freshness in case a concurrent coalesced call just completed
    if (this.cacheManager.isFresh(coalescedNow) && !isForced) {
      const freshCached = this.cacheManager.getLastKnownGood()!;
      return {
        body: {
          status: marketState.is_open ? "fresh" : "market_closed",
          data: freshCached,
          message: marketState.is_open
            ? "Brent crude price observation is up to date."
            : `${marketState.description} Showing latest available price.`,
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(coalescedNow),
          server_time: new Date(coalescedNow).toISOString(),
          market: marketState,
        },
        status: 200,
      };
    }

    // Check budget limit for this specific request class!
    // Manual requests cannot consume the 110-request protected automatic reserve.
    if (this.accountPool.isBudgetExhaustedFor(requestClass)) {
      const quotaMsg = requestClass === "manual"
        ? "Manual refresh limit reached for today. Protected automatic updates remain active."
        : "Daily upstream API quota limit reached. Showing last valid price.";
      return this.createFallbackResponse("quota_exhausted", quotaMsg, marketState);
    }

    const activeCount = this.accountPool.getActiveAccountsCount(coalescedNow);
    if (activeCount === 0) {
      return this.createFallbackResponse(
        "quota_exhausted",
        "Price updates are temporarily unavailable due to daily quota. Showing last valid price.",
        marketState
      );
    }

    let lastError: Error | null = null;
    let attempts = 0;
    const maxAttempts = Math.max(1, activeCount);

    while (attempts < maxAttempts) {
      const tickNow = this.getNow();
      this.checkAndPersistDayReset(tickNow);

      if (this.accountPool.isBudgetExhaustedFor(requestClass)) {
        break;
      }

      const account = this.accountPool.selectEligibleAccount(tickNow, requestClass);
      if (!account) {
        break;
      }

      attempts++;
      this.accountPool.recordRequest(account.slot, requestClass, tickNow);
      this.persistAccountUpdate(account.slot);
      this.persistUsageStats();

      this.cacheManager.recordUpstreamAttempt(tickNow);
      this.persistCacheState();

      try {
        const observation = await this.provider.fetchLatestBrentPrice(account.key);

        // Explicit and safe handling for stale/synthetic responses:
        // Do NOT overwrite last known-good valid observation!
        if (observation.is_stale || observation.is_synthetic) {
          this.cacheManager.storeObservation(observation, this.getNow());
          this.persistCacheState();

          const dataToServe = this.cacheManager.getLastKnownGood() || observation;
          return {
            body: {
              status: !marketState.is_open ? "market_closed" : "stale",
              data: dataToServe,
              message: !marketState.is_open
                ? `${marketState.description} Showing latest price.`
                : "Provider returned stale/synthetic market observation. Preserving last known valid price.",
              next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(this.getNow()),
              server_time: new Date(this.getNow()).toISOString(),
              market: marketState,
            },
            status: 200,
          };
        }

        // Fresh, non-stale, non-synthetic observation
        this.cacheManager.storeObservation(observation, this.getNow());
        this.persistCacheState();
        this.recordHourlyObservation(observation, this.getNow());

        return {
          body: {
            status: !marketState.is_open ? "market_closed" : "fresh",
            data: observation,
            message: !marketState.is_open
              ? `${marketState.description} Preserving latest price.`
              : "Brent crude price updated successfully from provider.",
            next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(this.getNow()),
            server_time: new Date(this.getNow()).toISOString(),
            market: marketState,
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
    let status: HawklyxPriceResponse["status"] = !marketState.is_open ? "market_closed" : "stale";
    let message = "Price updates are temporarily unavailable. Showing last known valid price.";

    const fallbackNow = this.getNow();
    if (this.accountPool.isBudgetExhaustedFor(requestClass) || this.accountPool.getActiveAccountsCount(fallbackNow) === 0) {
      status = "quota_exhausted";
      message = requestClass === "manual" && this.accountPool.isManualBudgetExhausted()
        ? "Manual refresh limit reached for today. Protected automatic updates remain active."
        : "Price updates are temporarily unavailable due to daily quota. Showing last valid price.";
    } else if (lastError instanceof ProviderServerError) {
      status = "provider_unavailable";
      message = "Upstream market data provider is temporarily unavailable. Showing last valid price.";
    } else if (lastError instanceof ProviderValidationError) {
      status = "invalid_provider_response";
      message = "Received unexpected data from provider. Showing last valid price.";
    }

    return this.createFallbackResponse(status, message, marketState);
  }

  private createFallbackResponse(
    status: HawklyxPriceResponse["status"],
    message: string,
    market?: MarketStateInfo
  ): { body: HawklyxPriceResponse; status: number } {
    const fallbackNow = this.getNow();
    const cached = this.cacheManager.getLastKnownGood() || this.cacheManager.getObservation();

    if (cached) {
      return {
        body: {
          status,
          data: cached,
          message,
          next_refresh_allowed_at: this.cacheManager.getNextRefreshAllowedIso(fallbackNow),
          server_time: new Date(fallbackNow).toISOString(),
          market,
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
        market,
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
    const nowMs = this.getNow();
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
      daily_budget: this.accountPool.getGlobalDailyBudget(),
      automatic_requests_today: this.accountPool.getAutomaticUsedToday(),
      manual_requests_today: this.accountPool.getManualUsedToday(),
      automatic_reserve: this.accountPool.getAutomaticReserveBudget(),
      manual_budget_cap: this.accountPool.getManualBudgetCap(),
      has_cached_observation: effectiveCached !== null,
      last_successful_fetch: this.cacheManager.getLastSuccessfulFetchMs()
        ? new Date(this.cacheManager.getLastSuccessfulFetchMs()!).toISOString()
        : null,
      cache_age_seconds: this.cacheManager.getCacheAgeSeconds(nowMs),
      accounts: this.accountPool.getSanitizedHealth(nowMs),
    };
  }

  /**
   * Strictly read-only history endpoint handler.
   * Reads canonical hourly and aggregate records from SQLite storage.
   * NEVER calls upstream provider, NEVER increments quotas, and NEVER triggers cooldowns.
   */
  public async handleHistoryRequest(options: {
    range?: string;
    resolution?: string;
    instrument?: string;
  } = {}): Promise<{
    body: HawklyxHistoryResponse;
    status: number;
  }> {
    if (this.initialized) {
      await this.initialized;
    }

    const nowMs = this.getNow();
    const instrument = (options.instrument || "BRENT_CRUDE_USD").toUpperCase().trim();
    const rawRange = (options.range || "30d").toLowerCase().trim();
    const validRanges = ["1d", "7d", "30d", "6m", "1y"];
    const range = validRanges.includes(rawRange) ? rawRange : "30d";

    const rawResolution = (options.resolution || "auto").toLowerCase().trim();
    const validResolutions = ["auto", "hourly", "daily"];
    let resolution = validResolutions.includes(rawResolution) ? rawResolution : "auto";

    if (resolution === "auto") {
      resolution = (range === "6m" || range === "1y") ? "daily" : "hourly";
    }

    // Calculate time window in milliseconds
    const rangeDurationMs: Record<string, number> = {
      "1d": 24 * 3600 * 1000,
      "7d": 7 * 24 * 3600 * 1000,
      "30d": 30 * 24 * 3600 * 1000,
      "6m": 180 * 24 * 3600 * 1000,
      "1y": 365 * 24 * 3600 * 1000,
    };
    const durationMs = rangeDurationMs[range] || (30 * 24 * 3600 * 1000);
    const startWindowIso = new Date(nowMs - durationMs).toISOString();
    const endWindowIso = new Date(nowMs).toISOString();

    const sql = this.ctx?.storage?.sql;
    if (!sql) {
      return {
        body: {
          status: "empty",
          instrument,
          currency: "USD",
          unit: "USD / barrel",
          range,
          resolution,
          collection_start_date: null,
          earliest_timestamp: null,
          latest_timestamp: null,
          count: 0,
          points: [],
          summary: null,
          is_partial: false,
          server_time: new Date(nowMs).toISOString(),
          message: "Persistence storage is unavailable.",
        },
        status: 200,
      };
    }

    // Determine collection start date from system_state or min bucket
    let collectionStartDate: string | null = null;
    const startRows = sql.exec<{ value: string }>(
      "SELECT value FROM system_state WHERE key = 'collection_start_date'"
    ).toArray();
    if (startRows.length > 0 && startRows[0].value) {
      collectionStartDate = startRows[0].value;
    } else {
      const minRow = sql.exec<{ min_bucket: string | null }>(
        "SELECT MIN(bucket_hour_utc) AS min_bucket FROM price_observations WHERE instrument = ?",
        instrument
      ).toArray();
      if (minRow.length > 0 && minRow[0].min_bucket) {
        collectionStartDate = minRow[0].min_bucket;
      }
    }

    const points: HistoricalDataPoint[] = [];

    if (resolution === "hourly") {
      const rows = sql.exec<{
        bucket_hour_utc: string;
        price: number;
        currency: string;
        unit: string;
        as_of: string;
        fetched_at: string;
      }>(
        `SELECT bucket_hour_utc, price, currency, unit, as_of, fetched_at 
         FROM price_observations 
         WHERE instrument = ? AND bucket_hour_utc >= ? AND bucket_hour_utc <= ? 
         ORDER BY bucket_hour_utc ASC`,
        instrument,
        startWindowIso.slice(0, 13) + ":00:00Z",
        endWindowIso.slice(0, 13) + ":00:00Z"
      ).toArray();

      for (const row of rows) {
        points.push({
          timestamp: row.bucket_hour_utc,
          price: row.price,
          formatted: `$${row.price.toFixed(2)}`,
          as_of: row.as_of,
          fetched_at: row.fetched_at,
        });
      }
    } else {
      // Daily resolution: query completed rollups unioned with unrolled recent observations
      const startDateIso = startWindowIso.slice(0, 10);
      const endDateIso = endWindowIso.slice(0, 10);

      const rollupRows = sql.exec<{
        trading_date_utc: string;
        open: number;
        high: number;
        low: number;
        close: number;
        first_as_of: string;
        last_as_of: string;
        sample_count: number;
        is_complete: number;
      }>(
        `SELECT trading_date_utc, open, high, low, close, first_as_of, last_as_of, sample_count, is_complete 
         FROM price_daily_rollups 
         WHERE instrument = ? AND trading_date_utc >= ? AND trading_date_utc <= ? 
         ORDER BY trading_date_utc ASC`,
        instrument,
        startDateIso,
        endDateIso
      ).toArray();

      const rollupDates = new Set(rollupRows.map((r) => r.trading_date_utc));

      for (const row of rollupRows) {
        points.push({
          timestamp: row.trading_date_utc,
          price: row.close,
          formatted: `$${row.close.toFixed(2)}`,
          as_of: row.last_as_of,
          fetched_at: row.last_as_of,
          open: row.open,
          high: row.high,
          low: row.low,
          close: row.close,
          sample_count: row.sample_count,
          is_complete: Boolean(row.is_complete),
        });
      }

      // Aggregate unrolled days from price_observations (e.g. today's ongoing day)
      const rawRows = sql.exec<{
        bucket_hour_utc: string;
        price: number;
        as_of: string;
      }>(
        `SELECT bucket_hour_utc, price, as_of 
         FROM price_observations 
         WHERE instrument = ? AND substr(bucket_hour_utc, 1, 10) >= ? AND substr(bucket_hour_utc, 1, 10) <= ? 
         ORDER BY bucket_hour_utc ASC`,
        instrument,
        startDateIso,
        endDateIso
      ).toArray();

      const unrolledByDate = new Map<string, Array<{ price: number; as_of: string }>>();
      for (const row of rawRows) {
        const date = row.bucket_hour_utc.slice(0, 10);
        if (!rollupDates.has(date)) {
          const list = unrolledByDate.get(date) || [];
          list.push({ price: row.price, as_of: row.as_of });
          unrolledByDate.set(date, list);
        }
      }

      for (const [date, obsList] of unrolledByDate.entries()) {
        const prices = obsList.map((o) => o.price);
        const open = obsList[0].price;
        const close = obsList[obsList.length - 1].price;
        const high = Math.max(...prices);
        const low = Math.min(...prices);
        points.push({
          timestamp: date,
          price: close,
          formatted: `$${close.toFixed(2)}`,
          as_of: obsList[obsList.length - 1].as_of,
          fetched_at: obsList[obsList.length - 1].as_of,
          open,
          high,
          low,
          close,
          sample_count: obsList.length,
          is_complete: obsList.length >= 20,
        });
      }

      points.sort((a, b) => a.timestamp.localeCompare(b.timestamp));
    }

    // Determine if history is partial relative to requested range
    const isPartial = collectionStartDate !== null && startWindowIso < collectionStartDate;

    let summary: HistorySummary | null = null;
    if (points.length > 0) {
      const firstPrice = points[0].open ?? points[0].price;
      const latestPrice = points[points.length - 1].close ?? points[points.length - 1].price;
      const changeAbs = Math.round((latestPrice - firstPrice) * 100) / 100;
      const changePct = firstPrice > 0
        ? Math.round(((latestPrice - firstPrice) / firstPrice) * 10000) / 100
        : 0;
      const high = Math.max(...points.map((p) => p.high ?? p.price));
      const low = Math.min(...points.map((p) => p.low ?? p.price));

      summary = {
        first_price: firstPrice,
        latest_price: latestPrice,
        change_absolute: changeAbs,
        change_percent: changePct,
        high,
        low,
      };
    }

    return {
      body: {
        status: points.length > 0 ? "success" : "empty",
        instrument,
        currency: "USD",
        unit: "USD / barrel",
        range,
        resolution,
        collection_start_date: collectionStartDate,
        earliest_timestamp: points.length > 0 ? points[0].timestamp : null,
        latest_timestamp: points.length > 0 ? points[points.length - 1].timestamp : null,
        count: points.length,
        points,
        summary,
        is_partial: isPartial,
        server_time: new Date(nowMs).toISOString(),
        message: points.length === 0
          ? "Historical data is accumulating. Showing empty history."
          : undefined,
      },
      status: 200,
    };
  }
}

