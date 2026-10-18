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

/**
 * In-memory SQLite storage mock matching Cloudflare Workers SQLite DO behavior
 * Supports cross-instance shared persistence for testing isolate crashes and restarts.
 */
export class MockSqlStorageEngine {
  public systemState = new Map<string, string>();
  public accountUsage = new Map<number, AccountUsageRow>();
  public observations = new Map<string, ObservationRow>();

  public createStorage(): SqlStorage {
    const engine = this;

    return {
      get databaseSize() {
        return 1024;
      },
      Cursor: {} as any,
      Statement: {} as any,

      exec<T extends Record<string, SqlStorageValue>>(query: string, ...bindings: any[]): SqlStorageCursor<T> {
        const q = query.trim();

        // CREATE TABLE statements
        if (q.toUpperCase().startsWith("CREATE TABLE")) {
          return emptyCursor<T>();
        }

        // system_state queries
        if (q.includes("FROM system_state")) {
          const rows: SystemStateRow[] = Array.from(engine.systemState.entries()).map(([k, v]) => ({
            key: k,
            value: v,
          }));
          return createCursor<T>(rows as unknown as T[]);
        }

        if (q.includes("INSERT OR REPLACE INTO system_state")) {
          const key = String(bindings[0]);
          const value = String(bindings[1]);
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
