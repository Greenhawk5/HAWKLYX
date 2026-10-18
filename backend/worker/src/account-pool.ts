import { AccountHealthInfo, Env } from "./types";

export type QuarantineReason = "EXHAUSTED" | "RATE_LIMITED" | "INVALID_AUTH" | "DISABLED";

export interface AccountState {
  slot: number;
  key: string;
  usedToday: number;
  maxQuota: number;
  quarantinedUntil: number | null; // Unix timestamp ms, or null if active
  quarantineReason?: QuarantineReason;
  lastUsedAt: number | null;
}

export class AccountPoolManager {
  private accounts: AccountState[] = [];
  private lastResetDay: string = "";
  private globalDailyBudget: number = 200;
  private defaultAccountQuota: number = 50;

  constructor(env: Env) {
    this.initFromEnv(env);
  }

  public initFromEnv(env: Env): void {
    this.accounts = [];
    this.defaultAccountQuota = parseInt(env.MAX_DAILY_QUOTA_PER_ACCOUNT || "50", 10);
    this.globalDailyBudget = parseInt(env.GLOBAL_DAILY_BUDGET || "200", 10);

    const keys = [
      env.OILPRICEAPI_KEY_1,
      env.OILPRICEAPI_KEY_2,
      env.OILPRICEAPI_KEY_3,
      env.OILPRICEAPI_KEY_4,
      env.OILPRICEAPI_KEY_5,
    ];

    keys.forEach((key, index) => {
      const slot = index + 1;
      const trimmed = key ? key.trim() : "";
      if (trimmed.length > 0) {
        this.accounts.push({
          slot,
          key: trimmed,
          usedToday: 0,
          maxQuota: this.defaultAccountQuota,
          quarantinedUntil: null,
          lastUsedAt: null,
        });
      }
    });

    this.checkDayReset();
  }

  private getUtcDayString(now: Date = new Date()): string {
    return now.toISOString().slice(0, 10);
  }

  /**
   * Resets daily counters when a new UTC day starts.
   */
  public checkDayReset(now: Date = new Date()): void {
    const currentDay = this.getUtcDayString(now);
    if (this.lastResetDay !== currentDay) {
      this.lastResetDay = currentDay;
      for (const acc of this.accounts) {
        acc.usedToday = 0;
        // Unquarantine if previously exhausted by daily quota
        if (acc.quarantineReason === "EXHAUSTED") {
          acc.quarantinedUntil = null;
          delete acc.quarantineReason;
        }
      }
    }
  }

  /**
   * Returns total requests consumed across all accounts today.
   */
  public getTotalUsedToday(): number {
    this.checkDayReset();
    return this.accounts.reduce((sum, acc) => sum + acc.usedToday, 0);
  }

  /**
   * Checks whether the global daily budget has been reached.
   */
  public isGlobalBudgetExhausted(): boolean {
    return this.getTotalUsedToday() >= this.globalDailyBudget;
  }

  /**
   * Selects the next eligible account using least-used, then oldest-used strategy.
   * Returns null if no eligible account is available or global budget is exhausted.
   */
  public selectEligibleAccount(nowMs: number = Date.now()): AccountState | null {
    this.checkDayReset();

    if (this.isGlobalBudgetExhausted()) {
      return null;
    }

    const eligible = this.accounts.filter((acc) => {
      // Check permanent or temporary quarantine
      if (acc.quarantineReason === "INVALID_AUTH") {
        return false;
      }
      if (acc.quarantinedUntil !== null && nowMs < acc.quarantinedUntil) {
        return false;
      }
      // If temporary quarantine period has passed, restore
      if (acc.quarantinedUntil !== null && nowMs >= acc.quarantinedUntil) {
        acc.quarantinedUntil = null;
        delete acc.quarantineReason;
      }
      // Check per-account quota
      if (acc.usedToday >= acc.maxQuota) {
        return false;
      }
      return true;
    });

    if (eligible.length === 0) {
      return null;
    }

    // Sort by least used first, then by least recently used
    eligible.sort((a, b) => {
      if (a.usedToday !== b.usedToday) {
        return a.usedToday - b.usedToday;
      }
      return (a.lastUsedAt || 0) - (b.lastUsedAt || 0);
    });

    return eligible[0];
  }

