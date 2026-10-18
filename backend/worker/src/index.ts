import { AccountPoolManager } from "./account-pool";
import { CacheManager } from "./cache";
import {
  OilPriceProvider,
  ProviderAuthError,
  ProviderQuotaExhaustedError,
  ProviderRateLimitedError,
  ProviderServerError,
  ProviderValidationError,
} from "./provider";
import { Env, HawklyxPriceResponse, HealthStatusResponse } from "./types";

// Module-level singletons for in-memory persistence during worker isolate lifetime
let accountPool: AccountPoolManager | null = null;
let cacheManager: CacheManager | null = null;
let provider: OilPriceProvider | null = null;

function getServices(env: Env) {
  if (!accountPool) {
    accountPool = new AccountPoolManager(env);
  }
  if (!cacheManager) {
    const freshnessMin = parseInt(env.DEFAULT_UPSTREAM_INTERVAL_MINUTES || "15", 10);
    const cooldownSec = parseInt(env.MANUAL_REFRESH_COOLDOWN_SECONDS || "180", 10);
    cacheManager = new CacheManager({
      freshnessIntervalMinutes: freshnessMin,
      cooldownSeconds: cooldownSec,
    });
  }
  if (!provider) {
    const sanityMin = parseFloat(env.PRICE_SANITY_MIN || "10");
    const sanityMax = parseFloat(env.PRICE_SANITY_MAX || "250");
    provider = new OilPriceProvider({
      sanityMinPrice: sanityMin,
      sanityMaxPrice: sanityMax,
    });
  }
  return { accountPool, cacheManager, provider };
}

