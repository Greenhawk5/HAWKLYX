import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HawklyxCoordinator } from "../src/coordinator";
import worker from "../src/index";
import { Env, HawklyxHistoryResponse, HawklyxPriceResponse } from "../src/types";
import { createMockDurableObjectState, MockSqlStorageEngine } from "./test-helpers";

describe("Autonomous Server-Side Price Collection (Cron Trigger & Coordinator Integration)", () => {
  let baseEnv: Env;
  let coordinator: HawklyxCoordinator;
  let envWithDO: Env;
  let storageEngine: MockSqlStorageEngine;

  function createDOBinding(inst: any): DurableObjectNamespace {
    return {
      getByName: vi.fn().mockReturnValue(inst),
      idFromName: vi.fn(),
      idFromString: vi.fn(),
      get: vi.fn().mockReturnValue(inst),
      jurisdiction: vi.fn(),
    } as unknown as DurableObjectNamespace;
  }

  function createMockExecutionContext(): ExecutionContext {
    return {
      waitUntil: vi.fn(),
      passThroughOnException: vi.fn(),
    } as unknown as ExecutionContext;
  }

  function createMockScheduledController(scheduledTime: number = Date.now(), cron: string = "*/15 * * * *"): ScheduledController {
    return {
      scheduledTime,
      cron,
      noRetry: vi.fn(),
    } as unknown as ScheduledController;
  }

  function sampleValidApiResponse(price: number, asOf: string, stale: boolean = false, synthetic: boolean = false) {
    return {
      status: "success",
      data: {
        price,
        formatted: `$${price.toFixed(2)}`,
        currency: "USD",
        code: "BRENT_CRUDE_USD",
        as_of: asOf,
        created_at: asOf,
        collected_at: asOf,
        stale,
        synthetic,
        source: "OilPriceAPI",
      },
    };
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.useFakeTimers({ toFake: ["Date"] });
    // Default test fixture: Wednesday 14:00 UTC (active trading window)
    vi.setSystemTime(new Date("2026-10-14T14:00:00Z"));

    baseEnv = {
      OILPRICEAPI_KEY_1: "test_key_1",
      OILPRICEAPI_KEY_2: "test_key_2",
      DEFAULT_UPSTREAM_INTERVAL_MINUTES: "15",
      MANUAL_REFRESH_COOLDOWN_SECONDS: "180",
      MAX_DAILY_QUOTA_PER_ACCOUNT: "50",
      GLOBAL_DAILY_BUDGET: "250",
      AUTOMATIC_RESERVE_BUDGET: "110",
      MANUAL_BUDGET_CAP: "140",
      PRICE_SANITY_MIN: "10",
      PRICE_SANITY_MAX: "250",
    };

    storageEngine = new MockSqlStorageEngine();
    const state = createMockDurableObjectState(storageEngine);
    coordinator = new HawklyxCoordinator(state, baseEnv);

    envWithDO = {
      ...baseEnv,
      COORDINATOR: createDOBinding(coordinator),
    };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("Scheduled Handler Invocation & Binding Resilience", () => {
    it("safely handles missing COORDINATOR binding without throwing unhandled exceptions", async () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      const envWithoutDO: Env = { ...baseEnv };
      delete envWithoutDO.COORDINATOR;

      const controller = createMockScheduledController();
      const ctx = createMockExecutionContext();

      await expect(worker.scheduled(controller, envWithoutDO, ctx)).resolves.not.toThrow();
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        expect.stringContaining("COORDINATOR Durable Object binding is unavailable")
      );
    });

    it("safely catches unexpected coordinator errors without unhandled rejection", async () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      const brokenCoordinator = {
        handlePriceRequest: vi.fn().mockRejectedValue(new Error("DO internal failure")),
      };
      const brokenEnv: Env = {
        ...baseEnv,
        COORDINATOR: createDOBinding(brokenCoordinator),
      };

      const controller = createMockScheduledController();
      const ctx = createMockExecutionContext();

      await expect(worker.scheduled(controller, brokenEnv, ctx)).resolves.not.toThrow();
      expect(consoleErrorSpy).toHaveBeenCalledWith(
        expect.stringContaining("Automatic price collection failed"),
        expect.any(Error)
      );
    });

    it("invokes coordinator handlePriceRequest with force=false and requestClass='automatic'", async () => {
      const spy = vi.spyOn(coordinator, "handlePriceRequest");
      const controller = createMockScheduledController();
      const ctx = createMockExecutionContext();

      // Mock upstream response
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(sampleValidApiResponse(82.5, "2026-10-14T14:00:00Z")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      try {
        await worker.scheduled(controller, envWithDO, ctx);
        expect(spy).toHaveBeenCalledWith({ force: false, requestClass: "automatic" });
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });

  describe("Market Closure and Cold-Start Cache Safety", () => {
    it("strictly skips upstream call during market closure even with empty cold-start cache", async () => {
      // Set time to Saturday 12:00 UTC (exchange closed)
      vi.setSystemTime(new Date("2026-10-17T12:00:00Z"));

      let upstreamCalls = 0;
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        upstreamCalls++;
        throw new Error("Upstream MUST NOT be called when market is closed!");
      });

      try {
        // storageEngine observations is completely empty (cold-start)
        expect(storageEngine.observations.size).toBe(0);

        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        await worker.scheduled(controller, envWithDO, ctx);

        // Verify ZERO upstream calls were made
        expect(upstreamCalls).toBe(0);

        // Verify health reflects 0 requests consumed
        const health = await coordinator.getHealth();
        expect(health.total_requests_today).toBe(0);
        expect(health.automatic_requests_today).toBe(0);

        // Verify handlePriceRequest returned market_closed with data: null
        const res = await coordinator.handlePriceRequest({ requestClass: "automatic" });
        expect(res.status).toBe(200);
        expect(res.body.status).toBe("market_closed");
        expect(res.body.data).toBeNull();
        expect(res.body.market?.is_open).toBe(false);
        expect(res.body.message).toContain("Price collection will begin when trading resumes");
      } finally {
        globalThis.fetch = originalFetch;
      }
    });

    it("serves cached price during market closure with warm cache without polling upstream", async () => {
      vi.setSystemTime(new Date("2026-10-17T12:00:00Z"));

      let upstreamCalls = 0;
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        upstreamCalls++;
        throw new Error("Upstream MUST NOT be called when market is closed!");
      });

      try {
        // Seed known-good observation from Friday close
        storageEngine.observations.set("last_known_good", {
          key: "last_known_good",
          data: JSON.stringify(sampleValidApiResponse(85.5, "2026-10-16T21:55:00Z").data),
          updated_at: new Date("2026-10-16T21:55:00Z").getTime(),
        });

        const state = createMockDurableObjectState(storageEngine);
        const warmCoordinator = new HawklyxCoordinator(state, baseEnv);
        const warmEnv: Env = {
          ...baseEnv,
          COORDINATOR: createDOBinding(warmCoordinator),
        };

        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        await worker.scheduled(controller, warmEnv, ctx);
        expect(upstreamCalls).toBe(0);

        const res = await warmCoordinator.handlePriceRequest({ requestClass: "automatic" });
        expect(res.status).toBe(200);
        expect(res.body.status).toBe("market_closed");
        expect(res.body.data?.price).toBe(85.5);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });

  describe("Cache Freshness Suppression", () => {
    it("skips upstream call when cache is fresh within 15-minute window", async () => {
      let upstreamCalls = 0;
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        upstreamCalls++;
        return new Response(JSON.stringify(sampleValidApiResponse(82.0, "2026-10-14T14:00:00Z")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      });

      try {
        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        // Run 1: Cold cache -> fetches upstream
        await worker.scheduled(controller, envWithDO, ctx);
        expect(upstreamCalls).toBe(1);

        // Advance clock by 5 minutes (still within 15-minute freshness interval)
        vi.advanceTimersByTime(5 * 60 * 1000);

        // Run 2: Cache is fresh -> skips upstream call completely
        await worker.scheduled(controller, envWithDO, ctx);
        expect(upstreamCalls).toBe(1); // Call count unchanged!

        // Advance clock by another 11 minutes (total 16 minutes > 15-minute freshness)
        vi.advanceTimersByTime(11 * 60 * 1000);

        // Run 3: Cache is expired -> fetches upstream again
        await worker.scheduled(controller, envWithDO, ctx);
        expect(upstreamCalls).toBe(2);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });

  describe("Concurrent Cron and Android Client Deduplication", () => {
    it("coalesces overlapping Cron and Android requests into exactly 1 upstream fetch", async () => {
      let upstreamCalls = 0;
      const originalFetch = globalThis.fetch;

      // Simulate network latency of 100ms
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        upstreamCalls++;
        await new Promise((resolve) => setTimeout(resolve, 100));
        return new Response(JSON.stringify(sampleValidApiResponse(83.0, "2026-10-14T14:00:00Z")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      });

      try {
        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();
        const androidReq = new Request("http://localhost/api/v1/price/latest");

        // Fire both Cron and Android requests simultaneously
        const cronPromise = worker.scheduled(controller, envWithDO, ctx);
        const androidPromise = worker.fetch(androidReq, envWithDO);

        // Advance fake timer to resolve the 100ms delay
        vi.advanceTimersByTime(100);

        const [, androidRes] = await Promise.all([cronPromise, androidPromise]);

        // Verify only 1 upstream call occurred
        expect(upstreamCalls).toBe(1);

        // Verify Android client received fresh data
        expect(androidRes.status).toBe(200);
        const body = (await androidRes.json()) as HawklyxPriceResponse;
        expect(body.status).toBe("fresh");
        expect(body.data?.price).toBe(83.0);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });

  describe("Budget Hard-Cap Enforcement", () => {
    it("aborts upstream call and returns quota_exhausted when automatic daily budget is exhausted", async () => {
      let upstreamCalls = 0;
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        upstreamCalls++;
        return new Response(JSON.stringify(sampleValidApiResponse(84.0, "2026-10-14T14:00:00Z")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      });

      try {
        // Seed storage with budget already exhausted: 250 requests used
        storageEngine.systemState.set("automatic_used_today", "110");
        storageEngine.systemState.set("manual_used_today", "140");
        storageEngine.systemState.set("last_reset_day", "2026-10-14");

        const state = createMockDurableObjectState(storageEngine);
        const exhaustedCoordinator = new HawklyxCoordinator(state, baseEnv);
        const exhaustedEnv: Env = {
          ...baseEnv,
          COORDINATOR: createDOBinding(exhaustedCoordinator),
        };

        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        await worker.scheduled(controller, exhaustedEnv, ctx);

        // Verify 0 upstream calls were attempted
        expect(upstreamCalls).toBe(0);

        const health = await exhaustedCoordinator.getHealth();
        expect(health.status).toBe("exhausted");
        expect(health.total_requests_today).toBe(250);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });

  describe("Historical Hourly Persistence & Deduplication", () => {
    it("persists at most 1 canonical observation per UTC hour across consecutive cron runs", async () => {
      let currentPrice = 82.0;
      let currentAsOf = "2026-10-14T10:00:00Z";

      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockImplementation(async () => {
        return new Response(JSON.stringify(sampleValidApiResponse(currentPrice, currentAsOf)), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      });

      try {
        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        // --- Hour 10: Run at 10:00 UTC ---
        vi.setSystemTime(new Date("2026-10-14T10:00:00Z"));
        currentPrice = 82.0;
        currentAsOf = "2026-10-14T10:00:00Z";
        await worker.scheduled(controller, envWithDO, ctx);

        // --- Hour 10: Run at 10:16 UTC (same hour, cache expired, newer price/as_of) ---
        vi.setSystemTime(new Date("2026-10-14T10:16:00Z"));
        currentPrice = 82.4;
        currentAsOf = "2026-10-14T10:15:00Z";
        await worker.scheduled(controller, envWithDO, ctx);

        // Verify only 1 observation exists for 10:00 UTC bucket, updated to 82.40
        expect(storageEngine.priceObservations.size).toBe(1);
        const hour10Obs = storageEngine.priceObservations.get("BRENT_CRUDE_USD:2026-10-14T10:00:00Z");
        expect(hour10Obs?.price).toBe(82.4);

        // --- Hour 11: Run at 11:00 UTC ---
        vi.setSystemTime(new Date("2026-10-14T11:00:00Z"));
        currentPrice = 83.1;
        currentAsOf = "2026-10-14T11:00:00Z";
        await worker.scheduled(controller, envWithDO, ctx);

        // --- Hour 12: Run at 12:00 UTC ---
        vi.setSystemTime(new Date("2026-10-14T12:00:00Z"));
        currentPrice = 82.8;
        currentAsOf = "2026-10-14T12:00:00Z";
        await worker.scheduled(controller, envWithDO, ctx);

        // Verify exactly 3 canonical hourly observations exist
        expect(storageEngine.priceObservations.size).toBe(3);
        expect(storageEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-14T10:00:00Z")).toBe(true);
        expect(storageEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-14T11:00:00Z")).toBe(true);
        expect(storageEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-14T12:00:00Z")).toBe(true);

        // Verify history endpoint serves all 3 points with valid summary OHLC statistics
        const historyRes = await coordinator.handleHistoryRequest({ range: "1d", resolution: "hourly" });
        expect(historyRes.status).toBe(200);
        const historyBody = historyRes.body as HawklyxHistoryResponse;
        expect(historyBody.status).toBe("success");
        expect(historyBody.count).toBe(3);
        expect(historyBody.points.length).toBe(3);
        expect(historyBody.summary?.first_price).toBe(82.4);
        expect(historyBody.summary?.latest_price).toBe(82.8);
        expect(historyBody.summary?.high).toBe(83.1);
        expect(historyBody.summary?.low).toBe(82.4);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });

    it("strictly rejects synthetic and stale observations from entering SQLite history", async () => {
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(sampleValidApiResponse(85.0, "2026-10-14T14:00:00Z", false, true)), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      try {
        const controller = createMockScheduledController();
        const ctx = createMockExecutionContext();

        await worker.scheduled(controller, envWithDO, ctx);

        // Verify synthetic observation was NOT recorded into price_observations
        expect(storageEngine.priceObservations.size).toBe(0);

        // Verify history endpoint returns empty
        const historyRes = await coordinator.handleHistoryRequest({ range: "1d", resolution: "hourly" });
        expect(historyRes.body.status).toBe("empty");
        expect(historyRes.body.count).toBe(0);
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });
});
