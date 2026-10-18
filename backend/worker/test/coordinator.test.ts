import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HawklyxCoordinator } from "../src/coordinator";
import { Env } from "../src/types";
import {
  createMockDurableObjectState,
  MockSqlStorageEngine,
  sampleValidPriceResponse,
} from "./test-helpers";

describe("HawklyxCoordinator Durable Object", () => {
  let mockEnv: Env;
  let storageEngine: MockSqlStorageEngine;

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.useFakeTimers({ toFake: ["Date"] });
    // Default test fixture: Wednesday 14:00 UTC (active trading window)
    vi.setSystemTime(new Date("2026-10-14T14:00:00Z"));

    storageEngine = new MockSqlStorageEngine();
    mockEnv = {
      OILPRICEAPI_KEY_1: "key_alpha",
      OILPRICEAPI_KEY_2: "key_beta",
      DEFAULT_UPSTREAM_INTERVAL_MINUTES: "15",
      MANUAL_REFRESH_COOLDOWN_SECONDS: "180",
      MAX_DAILY_QUOTA_PER_ACCOUNT: "50",
      GLOBAL_DAILY_BUDGET: "250",
      AUTOMATIC_RESERVE_BUDGET: "110",
      MANUAL_BUDGET_CAP: "140",
      PRICE_SANITY_MIN: "10",
      PRICE_SANITY_MAX: "250",
    };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("coalesces concurrent requests so only a single upstream fetch occurs", async () => {
    let upstreamCallCount = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockImplementation(async () => {
      upstreamCallCount++;
      // Simulate small latency
      await new Promise((r) => setTimeout(r, 40));
      return new Response(JSON.stringify(sampleValidPriceResponse(84.5)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    try {
      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      // Fire 5 simultaneous requests concurrently
      const results = await Promise.all([
        coordinator.handlePriceRequest(),
        coordinator.handlePriceRequest(),
        coordinator.handlePriceRequest(),
        coordinator.handlePriceRequest(),
        coordinator.handlePriceRequest(),
      ]);

      // Exactly ONE upstream call was made
      expect(upstreamCallCount).toBe(1);

      // All 5 callers received identical valid results
      for (const res of results) {
        expect(res.status).toBe(200);
        expect(res.body.status).toBe("fresh");
        expect(res.body.data?.price).toBe(84.5);
      }

      // Quota incremented by exactly 1
      const health = await coordinator.getHealth();
      expect(health.total_requests_today).toBe(1);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("survives isolate restart: recovers cache, quota, and cooldown from SQLite", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(sampleValidPriceResponse(82.25)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    try {
      // Instance 1: runs, makes upstream call, writes to persistent storage
      const state1 = createMockDurableObjectState(storageEngine);
      const coordinator1 = new HawklyxCoordinator(state1, mockEnv);
      const firstRes = await coordinator1.handlePriceRequest();
      expect(firstRes.body.status).toBe("fresh");
      expect(firstRes.body.data?.price).toBe(82.25);

      const health1 = await coordinator1.getHealth();
      expect(health1.total_requests_today).toBe(1);

      // Verify records in persistent SQLite mock
      expect(storageEngine.observations.has("last_known_good")).toBe(true);
      expect(storageEngine.systemState.has("last_upstream_attempt_ms")).toBe(true);
      expect(storageEngine.accountUsage.get(1)?.used_today).toBe(1);

      // Simulate Isolate crash / restart: coordinator1 is garbage collected
      // Coordinator 2 starts up using the EXACT SAME storage engine
      globalThis.fetch = vi.fn().mockRejectedValue(new Error("Should not be called if cache recovered!"));

      const state2 = createMockDurableObjectState(storageEngine);
      const coordinator2 = new HawklyxCoordinator(state2, mockEnv);

      // Coordinator 2 should immediately serve cached observation without calling fetch
      const recoveredRes = await coordinator2.handlePriceRequest();
      expect(recoveredRes.status).toBe(200);
      expect(recoveredRes.body.status).toBe("fresh");
      expect(recoveredRes.body.data?.price).toBe(82.25);

      const health2 = await coordinator2.getHealth();
      expect(health2.total_requests_today).toBe(1);
      expect(health2.has_cached_observation).toBe(true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("preserves last-known-good price and does not overwrite it with stale or synthetic responses", async () => {
    const originalFetch = globalThis.fetch;

    try {
      // 1. Initial valid, non-stale, non-synthetic observation
      globalThis.fetch = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(sampleValidPriceResponse(86.5, false, false)), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      const res1 = await coordinator.handlePriceRequest();
      expect(res1.body.status).toBe("fresh");
      expect(res1.body.data?.price).toBe(86.5);

      // 2. Upstream returns a response marked stale: true
      globalThis.fetch = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(sampleValidPriceResponse(70.0, true, false)), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      // Force a refresh (bypassing cooldown by resetting attempt timestamp for testing)
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const stateAfterStale = createMockDurableObjectState(storageEngine);
      const coordinatorStale = new HawklyxCoordinator(stateAfterStale, mockEnv);

      const res2 = await coordinatorStale.handlePriceRequest({ force: true });
      expect(res2.body.status).toBe("stale");
      // The last known-good observation (86.5) was preserved!
      expect(res2.body.data?.price).toBe(86.5);
      expect(res2.body.message).toContain("stale/synthetic");

      // 3. Upstream completely fails now: verify fallback serves the preserved 86.5
      globalThis.fetch = vi.fn().mockRejectedValue(new Error("Network completely down"));
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const stateAfterFail = createMockDurableObjectState(storageEngine);
      const coordinatorFail = new HawklyxCoordinator(stateAfterFail, mockEnv);

      const res3 = await coordinatorFail.handlePriceRequest({ force: true });
      expect(res3.status).toBe(200);
      expect(res3.body.status).toBe("provider_unavailable");
      expect(res3.body.data?.price).toBe(86.5);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("strictly enforces 3-minute cooldown on forced and ordinary requests", async () => {
    let callCount = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockImplementation(async () => {
      callCount++;
      return new Response(JSON.stringify(sampleValidPriceResponse(80.0)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    try {
      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      // Call 1: cold cache -> calls upstream
      await coordinator.handlePriceRequest();
      expect(callCount).toBe(1);

      // Call 2: Forced refresh immediately within cooldown window
      const forcedRes = await coordinator.handlePriceRequest({ force: true });
      expect(callCount).toBe(1); // Blocked by cooldown!
      expect(forcedRes.body.message).toContain("Manual refresh is cooling down");
      expect(forcedRes.body.data?.price).toBe(80.0);

      // Call 3: Non-forced request while upstream is in cooldown
      const nonForcedRes = await coordinator.handlePriceRequest({ force: false });
      expect(callCount).toBe(1); // Served from cache!
      expect(nonForcedRes.body.status).toBe("fresh");
      expect(nonForcedRes.body.data?.price).toBe(80.0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("rotates accounts properly on 401/403 (quarantine) and 402 (quota limit)", async () => {
    const originalFetch = globalThis.fetch;
    let attempt = 0;

    globalThis.fetch = vi.fn().mockImplementation(async (url, init: any) => {
      attempt++;
      const auth = init.headers.Authorization;
      if (auth.includes("key_alpha")) {
        // First account returns 401 Unauthorized
        return new Response(JSON.stringify({ status: "error", error: "Unauthorized" }), { status: 401 });
      }
      if (auth.includes("key_beta")) {
        // Second account returns valid data
        return new Response(JSON.stringify(sampleValidPriceResponse(83.1)), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({ status: "error" }), { status: 500 });
    });

    try {
      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      const res = await coordinator.handlePriceRequest();
      expect(res.status).toBe(200);
      expect(res.body.status).toBe("fresh");
      expect(res.body.data?.price).toBe(83.1);

      // Account 1 (key_alpha) is quarantined as INVALID_AUTH
      const health = await coordinator.getHealth();
      expect(health.configured_accounts).toBe(2);
      expect(health.active_accounts).toBe(1);
      expect(health.accounts[0].active).toBe(false);
      expect(health.accounts[0].quarantine_reason).toBe("INVALID_AUTH");
      expect(health.accounts[1].active).toBe(true);

      // Next request will select Account 2 directly, not attempting Account 1
      attempt = 0;
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const state2 = createMockDurableObjectState(storageEngine);
      const coordinator2 = new HawklyxCoordinator(state2, mockEnv);
      await coordinator2.handlePriceRequest({ force: true });

      // Only Account 2 was called (attempt === 1)
      expect(attempt).toBe(1);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("enforces global daily budget and returns quota_exhausted without leaking secrets", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(sampleValidPriceResponse(81.0)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    try {
      const tightEnv: Env = {
        ...mockEnv,
        GLOBAL_DAILY_BUDGET: "2",
        AUTOMATIC_RESERVE_BUDGET: "1",
        MANUAL_BUDGET_CAP: "1",
      };

      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, tightEnv);

      // 1st request -> success
      await coordinator.handlePriceRequest();

      // Reset cooldown timestamp for testing
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const state2 = createMockDurableObjectState(storageEngine);
      const coordinator2 = new HawklyxCoordinator(state2, tightEnv);
      await coordinator2.handlePriceRequest({ force: true });

      // Now 2 requests have been consumed, reaching the daily budget
      const health = await coordinator2.getHealth();
      expect(health.total_requests_today).toBe(2);
      expect(health.status).toBe("exhausted");

      // 3rd request should fail fast with quota_exhausted and preserve last price
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const state3 = createMockDurableObjectState(storageEngine);
      const coordinator3 = new HawklyxCoordinator(state3, tightEnv);

      const res3 = await coordinator3.handlePriceRequest({ force: true });
      expect(res3.status).toBe(200);
      expect(res3.body.status).toBe("quota_exhausted");
      expect(res3.body.data?.price).toBe(81.0);
      expect(res3.body.message.toLowerCase()).toMatch(/quota|limit/);

      // Verify no secrets leaked in response or health
      const serialized = JSON.stringify(res3) + JSON.stringify(health);
      expect(serialized).not.toContain("key_alpha");
      expect(serialized).not.toContain("key_beta");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("handles UTC day reset correctly across SQLite storage", async () => {
    const state = createMockDurableObjectState(storageEngine);
    // Seed storage with yesterday's usage
    storageEngine.systemState.set("last_reset_day", "2020-01-01");
    storageEngine.accountUsage.set(1, {
      slot: 1,
      used_today: 45,
      last_used_at: Date.now() - 100000,
      quarantined_until: Date.now() + 500000,
      quarantine_reason: "EXHAUSTED",
    });

    const coordinator = new HawklyxCoordinator(state, mockEnv);
    const health = await coordinator.getHealth();

    // After loading with a new UTC date, used_today is reset and EXHAUSTED quarantine cleared
    expect(health.total_requests_today).toBe(0);
    expect(health.accounts[0].used_today).toBe(0);
    expect(health.accounts[0].active).toBe(true);
    expect(storageEngine.systemState.get("last_reset_day")).toBe(new Date().toISOString().slice(0, 10));
  });

  it("protects 110-request automatic reserve: blocks manual requests when 140 cap reached while automatic requests succeed", async () => {
    let upstreamCallCount = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockImplementation(async () => {
      upstreamCallCount++;
      return new Response(JSON.stringify(sampleValidPriceResponse(83.5)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    try {
      // Seed storage with manual cap exhausted: 140 manual requests used, 10 automatic used
      storageEngine.systemState.set("manual_used_today", "140");
      storageEngine.systemState.set("automatic_used_today", "10");
      storageEngine.systemState.set("last_reset_day", "2026-10-14");
      // Add existing observation
      storageEngine.observations.set("last_known_good", {
        key: "last_known_good",
        data: JSON.stringify(sampleValidPriceResponse(82.0).data),
        updated_at: Date.now() - 600000,
      });

      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      // Manual request must be rejected to protect automatic reserve
      const manualRes = await coordinator.handlePriceRequest({ force: true, requestClass: "manual" });
      expect(manualRes.status).toBe(200);
      expect(manualRes.body.status).toBe("quota_exhausted");
      expect(manualRes.body.message).toContain("Manual refresh limit reached for today");
      expect(manualRes.body.data?.price).toBe(82.0);
      expect(upstreamCallCount).toBe(0); // Upstream not called for blocked manual request

      // Automatic request must STILL succeed because automatic reserve (110) is untouched!
      storageEngine.systemState.set("last_upstream_attempt_ms", "0");
      const autoRes = await coordinator.handlePriceRequest({ requestClass: "automatic" });
      expect(autoRes.status).toBe(200);
      expect(autoRes.body.status).toBe("fresh");
      expect(autoRes.body.data?.price).toBe(83.5);
      expect(upstreamCallCount).toBe(1);

      // Verify health stats reflect separate tracking
      const health = await coordinator.getHealth();
      expect(health.manual_requests_today).toBe(140);
      expect(health.automatic_requests_today).toBe(11);
      expect(health.total_requests_today).toBe(151);
      expect(health.daily_budget).toBe(250);
      expect(health.automatic_reserve).toBe(110);
      expect(health.manual_budget_cap).toBe(140);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("skips upstream call during market closure on automatic requests, strictly preserving quota", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockImplementation(async () => {
      throw new Error("Upstream fetch must NOT be called when market is closed!");
    });

    try {
      // Set time to Saturday noon (market completely closed)
      vi.setSystemTime(new Date("2026-10-17T12:00:00Z"));

      storageEngine.systemState.set("last_reset_day", "2026-10-17");
      storageEngine.observations.set("last_known_good", {
        key: "last_known_good",
        data: JSON.stringify(sampleValidPriceResponse(86.25).data),
        updated_at: new Date("2026-10-16T21:55:00Z").getTime(),
      });

      const state = createMockDurableObjectState(storageEngine);
      const coordinator = new HawklyxCoordinator(state, mockEnv);

      // Automatic scheduled request arrives
      const res = await coordinator.handlePriceRequest({ requestClass: "automatic" });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe("market_closed");
      expect(res.body.data?.price).toBe(86.25);
      expect(res.body.market?.is_open).toBe(false);
      expect(res.body.market?.session).toBe("weekend_closed");
      expect(res.body.message).toContain("Showing latest available price");

      // Verify 0 upstream requests were consumed
      const health = await coordinator.getHealth();
      expect(health.total_requests_today).toBe(0);
      expect(health.automatic_requests_today).toBe(0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
