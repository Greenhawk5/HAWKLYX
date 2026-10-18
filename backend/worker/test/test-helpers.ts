import { SqlStorage, SqlStorageCursor, SqlStorageValue } from "@cloudflare/workers-types";
import { ValidatedObservation } from "../src/types";

interface SystemStateRow {
  key: string;
  value: string;
}

interface AccountUsageRow {
  slot: number;
  used_today: number;
  last_used_at: number | null;
  quarantined_until: number | null;
  quarantine_reason: string | null;
}

interface ObservationRow {
  key: string;
  data: string;
  updated_at: number;
}

export interface PriceObservationRow {
  id: number;
  instrument: string;
  bucket_hour_utc: string;
  price: number;
  currency: string;
  unit: string;
  as_of: string;
  fetched_at: string;
  source: string;
  created_at: number;
}

export interface PriceDailyRollupRow {
  instrument: string;
  trading_date_utc: string;
  open: number;
  high: number;
  low: number;
  close: number;
  first_as_of: string;
  last_as_of: string;
  sample_count: number;
  is_complete: number;
  updated_at: number;
}

/**
 * In-memory SQLite storage mock matching Cloudflare Workers SQLite DO behavior
 * Supports cross-instance shared persistence for testing isolate crashes and restarts.
 */
export class MockSqlStorageEngine {
  public systemState = new Map<string, string>();
  public accountUsage = new Map<number, AccountUsageRow>();
  public observations = new Map<string, ObservationRow>();
  public priceObservations = new Map<string, PriceObservationRow>();
  public priceDailyRollups = new Map<string, PriceDailyRollupRow>();
  private nextObsId = 1;

