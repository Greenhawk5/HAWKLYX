import { OilPriceApiRawResponse, ValidatedObservation } from "./types";

export class ProviderAuthError extends Error {
  constructor(message: string = "Invalid provider API credentials") {
    super(message);
    this.name = "ProviderAuthError";
  }
}

export class ProviderQuotaExhaustedError extends Error {
  public resetTimeMs?: number;
  constructor(message: string = "Upstream provider daily quota exhausted", resetTimeMs?: number) {
    super(message);
    this.name = "ProviderQuotaExhaustedError";
    this.resetTimeMs = resetTimeMs;
  }
}

export class ProviderRateLimitedError extends Error {
  public retryAfterSeconds: number;
  constructor(message: string = "Upstream provider rate limited", retryAfterSeconds: number = 60) {
    super(message);
    this.name = "ProviderRateLimitedError";
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class ProviderServerError extends Error {
  constructor(message: string = "Upstream provider server error") {
    super(message);
    this.name = "ProviderServerError";
  }
}

export class ProviderValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ProviderValidationError";
  }
}

export interface ProviderConfig {
  baseUrl?: string;
  sanityMinPrice?: number;
  sanityMaxPrice?: number;
  timeoutMs?: number;
}

export class OilPriceProvider {
  private baseUrl: string;
  private sanityMinPrice: number;
  private sanityMaxPrice: number;
  private timeoutMs: number;

  constructor(config: ProviderConfig = {}) {
    this.baseUrl = config.baseUrl || "https://api.oilpriceapi.com/v1";
    this.sanityMinPrice = config.sanityMinPrice ?? 10.0;
    this.sanityMaxPrice = config.sanityMaxPrice ?? 250.0;
    this.timeoutMs = config.timeoutMs ?? 8000;
  }

  /**
   * Fetches latest Brent crude price using the provided key and validates all constraints.
   */
  public async fetchLatestBrentPrice(apiKey: string): Promise<ValidatedObservation> {
    const url = `${this.baseUrl}/prices/latest?by_code=BRENT_CRUDE_USD`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);

    let response: Response;
    try {
      response = await fetch(url, {
        method: "GET",
        headers: {
          Authorization: `Token ${apiKey}`,
          Accept: "application/json",
          "User-Agent": "HAWKLYX/1.0",
        },
        signal: controller.signal,
      });
    } catch (err: unknown) {
      clearTimeout(timeout);
      if (err instanceof Error && err.name === "AbortError") {
        throw new ProviderServerError("Upstream provider request timed out");
      }
      throw new ProviderServerError(`Network error contacting upstream provider: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      clearTimeout(timeout);
    }

    if (response.status === 401 || response.status === 403) {
      throw new ProviderAuthError(`Provider rejected credentials (HTTP ${response.status})`);
    }

    if (response.status === 402) {
      throw new ProviderQuotaExhaustedError("Provider returned HTTP 402: Daily quota exhausted");
    }

    if (response.status === 429) {
      const retryHeader = response.headers.get("Retry-After");
      const retryAfter = retryHeader ? parseInt(retryHeader, 10) || 60 : 60;
      throw new ProviderRateLimitedError(`Provider rate limited (HTTP 429)`, retryAfter);
    }

    if (response.status >= 500) {
      throw new ProviderServerError(`Provider error HTTP ${response.status}`);
    }

    let json: OilPriceApiRawResponse;
    try {
      json = (await response.json()) as OilPriceApiRawResponse;
    } catch (e) {
      throw new ProviderValidationError("Failed to parse provider response JSON");
    }

    return this.validateAndTransform(json);
  }

  /**
   * Validates raw JSON payload according to HAWKLYX resilience specification.
   */
  public validateAndTransform(raw: OilPriceApiRawResponse, fetchedAt: string = new Date().toISOString()): ValidatedObservation {
    if (!raw || typeof raw !== "object") {
      throw new ProviderValidationError("Provider response is empty or not an object");
    }

    if (raw.status !== "success") {
      const msg = raw.error || raw.message || "Provider status is not 'success'";
      if (msg.toLowerCase().includes("quota") || msg.toLowerCase().includes("limit")) {
        throw new ProviderQuotaExhaustedError(msg);
      }
      throw new ProviderValidationError(`Provider returned non-success status: ${msg}`);
    }

    const data = raw.data;
    if (!data || typeof data !== "object") {
      throw new ProviderValidationError("Missing 'data' property in provider response");
    }

    // Verify commodity code
    const code = (data.code || "").toUpperCase().trim();
    if (code !== "BRENT_CRUDE_USD") {
      throw new ProviderValidationError(`Invalid commodity code: expected BRENT_CRUDE_USD, received '${data.code}'`);
    }

    // Verify price numeric and sanity bounds
    const price = data.price;
    if (typeof price !== "number" || isNaN(price) || !isFinite(price)) {
      throw new ProviderValidationError(`Invalid price value: ${price}`);
    }

    if (price < this.sanityMinPrice || price > this.sanityMaxPrice) {
      throw new ProviderValidationError(
        `Price ${price} is outside sanity bounds [${this.sanityMinPrice}, ${this.sanityMaxPrice}]`
      );
    }

    // Timestamp verification
    const asOf = data.as_of || data.created_at || fetchedAt;

    // Formatted price string
    const formatted = data.formatted || `$${price.toFixed(2)}`;

    return {
      code: "BRENT_CRUDE_USD",
      name: "Brent Crude Oil",
      price: Math.round(price * 100) / 100,
      currency: (data.currency || "USD").toUpperCase(),
      unit: "USD / barrel",
      formatted,
      as_of: asOf,
      collected_at: data.collected_at || null,
      created_at: data.created_at || null,
      fetched_at: fetchedAt,
      is_stale: Boolean(data.stale),
      is_synthetic: Boolean(data.synthetic),
      source: data.source || "OilPriceAPI",
    };
  }
}