function jsonResponse(data: unknown, status: number = 200): Response {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Cache-Control": "no-store, no-cache, must-revalidate",
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    const url = new URL(request.url);
    const pathname = url.pathname;
    const nowMs = Date.now();
    const { accountPool, cacheManager, provider } = getServices(env);

    // Route: /health
    if (pathname === "/health") {
      const activeCount = accountPool.getActiveAccountsCount(nowMs);
      const configuredCount = accountPool.getAccountsCount();
      const isExhausted = accountPool.isGlobalBudgetExhausted() || (configuredCount > 0 && activeCount === 0);

      const health: HealthStatusResponse = {
        status: isExhausted ? "exhausted" : activeCount < configuredCount ? "degraded" : "ok",
        service: "HAWKLYX Edge Service",
        version: "1.0.0",
        configured_accounts: configuredCount,
        active_accounts: activeCount,
        total_requests_today: accountPool.getTotalUsedToday(),
        daily_budget: parseInt(env.GLOBAL_DAILY_BUDGET || "200", 10),
        has_cached_observation: cacheManager.getObservation() !== null,
        last_successful_fetch: cacheManager.getObservation()?.fetched_at || null,
        cache_age_seconds: cacheManager.getCacheAgeSeconds(nowMs),
        accounts: accountPool.getSanitizedHealth(nowMs),
      };
      return jsonResponse(health);
    }

    // Routes: /api/v1/price/latest and /
    if (pathname === "/api/v1/price/latest" || pathname === "/" || pathname === "/price") {
      const forceParam = url.searchParams.get("force");
      const isForced = forceParam === "true" || forceParam === "1";

      const cached = cacheManager.getObservation();
      const isFresh = cacheManager.isFresh(nowMs);
      const isInCooldown = cacheManager.isInCooldown(nowMs);

      // Scenario 1: Cached data is fresh and client didn't force
      if (isFresh && !isForced && cached) {
        const body: HawklyxPriceResponse = {
          status: cached.is_stale ? "stale" : "fresh",
          data: cached,
          message: "Brent crude price observation is up to date.",
          next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
        };
        return jsonResponse(body, 200);
      }

      // Scenario 2: Client requested force-refresh, but cooldown is active
      if (isForced && isInCooldown && cached) {
        const body: HawklyxPriceResponse = {
          status: isFresh ? "fresh" : "stale",
          data: cached,
          message: "Manual refresh is cooling down. Showing latest available price.",
          next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(nowMs),
          server_time: new Date(nowMs).toISOString(),
        };
        return jsonResponse(body, 200);
      }

      // Scenario 3: Need to fetch new observation from upstream provider
      // Use coalescing to collapse simultaneous concurrent requests
      return await cacheManager.coalesce(async () => {
        // Re-check cache inside coalesced block
        const coalescedNow = Date.now();
        if (cacheManager.isFresh(coalescedNow) && !isForced) {
          const freshCached = cacheManager.getObservation()!;
          return jsonResponse({
            status: freshCached.is_stale ? "stale" : "fresh",
            data: freshCached,
            message: "Brent crude price observation is up to date.",
            next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(coalescedNow),
            server_time: new Date(coalescedNow).toISOString(),
          });
        }

        // Try accounts in pool until success or all exhausted
        let lastError: Error | null = null;
        let attempts = 0;
        const maxAttempts = Math.max(1, accountPool.getActiveAccountsCount(coalescedNow));

        while (attempts < maxAttempts) {
          const account = accountPool.selectEligibleAccount(Date.now());
          if (!account) {
            break;
          }

          attempts++;
          accountPool.recordRequest(account.slot, Date.now());
          cacheManager.recordUpstreamAttempt(Date.now());

          try {
            const observation = await provider.fetchLatestBrentPrice(account.key);
            cacheManager.storeObservation(observation, Date.now());

            const body: HawklyxPriceResponse = {
              status: observation.is_stale ? "stale" : "fresh",
              data: observation,
              message: "Brent crude price updated successfully from provider.",
              next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(Date.now()),
              server_time: new Date().toISOString(),
            };
            return jsonResponse(body, 200);
          } catch (err: unknown) {
            lastError = err instanceof Error ? err : new Error(String(err));

            if (err instanceof ProviderAuthError) {
              accountPool.markInvalidAuth(account.slot);
            } else if (err instanceof ProviderQuotaExhaustedError) {
              accountPool.markExhausted(account.slot, err.resetTimeMs);
            } else if (err instanceof ProviderRateLimitedError) {
              accountPool.markRateLimited(account.slot, err.retryAfterSeconds);
            } else if (err instanceof ProviderServerError || err instanceof ProviderValidationError) {
              // Server or validation error: do not rotate further for this tick
              break;
            }
          }
        }

        // Upstream fetch failed. Handle fallback safely.
        const currentCached = cacheManager.getObservation();
        const fallbackNow = Date.now();

        if (currentCached) {
          let status: HawklyxPriceResponse["status"] = "stale";
          let message = "Price updates are temporarily unavailable. Showing last known valid price.";

          if (accountPool.isGlobalBudgetExhausted() || accountPool.getActiveAccountsCount(fallbackNow) === 0) {
            status = "quota_exhausted";
            message = "Price updates are temporarily unavailable due to daily quota. Showing last valid price.";
          } else if (lastError instanceof ProviderServerError) {
            status = "provider_unavailable";
            message = "Upstream market data provider is temporarily unavailable. Showing last valid price.";
          } else if (lastError instanceof ProviderValidationError) {
            status = "invalid_provider_response";
            message = "Received unexpected data from provider. Showing last valid price.";
          }

          const fallbackBody: HawklyxPriceResponse = {
            status,
            data: currentCached,
            message,
            next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(fallbackNow),
            server_time: new Date(fallbackNow).toISOString(),
          };
          return jsonResponse(fallbackBody, 200);
        }

        // No cached data exists yet
        const noDataBody: HawklyxPriceResponse = {
          status: "no_data",
          data: null,
          message: "Price unavailable — we could not retrieve a valid Brent price yet. Please try again later.",
          next_refresh_allowed_at: cacheManager.getNextRefreshAllowedIso(fallbackNow),
          server_time: new Date(fallbackNow).toISOString(),
        };
        return jsonResponse(noDataBody, 503);
      });
    }

    return jsonResponse({ error: "Not Found", message: `Route ${pathname} not found on HAWKLYX Edge Service` }, 404);
  },
};
