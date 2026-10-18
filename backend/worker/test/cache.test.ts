import { describe, expect, it } from "vitest";
import { CacheManager } from "../src/cache";
import { ValidatedObservation } from "../src/types";

describe("CacheManager", () => {
  const dummyObs: ValidatedObservation = {
    code: "BRENT_CRUDE_USD",
    name: "Brent Crude Oil",
    price: 80.5,
    currency: "USD",
    unit: "USD / barrel",
    formatted: "$80.50",
    as_of: "2026-10-18T10:00:00Z",
    collected_at: null,
    created_at: null,
    fetched_at: "2026-10-18T10:01:00Z",
    is_stale: false,
    is_synthetic: false,
    source: "OilPriceAPI",
  };

  it("evaluates freshness window correctly (15 min)", () => {
    const cache = new CacheManager({ freshnessIntervalMinutes: 15 });
    const now = 1000000;

    expect(cache.isFresh(now)).toBe(false);

    cache.storeObservation(dummyObs, now);
    expect(cache.isFresh(now)).toBe(true);

    // 14 minutes later: still fresh
    expect(cache.isFresh(now + 14 * 60 * 1000)).toBe(true);

    // 16 minutes later: stale
    expect(cache.isFresh(now + 16 * 60 * 1000)).toBe(false);
  });

  it("treats observation marked stale/synthetic upstream as not fresh", () => {
    const cache = new CacheManager({ freshnessIntervalMinutes: 15 });
    const now = 1000000;

    cache.storeObservation({ ...dummyObs, is_stale: true }, now);
    expect(cache.isFresh(now)).toBe(false);
  });

  it("enforces cooldown window (3 minutes)", () => {
    const cache = new CacheManager({ cooldownSeconds: 180 });
    const now = 1000000;

    cache.recordUpstreamAttempt(now);
    expect(cache.isInCooldown(now + 60 * 1000)).toBe(true);
    expect(cache.isInCooldown(now + 181 * 1000)).toBe(false);
  });

  it("coalesces simultaneous concurrent requests into a single promise", async () => {
    const cache = new CacheManager();
    let counter = 0;

    const slowFetcher = async () => {
      counter++;
      await new Promise((r) => setTimeout(r, 50));
      return dummyObs;
    };

    const [res1, res2, res3] = await Promise.all([
      cache.coalesce(slowFetcher),
      cache.coalesce(slowFetcher),
      cache.coalesce(slowFetcher),
    ]);

    expect(counter).toBe(1);
    expect(res1.price).toBe(80.5);
    expect(res2.price).toBe(80.5);
    expect(res3.price).toBe(80.5);
  });
});
