import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HawklyxCoordinator } from "../src/coordinator";
import { Env, ValidatedObservation } from "../src/types";
import { createMockDurableObjectState, MockSqlStorageEngine } from "./test-helpers";

describe("HAWKLYX Phase 1 & 2: Historical Storage & Read-Only History API", () => {
  let env: Env;
  let mockEngine: MockSqlStorageEngine;
  let coordinator: HawklyxCoordinator;
  let currentTimeMs: number;

  const createSampleObs = (
    price: number,
    asOf: string,
    fetchedAt: string = asOf,
    isStale: boolean = false,
    isSynthetic: boolean = false
  ): ValidatedObservation => ({
    code: "BRENT_CRUDE_USD",
    name: "Brent Crude Oil",
    price,
    currency: "USD",
    unit: "USD / barrel",
    formatted: `$${price.toFixed(2)}`,
    as_of: asOf,
    collected_at: asOf,
    created_at: asOf,
    fetched_at: fetchedAt,
    is_stale: isStale,
    is_synthetic: isSynthetic,
    source: "OilPriceAPI",
  });

  beforeEach(() => {
    mockEngine = new MockSqlStorageEngine();
    env = {
      OILPRICEAPI_KEY_1: "test-key-1",
      GLOBAL_DAILY_BUDGET: "250",
      AUTOMATIC_RESERVE_BUDGET: "110",
      MANUAL_BUDGET_CAP: "140",
    };
    currentTimeMs = Date.parse("2026-10-18T10:05:00Z");
    const ctx = createMockDurableObjectState(mockEngine);
    coordinator = new HawklyxCoordinator(ctx, env, { now: () => currentTimeMs });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Hourly Deduplication & Storage Integrity", () => {
    it("enforces at most one canonical observation per UTC hour", async () => {
      // First observation at 10:05 UTC
      currentTimeMs = Date.parse("2026-10-18T10:05:00Z");
      const obs1 = createSampleObs(82.5, "2026-10-18T10:00:00Z");
      const inserted1 = coordinator.recordHourlyObservation(obs1, currentTimeMs);
      expect(inserted1).toBe(true);
      expect(mockEngine.priceObservations.size).toBe(1);

      // Second observation at 10:20 UTC in the same hour with same as_of
      currentTimeMs = Date.parse("2026-10-18T10:20:00Z");
      const obs2 = createSampleObs(82.5, "2026-10-18T10:00:00Z");
      coordinator.recordHourlyObservation(obs2, currentTimeMs);
      expect(mockEngine.priceObservations.size).toBe(1);

      // Third observation at 10:50 UTC in the same hour
      currentTimeMs = Date.parse("2026-10-18T10:50:00Z");
      const obs3 = createSampleObs(82.7, "2026-10-18T10:45:00Z");
      coordinator.recordHourlyObservation(obs3, currentTimeMs);
      expect(mockEngine.priceObservations.size).toBe(1);

      // The canonical observation for 10:00 UTC should be updated to the latest observation (obs3 at 82.7)
      const canonical = mockEngine.priceObservations.get("BRENT_CRUDE_USD:2026-10-18T10:00:00Z");
      expect(canonical).toBeDefined();
      expect(canonical?.price).toBe(82.7);
      expect(canonical?.as_of).toBe("2026-10-18T10:45:00Z");
    });

    it("creates distinct canonical records across consecutive UTC hours", async () => {
      // 10:00 UTC hour
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      coordinator.recordHourlyObservation(createSampleObs(82.5, "2026-10-18T10:10:00Z"), currentTimeMs);

      // 11:00 UTC hour
      currentTimeMs = Date.parse("2026-10-18T11:15:00Z");
      coordinator.recordHourlyObservation(createSampleObs(83.1, "2026-10-18T11:10:00Z"), currentTimeMs);

      // 12:00 UTC hour
      currentTimeMs = Date.parse("2026-10-18T12:05:00Z");
      coordinator.recordHourlyObservation(createSampleObs(82.9, "2026-10-18T12:00:00Z"), currentTimeMs);

      expect(mockEngine.priceObservations.size).toBe(3);
      expect(mockEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-18T10:00:00Z")).toBe(true);
      expect(mockEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-18T11:00:00Z")).toBe(true);
      expect(mockEngine.priceObservations.has("BRENT_CRUDE_USD:2026-10-18T12:00:00Z")).toBe(true);
    });

    it("retains identical prices in separate hours when source timestamp advances", async () => {
      // Hour 10: price = 82.50, as_of = 10:00:00Z
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      coordinator.recordHourlyObservation(createSampleObs(82.5, "2026-10-18T10:00:00Z"), currentTimeMs);

      // Hour 11: price is still 82.50, but as_of advanced to 11:00:00Z
      currentTimeMs = Date.parse("2026-10-18T11:15:00Z");
      const inserted = coordinator.recordHourlyObservation(createSampleObs(82.5, "2026-10-18T11:00:00Z"), currentTimeMs);
      expect(inserted).toBe(true);

      expect(mockEngine.priceObservations.size).toBe(2);
      expect(mockEngine.priceObservations.get("BRENT_CRUDE_USD:2026-10-18T10:00:00Z")?.price).toBe(82.5);
      expect(mockEngine.priceObservations.get("BRENT_CRUDE_USD:2026-10-18T11:00:00Z")?.price).toBe(82.5);
    });

    it("does NOT record new observation when source timestamp (as_of) is unchanged across hours", async () => {
      // Friday market close observation at 21:55:00Z
      currentTimeMs = Date.parse("2026-10-16T22:00:00Z");
      coordinator.recordHourlyObservation(createSampleObs(86.25, "2026-10-16T21:55:00Z"), currentTimeMs);
      expect(mockEngine.priceObservations.size).toBe(1);

      // Saturday morning (market closed) - cached or frozen provider observation with SAME as_of
      currentTimeMs = Date.parse("2026-10-17T08:00:00Z");
      const inserted = coordinator.recordHourlyObservation(
        createSampleObs(86.25, "2026-10-16T21:55:00Z", "2026-10-17T08:00:00Z"),
        currentTimeMs
      );
      expect(inserted).toBe(false);
      // Still strictly 1 observation - no duplicate rows during closure
      expect(mockEngine.priceObservations.size).toBe(1);
    });

    it("rejects stale or synthetic observations from entering historical storage", async () => {
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      const staleObs = createSampleObs(82.5, "2026-10-18T10:00:00Z", "2026-10-18T10:15:00Z", true, false);
      const syntheticObs = createSampleObs(82.5, "2026-10-18T10:00:00Z", "2026-10-18T10:15:00Z", false, true);

      expect(coordinator.recordHourlyObservation(staleObs, currentTimeMs)).toBe(false);
      expect(coordinator.recordHourlyObservation(syntheticObs, currentTimeMs)).toBe(false);
      expect(mockEngine.priceObservations.size).toBe(0);
    });

    it("rejects missing, empty, or malformed as_of timestamps", async () => {
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      const emptyAsOf = createSampleObs(82.5, "");
      const invalidAsOf = createSampleObs(82.5, "not-a-valid-date");

      expect(coordinator.recordHourlyObservation(emptyAsOf, currentTimeMs)).toBe(false);
      expect(coordinator.recordHourlyObservation(invalidAsOf, currentTimeMs)).toBe(false);
      expect(mockEngine.priceObservations.size).toBe(0);
    });

    it("rejects future timestamps beyond plausible clock skew", async () => {
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      // Observation claiming to be 30 minutes in the future
      const futureAsOf = createSampleObs(82.5, "2026-10-18T10:45:00Z");

      expect(coordinator.recordHourlyObservation(futureAsOf, currentTimeMs)).toBe(false);
      expect(mockEngine.priceObservations.size).toBe(0);
    });

    it("rejects timestamps older than 7 days as fresh live observations", async () => {
      currentTimeMs = Date.parse("2026-10-18T10:15:00Z");
      // Observation claiming to be from 10 days ago
      const ancientAsOf = createSampleObs(82.5, "2026-10-08T10:15:00Z");

      expect(coordinator.recordHourlyObservation(ancientAsOf, currentTimeMs)).toBe(false);
      expect(mockEngine.priceObservations.size).toBe(0);
    });
  });

  describe("Read-Only History API Endpoint", () => {
    it("returns empty history response honestly when no data is collected yet", async () => {
      const res = await coordinator.handleHistoryRequest({ range: "30d" });
      expect(res.status).toBe(200);
      expect(res.body.status).toBe("empty");
      expect(res.body.count).toBe(0);
      expect(res.body.points).toEqual([]);
      expect(res.body.summary).toBeNull();
      expect(res.body.is_partial).toBe(false);
    });

    it("returns hourly points and accurate summary statistics for 1d, 7d, and 30d", async () => {
      // Seed 5 hourly observations
      const baseTime = Date.parse("2026-10-18T08:00:00Z");
      const testPrices = [80.0, 81.5, 82.0, 81.0, 83.0];

      testPrices.forEach((p, idx) => {
        const time = baseTime + idx * 3600 * 1000;
        const iso = new Date(time).toISOString();
        coordinator.recordHourlyObservation(createSampleObs(p, iso), time);
      });

      currentTimeMs = Date.parse("2026-10-18T13:00:00Z");
      const res = await coordinator.handleHistoryRequest({ range: "1d" });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe("success");
      expect(res.body.count).toBe(5);
      expect(res.body.resolution).toBe("hourly");
      expect(res.body.points.length).toBe(5);

      // Verify points chronological order
      expect(res.body.points[0].price).toBe(80.0);
      expect(res.body.points[4].price).toBe(83.0);

      // Verify summary statistics
      const summary = res.body.summary!;
      expect(summary.first_price).toBe(80.0);
      expect(summary.latest_price).toBe(83.0);
      expect(summary.change_absolute).toBe(3.0);
      expect(summary.change_percent).toBe(3.75); // (3.0 / 80.0) * 100
      expect(summary.high).toBe(83.0);
      expect(summary.low).toBe(80.0);
    });

    it("reports is_partial=true when requested range extends earlier than collection_start_date", async () => {
      // Single observation today
      currentTimeMs = Date.parse("2026-10-18T12:00:00Z");
      coordinator.recordHourlyObservation(createSampleObs(82.5, "2026-10-18T12:00:00Z"), currentTimeMs);

      // Request 30d range (which starts 30 days prior, whereas collection started today)
      const res = await coordinator.handleHistoryRequest({ range: "30d" });
      expect(res.body.is_partial).toBe(true);
      expect(res.body.collection_start_date).toBe("2026-10-18T12:00:00Z");
    });

    it("makes ZERO upstream provider requests and does not consume quotas", async () => {
      // Seed observation
      coordinator.recordHourlyObservation(createSampleObs(82.5, "2026-10-18T10:00:00Z"), currentTimeMs);

      const usedBefore = mockEngine.accountUsage.get(1)?.used_today || 0;
      const autoBefore = Number(mockEngine.systemState.get("automatic_used_today") || "0");
      const manualBefore = Number(mockEngine.systemState.get("manual_used_today") || "0");

      // Repeated history requests
      await coordinator.handleHistoryRequest({ range: "1d" });
      await coordinator.handleHistoryRequest({ range: "7d" });
      await coordinator.handleHistoryRequest({ range: "30d" });
      await coordinator.handleHistoryRequest({ range: "6m" });
      await coordinator.handleHistoryRequest({ range: "1y" });

      const usedAfter = mockEngine.accountUsage.get(1)?.used_today || 0;
      const autoAfter = Number(mockEngine.systemState.get("automatic_used_today") || "0");
      const manualAfter = Number(mockEngine.systemState.get("manual_used_today") || "0");

      expect(usedAfter).toBe(usedBefore);
      expect(autoAfter).toBe(autoBefore);
      expect(manualAfter).toBe(manualBefore);
    });
  });

  describe("Daily Rollups & Retention Cleanup", () => {
    it("generates daily OHLC rollups for completed past days on day reset", async () => {
      // Seed observations across October 16 and October 17
      const oct16Prices = [80.0, 84.0, 79.5, 83.5];
      oct16Prices.forEach((p, idx) => {
        const time = Date.parse(`2026-10-16T${10 + idx}:00:00Z`);
        coordinator.recordHourlyObservation(createSampleObs(p, new Date(time).toISOString()), time);
      });

      const oct17Prices = [83.0, 85.0, 82.0, 84.5];
      oct17Prices.forEach((p, idx) => {
        const time = Date.parse(`2026-10-17T${10 + idx}:00:00Z`);
        coordinator.recordHourlyObservation(createSampleObs(p, new Date(time).toISOString()), time);
      });

      // Transition to October 19 (triggers checkAndPersistDayReset)
      currentTimeMs = Date.parse("2026-10-19T00:05:00Z");
      await coordinator.getHealth(); // Triggers checkAndPersistDayReset

      expect(mockEngine.priceDailyRollups.size).toBe(2);

      const oct16Rollup = mockEngine.priceDailyRollups.get("BRENT_CRUDE_USD:2026-10-16");
      expect(oct16Rollup).toBeDefined();
      expect(oct16Rollup?.open).toBe(80.0);
      expect(oct16Rollup?.high).toBe(84.0);
      expect(oct16Rollup?.low).toBe(79.5);
      expect(oct16Rollup?.close).toBe(83.5);
      expect(oct16Rollup?.sample_count).toBe(4);

      const oct17Rollup = mockEngine.priceDailyRollups.get("BRENT_CRUDE_USD:2026-10-17");
      expect(oct17Rollup).toBeDefined();
      expect(oct17Rollup?.open).toBe(83.0);
      expect(oct17Rollup?.high).toBe(85.0);
      expect(oct17Rollup?.low).toBe(82.0);
      expect(oct17Rollup?.close).toBe(84.5);
    });

    it("serves daily resolution for 6m and 1y ranges using rollups and current day points", async () => {
      // Seed completed day rollup for October 16
      mockEngine.priceDailyRollups.set("BRENT_CRUDE_USD:2026-10-16", {
        instrument: "BRENT_CRUDE_USD",
        trading_date_utc: "2026-10-16",
        open: 81.0,
        high: 83.5,
        low: 80.5,
        close: 82.0,
        first_as_of: "2026-10-16T08:00:00Z",
        last_as_of: "2026-10-16T22:00:00Z",
        sample_count: 14,
        is_complete: 1,
        updated_at: Date.parse("2026-10-17T00:00:00Z"),
      });

      // Seed ongoing current day observations for October 18
      currentTimeMs = Date.parse("2026-10-18T14:00:00Z");
      coordinator.recordHourlyObservation(createSampleObs(84.0, "2026-10-18T10:00:00Z"), Date.parse("2026-10-18T10:00:00Z"));
      coordinator.recordHourlyObservation(createSampleObs(85.0, "2026-10-18T14:00:00Z"), currentTimeMs);

      const res = await coordinator.handleHistoryRequest({ range: "6m" });

      expect(res.status).toBe(200);
      expect(res.body.resolution).toBe("daily");
      expect(res.body.count).toBe(2);

      // Points must contain both the rolled-up past day and the aggregated ongoing day
      expect(res.body.points[0].timestamp).toBe("2026-10-16");
      expect(res.body.points[0].close).toBe(82.0);
      expect(res.body.points[1].timestamp).toBe("2026-10-18");
      expect(res.body.points[1].close).toBe(85.0);
    });

    it("distinguishes complete vs partial day rollups using sample_count and is_complete", async () => {
      // Completed day with 22 hourly samples (e.g. standard active trading day)
      for (let h = 0; h < 22; h++) {
        const hStr = h < 10 ? `0${h}` : `${h}`;
        const time = Date.parse(`2026-10-15T${hStr}:10:00Z`);
        coordinator.recordHourlyObservation(createSampleObs(80.0 + h * 0.1, `2026-10-15T${hStr}:05:00Z`), time);
      }

      // Partial day with only 4 hourly samples (e.g. early close or collection start)
      for (let h = 0; h < 4; h++) {
        const hStr = `0${h}`;
        const time = Date.parse(`2026-10-16T${hStr}:10:00Z`);
        coordinator.recordHourlyObservation(createSampleObs(82.0 + h * 0.1, `2026-10-16T${hStr}:05:00Z`), time);
      }

      // Roll up on October 17
      currentTimeMs = Date.parse("2026-10-17T00:05:00Z");
      await coordinator.alarm();

      const oct15 = mockEngine.priceDailyRollups.get("BRENT_CRUDE_USD:2026-10-15");
      const oct16 = mockEngine.priceDailyRollups.get("BRENT_CRUDE_USD:2026-10-16");

      expect(oct15?.is_complete).toBe(1);
      expect(oct15?.sample_count).toBe(22);

      expect(oct16?.is_complete).toBe(0);
      expect(oct16?.sample_count).toBe(4);
    });

    it("triggers daily rollup and retention maintenance on DO alarm", async () => {
      // Seed observation on past day
      const pastTime = Date.parse("2026-10-16T12:00:00Z");
      coordinator.recordHourlyObservation(createSampleObs(81.0, "2026-10-16T12:00:00Z"), pastTime);

      // Invoke alarm after day boundary
      currentTimeMs = Date.parse("2026-10-17T00:00:05Z");
      await coordinator.alarm();

      // Daily rollup must be generated
      expect(mockEngine.priceDailyRollups.has("BRENT_CRUDE_USD:2026-10-16")).toBe(true);
    });
  });
});