  /**
   * Records a request made by an account.
   */
  public recordRequest(slot: number, nowMs: number = Date.now()): void {
    const acc = this.accounts.find((a) => a.slot === slot);
    if (acc) {
      acc.usedToday++;
      acc.lastUsedAt = nowMs;
      if (acc.usedToday >= acc.maxQuota) {
        acc.quarantineReason = "EXHAUSTED";
        // Quarantined until end of current UTC day
        const tomorrowUtc = new Date();
        tomorrowUtc.setUTCHours(24, 0, 0, 0);
        acc.quarantinedUntil = tomorrowUtc.getTime();
      }
    }
  }

  /**
   * Mark an account as having exhausted its upstream quota (HTTP 402 or provider quota message).
   */
  public markExhausted(slot: number, resetTimestampMs?: number): void {
    const acc = this.accounts.find((a) => a.slot === slot);
    if (acc) {
      acc.quarantineReason = "EXHAUSTED";
      if (resetTimestampMs && resetTimestampMs > Date.now()) {
        acc.quarantinedUntil = resetTimestampMs;
      } else {
        const tomorrowUtc = new Date();
        tomorrowUtc.setUTCHours(24, 0, 0, 0);
        acc.quarantinedUntil = tomorrowUtc.getTime();
      }
    }
  }

  /**
   * Mark an account rate-limited (HTTP 429).
   */
  public markRateLimited(slot: number, cooldownSeconds: number = 60): void {
    const acc = this.accounts.find((a) => a.slot === slot);
    if (acc) {
      acc.quarantineReason = "RATE_LIMITED";
      acc.quarantinedUntil = Date.now() + cooldownSeconds * 1000;
    }
  }

  /**
   * Mark an account with invalid credentials (HTTP 401/403).
   * Permanently quarantines until configuration restart.
   */
  public markInvalidAuth(slot: number): void {
    const acc = this.accounts.find((a) => a.slot === slot);
    if (acc) {
      acc.quarantineReason = "INVALID_AUTH";
      acc.quarantinedUntil = Number.MAX_SAFE_INTEGER;
    }
  }

  public getAccountsCount(): number {
    return this.accounts.length;
  }

  public getActiveAccountsCount(nowMs: number = Date.now()): number {
    return this.accounts.filter((acc) => {
      if (acc.quarantineReason === "INVALID_AUTH") return false;
      if (acc.quarantinedUntil !== null && nowMs < acc.quarantinedUntil) return false;
      if (acc.usedToday >= acc.maxQuota) return false;
      return true;
    }).length;
  }

  /**
   * Generates sanitized health information without exposing raw keys.
   */
  public getSanitizedHealth(nowMs: number = Date.now()): AccountHealthInfo[] {
    return [1, 2, 3, 4, 5].map((slot) => {
      const acc = this.accounts.find((a) => a.slot === slot);
      if (!acc) {
        return {
          slot,
          configured: false,
          active: false,
          used_today: 0,
          remaining_estimate: 0,
        };
      }
      const isActive =
        acc.quarantineReason !== "INVALID_AUTH" &&
        !(acc.quarantinedUntil !== null && nowMs < acc.quarantinedUntil) &&
        acc.usedToday < acc.maxQuota;

      return {
        slot,
        configured: true,
        active: isActive,
        used_today: acc.usedToday,
        remaining_estimate: Math.max(0, acc.maxQuota - acc.usedToday),
        quarantine_reason: acc.quarantineReason,
      };
    });
  }

  public hydrate(
    persistedAccounts: Array<{
      slot: number;
      usedToday: number;
      lastUsedAt: number | null;
      quarantinedUntil: number | null;
      quarantineReason?: QuarantineReason;
    }>,
    lastResetDay?: string
  ): void {
    if (lastResetDay) {
      this.lastResetDay = lastResetDay;
    }
    for (const p of persistedAccounts) {
      const acc = this.accounts.find((a) => a.slot === p.slot);
      if (acc) {
        acc.usedToday = p.usedToday;
        acc.lastUsedAt = p.lastUsedAt;
        acc.quarantinedUntil = p.quarantinedUntil;
        acc.quarantineReason = p.quarantineReason;
      }
    }
  }

  public dump(): {
    accounts: AccountState[];
    lastResetDay: string;
  } {
    return {
      accounts: this.accounts.map((a) => ({ ...a })),
      lastResetDay: this.lastResetDay,
    };
  }

  public getLastResetDay(): string {
    return this.lastResetDay;
  }

  public setLastResetDay(day: string): void {
    this.lastResetDay = day;
  }
}
