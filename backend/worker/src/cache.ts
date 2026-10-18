import { ValidatedObservation } from "./types";

export interface CacheConfig {
  freshnessIntervalMinutes?: number;
  cooldownSeconds?: number;
}

export class CacheManager {
  private lastObservation: ValidatedObservation | null = null;
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
   * Returns true if cached observation exists and is within the freshness window,
   * and is not marked stale/synthetic upstream.
   */
  public isFresh(nowMs: number = Date.now()): boolean {
    if (!this.lastObservation || !this.lastSuccessfulFetchMs) {
      return false;
    }
    if (this.lastObservation.is_stale || this.lastObservation.is_synthetic) {
      return false;
    }
    const age = nowMs - this.lastSuccessfulFetchMs;
    return age <= this.freshnessIntervalMs;
  }

  /**
   * Checks whether a forced manual refresh is currently cooling down.
   */
  public isInCooldown(nowMs: number = Date.now()): boolean {
    return nowMs - this.lastUpstreamAttemptMs < this.cooldownIntervalMs;
  }

  /**
   * Computes the ISO-8601 timestamp after which the next forced refresh is permitted.
   */
  public getNextRefreshAllowedIso(nowMs: number = Date.now()): string {
    const nextAllowedMs = Math.max(nowMs, this.lastUpstreamAttemptMs + this.cooldownIntervalMs);
    return new Date(nextAllowedMs).toISOString();
  }

  /**
   * Atomically stores a validated observation.
   */
  public storeObservation(obs: ValidatedObservation, nowMs: number = Date.now()): void {
    this.lastObservation = obs;
    this.lastSuccessfulFetchMs = nowMs;
    this.lastUpstreamAttemptMs = nowMs;
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
