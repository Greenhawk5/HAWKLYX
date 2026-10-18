import { describe, expect, it } from "vitest";
import {
  OilPriceProvider,
  ProviderQuotaExhaustedError,
  ProviderValidationError,
} from "../src/provider";
import { OilPriceApiRawResponse } from "../src/types";

describe("OilPriceProvider validation", () => {
  const provider = new OilPriceProvider({
    sanityMinPrice: 10.0,
    sanityMaxPrice: 250.0,
  });

  it("validates and parses a valid Brent Crude response", () => {
    const raw: OilPriceApiRawResponse = {
      status: "success",
      data: {
        price: 82.45,
        formatted: "$82.45",
        currency: "USD",
        code: "BRENT_CRUDE_USD",
        created_at: "2026-10-18T10:00:00Z",
        as_of: "2026-10-18T09:55:00Z",
        collected_at: "2026-10-18T10:00:05Z",
        stale: false,
        synthetic: false,
        source: "OilPriceAPI",
      },
    };

    const validated = provider.validateAndTransform(raw);
    expect(validated.code).toBe("BRENT_CRUDE_USD");
    expect(validated.price).toBe(82.45);
    expect(validated.formatted).toBe("$82.45");
    expect(validated.currency).toBe("USD");
    expect(validated.as_of).toBe("2026-10-18T09:55:00Z");
    expect(validated.is_stale).toBe(false);
    expect(validated.is_synthetic).toBe(false);
  });

  it("preserves stale and synthetic flags accurately", () => {
    const raw: OilPriceApiRawResponse = {
      status: "success",
      data: {
        price: 79.1,
        code: "BRENT_CRUDE_USD",
        stale: true,
        synthetic: true,
      },
    };

    const validated = provider.validateAndTransform(raw);
    expect(validated.is_stale).toBe(true);
    expect(validated.is_synthetic).toBe(true);
  });

  it("rejects non-Brent commodity codes", () => {
    const raw: OilPriceApiRawResponse = {
      status: "success",
      data: {
        price: 75.0,
        code: "WTI_USD",
      },
    };

    expect(() => provider.validateAndTransform(raw)).toThrow(ProviderValidationError);
  });

  it("rejects non-numeric and out-of-bounds prices", () => {
    const invalidPrices = [-5, 0, 5, 300, NaN, Infinity];

    invalidPrices.forEach((p) => {
      const raw: OilPriceApiRawResponse = {
        status: "success",
        data: {
          price: p as number,
          code: "BRENT_CRUDE_USD",
        },
      };
      expect(() => provider.validateAndTransform(raw)).toThrow(ProviderValidationError);
    });
  });

  it("detects quota error message in non-success status", () => {
    const raw: OilPriceApiRawResponse = {
      status: "error",
      message: "Daily quota limit reached for this account",
    };

    expect(() => provider.validateAndTransform(raw)).toThrow(ProviderQuotaExhaustedError);
  });
});
