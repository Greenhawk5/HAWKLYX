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
  private globalDailyBudget: number = 250;
  private automaticReserveBudget: number = 110;
  private manualBudgetCap: number = 140;
  private automaticUsedToday: number = 0;
  private manualUsedToday: number = 0;
  private defaultAccountQuota: number = 50;

  constructor(env: Env) {
    this.initFromEnv(env);
  }

  public initFromEnv(env: Env): void {
    this.accounts = [];
    this.defaultAccountQuota = parseInt(env.MAX_DAILY_QUOTA_PER_ACCOUNT || "50", 10);
    this.globalDailyBudget = parseInt(env.GLOBAL_DAILY_BUDGET || "250", 10);
    this.automaticReserveBudget = parseInt(env.AUTOMATIC_RESERVE_BUDGET || "110", 10);
    this.manualBudgetCap = parseInt(
      env.MANUAL_BUDGET_CAP || String(Math.max(0, this.globalDailyBudget - this.automaticReserveBudget)),
      10
    );

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
      this.automaticUsedToday = 0;
      this.manualUsedToday = 0;
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
    return this.automaticUsedToday + this.manualUsedToday;
  }

  public getAutomaticUsedToday(): number {
    this.checkDayReset();
    return this.automaticUsedToday;
  }

  public getManualUsedToday(): number {
    this.checkDayReset();
    return this.manualUsedToday;
  }

  public getAutomaticReserveBudget(): number {
    return this.automaticReserveBudget;
  }

  public getManualBudgetCap(): number {
    return this.manualBudgetCap;
  }

  public getGlobalDailyBudget(): number {
    return this.globalDailyBudget;
  }

  /**
   * Checks whether the global daily budget has been reached.
   */
  public isGlobalBudgetExhausted(): boolean {
    return this.getTotalUsedToday() >= this.globalDailyBudget;
  }

  /**
   * Manual requests are strictly capped to the manual allocation (140 max)
   * to guarantee the 110-request automatic reserve remains untouched.
   */
  public isManualBudgetExhausted(): boolean {
    this.checkDayReset();
    return this.manualUsedToday >= this.manualBudgetCap || this.isGlobalBudgetExhausted();
  }

  /**
   * Automatic requests may consume the protected automatic reserve (110)
   * plus any genuinely unused manual allowance up to the global limit (250).
   */
  public isAutomaticBudgetExhausted(): boolean {
    this.checkDayReset();
    return this.isGlobalBudgetExhausted();
  }

  public isBudgetExhaustedFor(requestClass: "automatic" | "manual"): boolean {
    if (requestClass === "manual") {
      return this.isManualBudgetExhausted();
    }
    return this.isAutomaticBudgetExhausted();
  }

  /**
   * Selects the next eligible account using least-used, then oldest-used strategy.
   * Returns null if no eligible account is available or budget for this request class is exhausted.
   */
  public selectEligibleAccount(
    nowMs: number = Date.now(),
    requestClass: "automatic" | "manual" = "automatic"
  ): AccountState | null {
    this.checkDayReset();

    if (this.isBudgetExhaustedFor(requestClass)) {
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
   * Records an upstream request made by an account and attributes it to the
   * correct daily budget class (automatic reserve vs manual allocation).
   */
  public recordRequest(
    slot: number,
    requestClass: "automatic" | "manual" = "automatic",
    nowMs: number = Date.now()
  ): void {
    const acc = this.accounts.find((a) => a.slot === slot);
    if (acc) {
      acc.usedToday++;
      acc.lastUsedAt = nowMs;
      if (requestClass === "manual") {
        this.manualUsedToday++;
      } else {
        this.automaticUsedToday++;
      }
      if (acc.usedToday >= acc.maxQuota) {
        acc.quarantineReason = "EXHAUSTED";
        // Quarantined until end of current UTC day
        const tomorrowUtc = new Date(nowMs);
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
    lastResetDay?: string,
    usageStats?: {
      automaticUsedToday?: number;
      manualUsedToday?: number;
    }
  ): void {
    if (lastResetDay) {
      this.lastResetDay = lastResetDay;
    }
    if (usageStats?.automaticUsedToday !== undefined) {
      this.automaticUsedToday = usageStats.automaticUsedToday;
    }
    if (usageStats?.manualUsedToday !== undefined) {
      this.manualUsedToday = usageStats.manualUsedToday;
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
    automaticUsedToday: number;
    manualUsedToday: number;
  } {
    return {
      accounts: this.accounts.map((a) => ({ ...a })),
      lastResetDay: this.lastResetDay,
      automaticUsedToday: this.automaticUsedToday,
      manualUsedToday: this.manualUsedToday,
    };
  }

  public getLastResetDay(): string {
    return this.lastResetDay;
  }

  public setLastResetDay(day: string): void {
    this.lastResetDay = day;
  }
}
