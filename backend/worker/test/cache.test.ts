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

  it("preserves lastKnownGood when subsequent observation is stale or synthetic", () => {
    const cache = new CacheManager({ freshnessIntervalMinutes: 15 });
    const now = 1000000;

    // First store valid fresh observation
    cache.storeObservation(dummyObs, now);
    expect(cache.getLastKnownGood()?.price).toBe(80.5);
    expect(cache.isFresh(now)).toBe(true);

    // Now store a stale observation with different price
    const staleObs: ValidatedObservation = {
      ...dummyObs,
      price: 65.0,
      is_stale: true,
    };
    cache.storeObservation(staleObs, now + 1000);

    // lastKnownGood must still be 80.5!
    expect(cache.getLastKnownGood()?.price).toBe(80.5);
    expect(cache.getObservation()?.price).toBe(80.5);
    expect(cache.getRawLatestObservation()?.price).toBe(65.0);

    // Store a synthetic observation
    const syntheticObs: ValidatedObservation = {
      ...dummyObs,
      price: 50.0,
      is_synthetic: true,
    };
    cache.storeObservation(syntheticObs, now + 2000);

    // lastKnownGood must still remain 80.5
    expect(cache.getLastKnownGood()?.price).toBe(80.5);
  });

  it("supports hydration and dumping of cached state", () => {
    const cache1 = new CacheManager();
    cache1.storeObservation(dummyObs, 5000);

    const dumped = cache1.dump();
    expect(dumped.lastKnownGood?.price).toBe(80.5);
    expect(dumped.lastSuccessfulFetchMs).toBe(5000);

    const cache2 = new CacheManager();
    cache2.hydrate(dumped);
    expect(cache2.getLastKnownGood()?.price).toBe(80.5);
    expect(cache2.getLastSuccessfulFetchMs()).toBe(5000);
  });
});

