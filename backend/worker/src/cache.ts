import { ValidatedObservation } from "./types";

export interface CacheConfig {
  freshnessIntervalMinutes?: number;
  cooldownSeconds?: number;
}

export class CacheManager {
  private lastObservation: ValidatedObservation | null = null;
  private lastKnownGood: ValidatedObservation | null = null;
  private lastSuccessfulFetchMs: number | null = null;
  private lastUpstreamAttemptMs: number = 0;
  private inFlightPromise: Promise<ValidatedObservation> | null = null;

  public freshnessIntervalMs: number;
  public cooldownIntervalMs: number;

  constructor(config: CacheConfig = {}) {
    this.freshnessIntervalMs = (config.freshnessIntervalMinutes ?? 15) * 60 * 1000;
    this.cooldownIntervalMs = (config.cooldownSeconds ?? 180) * 1000;
  }

  public getObservation(): ValidatedObservation | null {
    return this.lastKnownGood || this.lastObservation;
  }

  public getLastKnownGood(): ValidatedObservation | null {
    return this.lastKnownGood;
  }

  public getRawLatestObservation(): ValidatedObservation | null {
    return this.lastObservation;
  }

  public getLastSuccessfulFetchMs(): number | null {
    return this.lastSuccessfulFetchMs;
  }

  public getCacheAgeSeconds(nowMs: number = Date.now()): number | null {
    if (!this.lastSuccessfulFetchMs) return null;
    return Math.max(0, Math.floor((nowMs - this.lastSuccessfulFetchMs) / 1000));
  }

  /**
   * Returns true if a known-good observation exists within the freshness window,
   * and is not marked stale/synthetic.
   */
  public isFresh(nowMs: number = Date.now()): boolean {
    if (!this.lastKnownGood || !this.lastSuccessfulFetchMs) {
      return false;
    }
    if (this.lastKnownGood.is_stale || this.lastKnownGood.is_synthetic) {
      return false;
    }
    const age = nowMs - this.lastSuccessfulFetchMs;
    return age <= this.freshnessIntervalMs;
  }

  /**
   * Checks whether a manual refresh or recent upstream attempt is currently cooling down.
   */
  public isInCooldown(nowMs: number = Date.now()): boolean {
    return nowMs - this.lastUpstreamAttemptMs < this.cooldownIntervalMs;
  }

  /**
   * Computes the ISO-8601 timestamp after which the next refresh is permitted.
   */
  public getNextRefreshAllowedIso(nowMs: number = Date.now()): string {
    const nextAllowedMs = Math.max(nowMs, this.lastUpstreamAttemptMs + this.cooldownIntervalMs);
    return new Date(nextAllowedMs).toISOString();
  }

  /**
   * Stores an observation safely:
   * Stale or synthetic observations do NOT overwrite the last known-good valid observation.
   */
  public storeObservation(obs: ValidatedObservation, nowMs: number = Date.now()): void {
    this.lastObservation = obs;
    this.lastUpstreamAttemptMs = nowMs;
    if (!obs.is_stale && !obs.is_synthetic) {
      this.lastKnownGood = obs;
      this.lastSuccessfulFetchMs = nowMs;
    }
  }

  public hydrate(state: {
    lastObservation?: ValidatedObservation | null;
    lastKnownGood?: ValidatedObservation | null;
    lastSuccessfulFetchMs?: number | null;
    lastUpstreamAttemptMs?: number;
  }): void {
    if (state.lastObservation !== undefined) this.lastObservation = state.lastObservation;
    if (state.lastKnownGood !== undefined) this.lastKnownGood = state.lastKnownGood;
    if (state.lastSuccessfulFetchMs !== undefined) this.lastSuccessfulFetchMs = state.lastSuccessfulFetchMs;
    if (state.lastUpstreamAttemptMs !== undefined) this.lastUpstreamAttemptMs = state.lastUpstreamAttemptMs;
  }

  public dump(): {
    lastObservation: ValidatedObservation | null;
    lastKnownGood: ValidatedObservation | null;
    lastSuccessfulFetchMs: number | null;
    lastUpstreamAttemptMs: number;
  } {
    return {
      lastObservation: this.lastObservation,
      lastKnownGood: this.lastKnownGood,
      lastSuccessfulFetchMs: this.lastSuccessfulFetchMs,
      lastUpstreamAttemptMs: this.lastUpstreamAttemptMs,
    };
  }

  /**
   * Records that an upstream attempt took place (for cooldown tracking).
   */
  public recordUpstreamAttempt(nowMs: number = Date.now()): void {
    this.lastUpstreamAttemptMs = nowMs;
  }

  /**
   * Request coalescing: if an upstream fetch is already underway, returns the existing
   * pending Promise so concurrent requests don't duplicate upstream calls.
   */
  public async coalesce<T>(fetchFn: () => Promise<T>): Promise<T> {
    if (this.inFlightPromise) {
      return this.inFlightPromise as unknown as Promise<T>;
    }

    const promise = (async () => {
      try {
        const result = await fetchFn();
        return result;
      } finally {
        this.inFlightPromise = null;
      }
    })();

    this.inFlightPromise = promise as unknown as Promise<ValidatedObservation>;
    return promise;
  }
}
