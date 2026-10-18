/**
 * HAWKLYX Types & Data Contracts
 * Defines both the upstream OilPriceAPI schema and the downstream HAWKLYX client contract.
 */

export interface CoordinatorClient {
  handlePriceRequest(options?: { force?: boolean }): Promise<{
    body: HawklyxPriceResponse;
    status: number;
  }>;
  getHealth(): Promise<HealthStatusResponse>;
}

export interface Env {
  // Secret slots for multi-account pool (1-5)
  OILPRICEAPI_KEY_1?: string;
  OILPRICEAPI_KEY_2?: string;
  OILPRICEAPI_KEY_3?: string;
  OILPRICEAPI_KEY_4?: string;
  OILPRICEAPI_KEY_5?: string;

  // Cloudflare Durable Object for atomic multi-isolate coordination
  COORDINATOR?: DurableObjectNamespace;

  // Configurable operational policies
  DEFAULT_UPSTREAM_INTERVAL_MINUTES?: string;
  MANUAL_REFRESH_COOLDOWN_SECONDS?: string;
  MAX_DAILY_QUOTA_PER_ACCOUNT?: string;
  GLOBAL_DAILY_BUDGET?: string;
  PRICE_SANITY_MIN?: string;
  PRICE_SANITY_MAX?: string;
}

/**
 * Raw response format from OilPriceAPI
 * https://docs.oilpriceapi.com/api-reference/prices/latest
 */
export interface OilPriceApiRawResponse {
  status: string; // "success" or error
  data?: {
    price: number;
    formatted?: string;
    currency?: string;
    code?: string;
    created_at?: string;
    type?: string;
    as_of?: string;
    collected_at?: string;
    stale?: boolean;
    synthetic?: boolean;
    source?: string;
  };
  error?: string;
  message?: string;
}

/**
 * Validated in-memory observation representation
 */
export interface ValidatedObservation {
  code: string;
  name: string;
  price: number;
  currency: string;
  unit: string;
  formatted: string;
  as_of: string;
  collected_at: string | null;
  created_at: string | null;
  fetched_at: string;
  is_stale: boolean;
  is_synthetic: boolean;
  source: string;
}

export type HawklyxStatus =
  | "fresh"
  | "stale"
  | "refreshing"
  | "provider_unavailable"
  | "quota_exhausted"
  | "no_data"
  | "invalid_provider_response";

/**
 * Public HAWKLYX API Response Contract
 * Consumed by the Android application and widget.
 * NEVER exposes upstream credentials, authorization headers, or private provider internals.
 */
export interface HawklyxPriceResponse {
  status: HawklyxStatus;
  data: ValidatedObservation | null;
  message: string;
  next_refresh_allowed_at: string;
  server_time: string;
}

export interface AccountHealthInfo {
  slot: number;
  configured: boolean;
  active: boolean;
  used_today: number;
  remaining_estimate: number;
  quarantine_reason?: string;
}

export interface HealthStatusResponse {
  status: "ok" | "degraded" | "exhausted";
  service: "HAWKLYX Edge Service";
  version: string;
  configured_accounts: number;
  active_accounts: number;
  total_requests_today: number;
  daily_budget: number;
  has_cached_observation: boolean;
  last_successful_fetch: string | null;
  cache_age_seconds: number | null;
  accounts: AccountHealthInfo[];
}