  public createStorage(): SqlStorage {
    const engine = this;

    return {
      get databaseSize() {
        return 1024;
      },
      Cursor: {} as any,
      Statement: {} as any,

      exec<T extends Record<string, SqlStorageValue>>(query: string, ...bindings: any[]): SqlStorageCursor<T> {
        const q = query.trim().replace(/\s+/g, " ");

        // CREATE TABLE / INDEX statements
        if (q.toUpperCase().startsWith("CREATE TABLE") || q.toUpperCase().startsWith("CREATE INDEX")) {
          return emptyCursor<T>();
        }

        // system_state queries
        if (q.includes("FROM system_state")) {
          if (q.includes("WHERE key =")) {
            const key = String(bindings[0]);
            const val = engine.systemState.get(key);
            const rows: SystemStateRow[] = val !== undefined ? [{ key, value: val }] : [];
            return createCursor<T>(rows as unknown as T[]);
          }
          const rows: SystemStateRow[] = Array.from(engine.systemState.entries()).map(([k, v]) => ({
            key: k,
            value: v,
          }));
          return createCursor<T>(rows as unknown as T[]);
        }

        if (q.includes("INSERT OR REPLACE INTO system_state") || q.includes("INSERT OR IGNORE INTO system_state")) {
          const key = String(bindings[0]);
          const value = String(bindings[1]);
          if (q.includes("INSERT OR IGNORE") && engine.systemState.has(key)) {
            return emptyCursor<T>();
          }
          engine.systemState.set(key, value);
          return emptyCursor<T>();
        }

        // account_usage queries
        if (q.includes("FROM account_usage")) {
          const rows: AccountUsageRow[] = Array.from(engine.accountUsage.values());
          return createCursor<T>(rows as unknown as T[]);
        }

        if (q.includes("INSERT OR REPLACE INTO account_usage")) {
          const slot = Number(bindings[0]);
          const row: AccountUsageRow = {
            slot,
            used_today: Number(bindings[1]),
            last_used_at: bindings[2] !== null && bindings[2] !== undefined ? Number(bindings[2]) : null,
            quarantined_until: bindings[3] !== null && bindings[3] !== undefined ? Number(bindings[3]) : null,
            quarantine_reason: bindings[4] !== null && bindings[4] !== undefined ? String(bindings[4]) : null,
          };
          engine.accountUsage.set(slot, row);
          return emptyCursor<T>();
        }

        if (q.includes("UPDATE account_usage")) {
          for (const row of engine.accountUsage.values()) {
            row.used_today = 0;
            if (row.quarantine_reason === "EXHAUSTED") {
              row.quarantined_until = null;
              row.quarantine_reason = null;
            }
          }
          return emptyCursor<T>();
        }

        // observations queries
        if (q.includes("FROM observations")) {
          const rows: ObservationRow[] = Array.from(engine.observations.values());
          return createCursor<T>(rows as unknown as T[]);
        }

        if (q.includes("INSERT OR REPLACE INTO observations")) {
          const key = String(bindings[0]);
          const data = String(bindings[1]);
          const updated_at = Number(bindings[2]);
          engine.observations.set(key, { key, data, updated_at });
          return emptyCursor<T>();
        }

        // price_observations: guard against duplicate as_of
        if (q.includes("FROM price_observations WHERE instrument = ? AND as_of = ?")) {
          const inst = String(bindings[0]);
          const asOf = String(bindings[1]);
          const match = Array.from(engine.priceObservations.values()).find(
            (o) => o.instrument === inst && o.as_of === asOf
          );
          return createCursor<T>(match ? ([{ id: match.id }] as unknown as T[]) : []);
        }

        // price_observations: INSERT with ON CONFLICT DO UPDATE WHERE excluded.as_of > price_observations.as_of
        if (q.includes("INSERT INTO price_observations")) {
          const instrument = String(bindings[0]);
          const bucketHourUtc = String(bindings[1]);
          const price = Number(bindings[2]);
          const currency = String(bindings[3]);
          const unit = String(bindings[4]);
          const asOf = String(bindings[5]);
          const fetchedAt = String(bindings[6]);
          const source = String(bindings[7]);
          const createdAt = Number(bindings[8]);
          const key = `${instrument}:${bucketHourUtc}`;
          const existing = engine.priceObservations.get(key);

          if (!existing) {
            const row: PriceObservationRow = {
              id: engine.nextObsId++,
              instrument,
              bucket_hour_utc: bucketHourUtc,
              price,
              currency,
              unit,
              as_of: asOf,
              fetched_at: fetchedAt,
              source,
              created_at: createdAt,
            };
            engine.priceObservations.set(key, row);
          } else if (asOf > existing.as_of) {
            existing.price = price;
            existing.currency = currency;
            existing.unit = unit;
            existing.as_of = asOf;
            existing.fetched_at = fetchedAt;
            existing.source = source;
            existing.created_at = createdAt;
          }
          return emptyCursor<T>();
        }

        // price_observations: min bucket query
        if (q.includes("SELECT MIN(bucket_hour_utc) AS min_bucket")) {
          const inst = String(bindings[0]);
          const buckets = Array.from(engine.priceObservations.values())
            .filter((o) => o.instrument === inst)
            .map((o) => o.bucket_hour_utc)
            .sort();
          const min = buckets.length > 0 ? buckets[0] : null;
          return createCursor<T>([{ min_bucket: min }] as unknown as T[]);
        }

        // price_observations: hourly range query
        if (q.includes("FROM price_observations WHERE instrument = ? AND bucket_hour_utc >= ? AND bucket_hour_utc <= ?")) {
          const inst = String(bindings[0]);
          const startBucket = String(bindings[1]);
          const endBucket = String(bindings[2]);
          const filtered = Array.from(engine.priceObservations.values())
            .filter((o) => o.instrument === inst && o.bucket_hour_utc >= startBucket && o.bucket_hour_utc <= endBucket)
            .sort((a, b) => a.bucket_hour_utc.localeCompare(b.bucket_hour_utc));
          return createCursor<T>(filtered as unknown as T[]);
        }

        // price_observations: unrolled date range query
        if (q.includes("FROM price_observations WHERE instrument = ? AND substr(bucket_hour_utc, 1, 10) >= ?")) {
          const inst = String(bindings[0]);
          const startDate = String(bindings[1]);
          const endDate = String(bindings[2]);
          const filtered = Array.from(engine.priceObservations.values())
            .filter(
              (o) =>
                o.instrument === inst &&
                o.bucket_hour_utc.slice(0, 10) >= startDate &&
                o.bucket_hour_utc.slice(0, 10) <= endDate
            )
            .sort((a, b) => a.bucket_hour_utc.localeCompare(b.bucket_hour_utc));
          return createCursor<T>(filtered as unknown as T[]);
        }

        // price_daily_rollups: roll up past days
        if (q.includes("INSERT OR REPLACE INTO price_daily_rollups")) {
          const updatedAt = Number(bindings[0]);
          const currentDay = String(bindings[1]);
          const completedByDate = new Map<string, PriceObservationRow[]>();
          for (const obs of engine.priceObservations.values()) {
            const date = obs.bucket_hour_utc.slice(0, 10);
            if (date < currentDay) {
              const list = completedByDate.get(date) || [];
              list.push(obs);
              completedByDate.set(date, list);
            }
          }
          for (const [date, list] of completedByDate.entries()) {
            list.sort((a, b) => a.bucket_hour_utc.localeCompare(b.bucket_hour_utc));
            const prices = list.map((l) => l.price);
            const key = `${list[0].instrument}:${date}`;
            engine.priceDailyRollups.set(key, {
              instrument: list[0].instrument,
              trading_date_utc: date,
              open: list[0].price,
              high: Math.max(...prices),
              low: Math.min(...prices),
              close: list[list.length - 1].price,
              first_as_of: list[0].as_of,
              last_as_of: list[list.length - 1].as_of,
              sample_count: list.length,
              is_complete: list.length >= 20 ? 1 : 0,
              updated_at: updatedAt,
            });
          }
          return emptyCursor<T>();
        }

        // price_daily_rollups: range query
        if (q.includes("FROM price_daily_rollups WHERE instrument = ? AND trading_date_utc >= ? AND trading_date_utc <= ?")) {
          const inst = String(bindings[0]);
          const startDate = String(bindings[1]);
          const endDate = String(bindings[2]);
          const filtered = Array.from(engine.priceDailyRollups.values())
            .filter((r) => r.instrument === inst && r.trading_date_utc >= startDate && r.trading_date_utc <= endDate)
            .sort((a, b) => a.trading_date_utc.localeCompare(b.trading_date_utc));
          return createCursor<T>(filtered as unknown as T[]);
        }

        // price_observations: bounded cleanup
        if (q.includes("DELETE FROM price_observations")) {
          const cutoffDate = String(bindings[0]);
          for (const [key, obs] of Array.from(engine.priceObservations.entries())) {
            const date = obs.bucket_hour_utc.slice(0, 10);
            if (date < cutoffDate && engine.priceDailyRollups.has(`${obs.instrument}:${date}`)) {
              engine.priceObservations.delete(key);
            }
          }
          return emptyCursor<T>();
        }

        return emptyCursor<T>();
      },
    };
  }
}

