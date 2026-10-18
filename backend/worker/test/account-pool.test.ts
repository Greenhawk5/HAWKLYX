import { describe, expect, it } from "vitest";
import { AccountPoolManager } from "../src/account-pool";
import { Env } from "../src/types";

describe("AccountPoolManager", () => {
  it("initializes accounts from env and selects using least-used strategy", () => {
    const env: Env = {
      OILPRICEAPI_KEY_1: "key_alpha",
      OILPRICEAPI_KEY_2: "key_beta",
      OILPRICEAPI_KEY_3: "",
      MAX_DAILY_QUOTA_PER_ACCOUNT: "10",
      GLOBAL_DAILY_BUDGET: "15",
    };

    const pool = new AccountPoolManager(env);
    expect(pool.getAccountsCount()).toBe(2);
    expect(pool.getActiveAccountsCount()).toBe(2);

    const first = pool.selectEligibleAccount();
    expect(first).not.toBeNull();
    expect(first?.key).toBe("key_alpha");

    pool.recordRequest(first!.slot);
    expect(first?.usedToday).toBe(1);

    // Second pick should pick key_beta (least used)
    const second = pool.selectEligibleAccount();
    expect(second?.key).toBe("key_beta");
    pool.recordRequest(second!.slot);

    expect(pool.getTotalUsedToday()).toBe(2);
  });

  it("quarantines account on 401/403 invalid auth permanently", () => {
    const env: Env = {
      OILPRICEAPI_KEY_1: "bad_key",
      OILPRICEAPI_KEY_2: "good_key",
    };
    const pool = new AccountPoolManager(env);

    pool.markInvalidAuth(1);
    expect(pool.getActiveAccountsCount()).toBe(1);

    const selected = pool.selectEligibleAccount();
    expect(selected?.key).toBe("good_key");
  });

  it("handles quota exhaustion (402) and daily budget limits", () => {
    const env: Env = {
      OILPRICEAPI_KEY_1: "key_1",
      MAX_DAILY_QUOTA_PER_ACCOUNT: "2",
      GLOBAL_DAILY_BUDGET: "5",
    };
    const pool = new AccountPoolManager(env);

    pool.recordRequest(1);
    pool.recordRequest(1);

    expect(pool.selectEligibleAccount()).toBeNull();
    expect(pool.getActiveAccountsCount()).toBe(0);
  });

  it("enforces global daily budget even if accounts have remaining capacity", () => {
    const env: Env = {
      OILPRICEAPI_KEY_1: "key_1",
      OILPRICEAPI_KEY_2: "key_2",
      MAX_DAILY_QUOTA_PER_ACCOUNT: "10",
      GLOBAL_DAILY_BUDGET: "2",
    };
    const pool = new AccountPoolManager(env);

    pool.recordRequest(1);
    pool.recordRequest(2);

    expect(pool.isGlobalBudgetExhausted()).toBe(true);
    expect(pool.selectEligibleAccount()).toBeNull();
  });

  it("sanitizes health report without leaking raw keys", () => {
    const env: Env = {
      OILPRICEAPI_KEY_1: "secret_super_key_12345",
    };
    const pool = new AccountPoolManager(env);
    const health = pool.getSanitizedHealth();

    expect(health.length).toBe(5);
    const slot1 = health[0];
    expect(slot1.configured).toBe(true);
    expect(slot1.active).toBe(true);

    const serialized = JSON.stringify(health);
    expect(serialized).not.toContain("secret_super_key_12345");
  });
});
