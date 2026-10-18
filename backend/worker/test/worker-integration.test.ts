import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HawklyxCoordinator } from "../src/coordinator";
import worker from "../src/index";
import { Env, HawklyxPriceResponse, HealthStatusResponse } from "../src/types";
import { createMockDurableObjectState, MockSqlStorageEngine } from "./test-helpers";

describe("Worker Endpoints Integration", () => {
  let baseEnv: Env;
  let coordinator: HawklyxCoordinator;
  let envWithDO: Env;

  function createDOBinding(inst: any): DurableObjectNamespace {
    return {
      getByName: vi.fn().mockReturnValue(inst),
      idFromName: vi.fn(),
      idFromString: vi.fn(),
      get: vi.fn().mockReturnValue(inst),
      jurisdiction: vi.fn(),
    } as unknown as DurableObjectNamespace;
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
    };

    const storage = new MockSqlStorageEngine();
    const state = createMockDurableObjectState(storage);
    coordinator = new HawklyxCoordinator(state, baseEnv);

    envWithDO = {
      ...baseEnv,
      COORDINATOR: createDOBinding(coordinator),
    };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("fails closed with 503 when COORDINATOR binding is completely missing", async () => {
    // Missing env.COORDINATOR must NEVER fall back to in-memory state; it must fail closed
    const envWithoutDO: Env = { ...baseEnv };
    delete envWithoutDO.COORDINATOR;

    // Price endpoint check
    const req = new Request("http://localhost/api/v1/price/latest");
    const res = await worker.fetch(req, envWithoutDO);

    expect(res.status).toBe(503);
    const body = (await res.json()) as HawklyxPriceResponse;
    expect(body.status).toBe("no_data");
    expect(body.data).toBeNull();
    expect(body.message).toContain("Coordination service is unavailable");

    // Health endpoint check
    const healthReq = new Request("http://localhost/health");
    const healthRes = await worker.fetch(healthReq, envWithoutDO);
    expect(healthRes.status).toBe(503);
    const healthData = (await healthRes.json()) as HealthStatusResponse;
    expect(healthData.status).toBe("exhausted");
  });

  it("fails closed with safe 503 when coordinator throws an unexpected error", async () => {
    const brokenCoordinator = {
      handlePriceRequest: vi.fn().mockRejectedValue(new Error("DO internal failure")),
      getHealth: vi.fn().mockRejectedValue(new Error("DO internal health failure")),
    };

    const envBroken: Env = {
      ...baseEnv,
      COORDINATOR: createDOBinding(brokenCoordinator),
    };

    const req = new Request("http://localhost/api/v1/price/latest");
    const res = await worker.fetch(req, envBroken);

    expect(res.status).toBe(503);
    const body = (await res.json()) as HawklyxPriceResponse;
    expect(body.status).toBe("no_data");
    expect(body.data).toBeNull();
    expect(body.message).toContain("temporarily unavailable");
  });

  it("handles GET /health and returns sanitized metrics", async () => {
    const req = new Request("http://localhost/health");
    const res = await worker.fetch(req, envWithDO);

    expect(res.status).toBe(200);
    const data = (await res.json()) as HealthStatusResponse;
    expect(data.service).toBe("HAWKLYX Edge Service");
    expect(data.configured_accounts).toBe(2);
    expect(data.accounts[0].configured).toBe(true);
    expect(data.accounts[0].active).toBe(true);

    // Ensure zero secrets leak
    const serialized = JSON.stringify(data);
    expect(serialized).not.toContain("test_key_1");
  });

  it("returns no_data when upstream fails and no cached observation exists", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockRejectedValue(new Error("Network connection failure"));

    try {
      const req = new Request("http://localhost/api/v1/price/latest");
      const res = await worker.fetch(req, envWithDO);

      expect(res.status).toBe(503);
      const data = (await res.json()) as HawklyxPriceResponse;
      expect(data.status).toBe("no_data");
      expect(data.data).toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("returns fresh observation upon successful upstream call", async () => {
    const originalFetch = globalThis.fetch;
    const fakeApiResponse = {
      status: "success",
      data: {
        price: 81.2,
        formatted: "$81.20",
        currency: "USD",
        code: "BRENT_CRUDE_USD",
        as_of: "2026-10-18T10:00:00Z",
        stale: false,
        synthetic: false,
        source: "OilPriceAPI",
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(fakeApiResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    try {
      const req = new Request("http://localhost/api/v1/price/latest?force=true");
      const res = await worker.fetch(req, envWithDO);

      expect(res.status).toBe(200);
      const body = (await res.json()) as HawklyxPriceResponse;
      expect(body.status).toBe("fresh");
      expect(body.data?.price).toBe(81.2);
      expect(body.data?.code).toBe("BRENT_CRUDE_USD");
      expect(body.data?.as_of).toBe("2026-10-18T10:00:00Z");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("serves cached observation when requested again within cooldown or freshness window", async () => {
    // 1st request: seed the DO coordinator cache
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          status: "success",
          data: {
            price: 81.2,
            code: "BRENT_CRUDE_USD",
            stale: false,
            synthetic: false,
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );

    try {
      await worker.fetch(new Request("http://localhost/api/v1/price/latest?force=true"), envWithDO);

      // 2nd request: serve from cache without calling fetch
      globalThis.fetch = vi.fn().mockRejectedValue(new Error("Should not be called!"));
      const req = new Request("http://localhost/api/v1/price/latest");
      const res = await worker.fetch(req, envWithDO);

      expect(res.status).toBe(200);
      const body = (await res.json()) as HawklyxPriceResponse;
      expect(body.status).toBe("fresh");
      expect(body.data?.price).toBe(81.2);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("handles OPTIONS preflight with standard CORS headers", async () => {
    const req = new Request("http://localhost/api/v1/price/latest", {
      method: "OPTIONS",
    });
    const res = await worker.fetch(req, envWithDO);

    expect(res.status).toBe(204);
    expect(res.headers.get("Access-Control-Allow-Origin")).toBe("*");
    expect(res.headers.get("Access-Control-Allow-Methods")).toContain("GET");
  });

  it("rejects non-GET HTTP methods with 405 Method Not Allowed", async () => {
    const methods = ["POST", "PUT", "DELETE", "PATCH"];

    for (const method of methods) {
      const req = new Request("http://localhost/api/v1/price/latest", {
        method,
      });
      const res = await worker.fetch(req, envWithDO);

      expect(res.status).toBe(405);
      expect(res.headers.get("Allow")).toBe("GET, OPTIONS");
      const json = await res.json();
      expect(json).toHaveProperty("error", "Method Not Allowed");
    }
  });

  it("rejects excessively long URIs with 414 URI Too Long", async () => {
    const longParam = "a".repeat(2100);
    const req = new Request(`http://localhost/api/v1/price/latest?query=${longParam}`);
    const res = await worker.fetch(req, envWithDO);

    expect(res.status).toBe(414);
    const json = await res.json();
    expect(json).toHaveProperty("error", "URI Too Long");
  });

  it("returns 404 for unknown endpoints", async () => {
    const req = new Request("http://localhost/api/unknown-endpoint");
    const res = await worker.fetch(req, envWithDO);

    expect(res.status).toBe(404);
    const json = await res.json();
    expect(json).toHaveProperty("error", "Not Found");
  });

  it("routes requests to env.COORDINATOR Durable Object when bound", async () => {
    const mockCoordinatorStub = {
      handlePriceRequest: vi.fn().mockResolvedValue({
        body: {
          status: "fresh",
          data: {
            code: "BRENT_CRUDE_USD",
            price: 89.9,
          },
          message: "From mock DO stub",
          next_refresh_allowed_at: new Date().toISOString(),
          server_time: new Date().toISOString(),
        },
        status: 200,
      }),
      getHealth: vi.fn().mockResolvedValue({
        status: "ok",
        service: "HAWKLYX Edge Service",
      }),
    };

    const envMockDO: Env = {
      ...baseEnv,
      COORDINATOR: createDOBinding(mockCoordinatorStub),
    };

    const req = new Request("http://localhost/api/v1/price/latest");
    const res = await worker.fetch(req, envMockDO);

    expect(res.status).toBe(200);
    const body = (await res.json()) as HawklyxPriceResponse;
    expect(body.data?.price).toBe(89.9);
    expect(body.message).toBe("From mock DO stub");
    expect(mockCoordinatorStub.handlePriceRequest).toHaveBeenCalledTimes(1);
    expect(envMockDO.COORDINATOR?.getByName).toHaveBeenCalledWith("global");
  });
});
