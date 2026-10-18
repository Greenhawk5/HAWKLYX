import { describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { Env, HawklyxPriceResponse, HealthStatusResponse } from "../src/types";

describe("Worker Endpoints Integration", () => {
  const mockEnv: Env = {
    OILPRICEAPI_KEY_1: "test_key_1",
    OILPRICEAPI_KEY_2: "test_key_2",
    DEFAULT_UPSTREAM_INTERVAL_MINUTES: "15",
    MANUAL_REFRESH_COOLDOWN_SECONDS: "180",
    MAX_DAILY_QUOTA_PER_ACCOUNT: "50",
    GLOBAL_DAILY_BUDGET: "200",
  };

  it("handles GET /health and returns sanitized metrics", async () => {
    const req = new Request("http://localhost/health");
    const res = await worker.fetch(req, mockEnv);

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
    // Mock global fetch to fail
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockRejectedValue(new Error("Network connection failure"));

    try {
      const req = new Request("http://localhost/api/v1/price/latest");
      const res = await worker.fetch(req, mockEnv);

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
      const res = await worker.fetch(req, mockEnv);

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
    // Cached observation now exists in isolate memory
    const req = new Request("http://localhost/api/v1/price/latest");
    const res = await worker.fetch(req, mockEnv);

    expect(res.status).toBe(200);
    const body = (await res.json()) as HawklyxPriceResponse;
    expect(body.status).toBe("fresh");
    expect(body.data?.price).toBe(81.2);
  });
});