function emptyCursor<T extends Record<string, SqlStorageValue>>(): SqlStorageCursor<T> {
  return createCursor<T>([]);
}

function createCursor<T extends Record<string, SqlStorageValue>>(items: T[]): SqlStorageCursor<T> {
  let index = 0;
  return {
    toArray: () => [...items],
    one: () => {
      if (items.length === 0) throw new Error("No rows returned from query");
      return items[0];
    },
    next: () => {
      if (index < items.length) {
        return { value: items[index++], done: false };
      }
      return { value: undefined as any, done: true };
    },
    raw: function* () {
      for (const item of items) {
        yield Object.values(item) as any;
      }
    },
    columnNames: items.length > 0 ? Object.keys(items[0]) : [],
    get rowsRead() {
      return items.length;
    },
    get rowsWritten() {
      return 0;
    },
    [Symbol.iterator]: function* () {
      for (const item of items) {
        yield item;
      }
    },
  };
}

export function createMockDurableObjectState(engine: MockSqlStorageEngine = new MockSqlStorageEngine()): DurableObjectState {
  const sql = engine.createStorage();

  const mockState: Partial<DurableObjectState> = {
    id: {
      toString: () => "mock-hawklyx-coordinator-id",
      equals: () => true,
      name: "global",
    },
    storage: {
      sql,
      setAlarm: async () => {},
      getAlarm: async () => null,
      deleteAlarm: async () => {},
    } as any,
    blockConcurrencyWhile: async (fn: () => Promise<any>) => {
      return await fn();
    },
    waitUntil: (promise: Promise<any>) => {
      // Fire and forget
      promise.catch(() => {});
    },
  };

  return mockState as DurableObjectState;
}

export const sampleValidPriceResponse = (price: number = 82.5, stale: boolean = false, synthetic: boolean = false) => ({
  status: "success",
  data: {
    price,
    formatted: `$${price.toFixed(2)}`,
    currency: "USD",
    code: "BRENT_CRUDE_USD",
    created_at: "2026-10-18T10:00:00Z",
    as_of: "2026-10-18T09:55:00Z",
    collected_at: "2026-10-18T10:00:05Z",
    stale,
    synthetic,
    source: "OilPriceAPI",
  },
});
