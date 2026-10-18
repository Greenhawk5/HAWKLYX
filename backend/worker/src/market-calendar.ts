import { MarketStateInfo } from "./types";

/**
 * Authoritative trading calendar for ICE Futures Europe Brent Crude (BRENT_CRUDE_USD).
 *
 * London local trading hours (Europe/London timezone):
 * - Sunday: Opens at 23:00 London (Pre-open 22:00). Trading runs 23:00-24:00.
 * - Monday - Thursday: 00:00-22:00 Regular Trading.
 *                      22:00-23:00 Daily settlement & maintenance break (Closed).
 *                      23:00-24:00 Regular Trading resumes.
 * - Friday: 00:00-22:00 Regular Trading.
 *           22:00 London: Closes for the weekend.
 * - Saturday: Entire day closed.
 * - Sunday: 00:00-23:00 Closed for the weekend.
 *
 * Timezone & Daylight Saving Transitions:
 * Evaluated strictly in `Europe/London` to handle British Summer Time (BST, UTC+1)
 * and Greenwich Mean Time (GMT, UTC+0) transitions dynamically without hardcoded offsets.
 */

export interface LondonDateTimeParts {
  year: number;
  month: number;
  day: number;
  weekday: number; // 0 = Sun, 1 = Mon, ..., 6 = Sat
  hour: number;
  minute: number;
  second: number;
}

/**
 * Known full-day market holidays for ICE Futures Europe Brent Crude.
 * Format: YYYY-MM-DD
 */
const KNOWN_HOLIDAYS = new Set<string>([
  // 2024
  "2024-01-01", // New Year's Day
  "2024-03-29", // Good Friday
  "2024-12-25", // Christmas Day
  // 2025
  "2025-01-01", // New Year's Day
  "2025-04-18", // Good Friday
  "2025-12-25", // Christmas Day
  // 2026
  "2026-01-01", // New Year's Day
  "2026-04-03", // Good Friday
  "2026-12-25", // Christmas Day
  // 2027
  "2027-01-01", // New Year's Day
  "2027-03-26", // Good Friday
  "2027-12-25", // Christmas Day
]);

/**
 * Extracts London local calendar parts for an arbitrary epoch millisecond timestamp.
 */
export function getLondonDateTimeParts(nowMs: number = Date.now()): LondonDateTimeParts {
  const date = new Date(nowMs);
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "numeric",
    day: "numeric",
    weekday: "short",
    hour: "numeric",
    minute: "numeric",
    second: "numeric",
    hour12: false,
  });

  const parts = formatter.formatToParts(date).reduce<Record<string, string>>((acc, p) => {
    acc[p.type] = p.value;
    return acc;
  }, {});

  const weekdayMap: Record<string, number> = {
    Sun: 0,
    Mon: 1,
    Tue: 2,
    Wed: 3,
    Thu: 4,
    Fri: 5,
    Sat: 6,
  };

  const rawHour = parseInt(parts.hour, 10);
  const hour = rawHour === 24 ? 0 : rawHour;

  return {
    year: parseInt(parts.year, 10),
    month: parseInt(parts.month, 10),
    day: parseInt(parts.day, 10),
    weekday: weekdayMap[parts.weekday] ?? 0,
    hour,
    minute: parseInt(parts.minute, 10),
    second: parseInt(parts.second, 10),
  };
}

/**
 * Computes the London offset (in milliseconds) from UTC for a target date.
 * Returns +3600000 during BST (UTC+1) and 0 during GMT (UTC+0).
 */
export function getLondonOffsetMs(date: Date): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((acc, p) => {
      acc[p.type] = p.value;
      return acc;
    }, {});

  const rawHour = parseInt(parts.hour, 10);
  const hour = rawHour === 24 ? 0 : rawHour;

  const londonAsUtc = Date.UTC(
    parseInt(parts.year, 10),
    parseInt(parts.month, 10) - 1,
    parseInt(parts.day, 10),
    hour,
    parseInt(parts.minute, 10),
    parseInt(parts.second, 10)
  );

  return londonAsUtc - date.getTime();
}

/**
 * Converts a target London calendar date and time (year, month, day, hour, minute)
 * to an exact UTC ISO-8601 string.
 */
export function londonTimeToUtcIso(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number = 0
): string {
  const approxUtc = new Date(Date.UTC(year, month - 1, day, hour, minute, 0));
  const offsetMs = getLondonOffsetMs(approxUtc);
  const exactDate = new Date(approxUtc.getTime() - offsetMs);
  return exactDate.toISOString();
}

/**
 * Evaluates the authoritative market state for Brent crude at any given point in time.
 */
export function getMarketState(nowMs: number = Date.now()): MarketStateInfo {
  const london = getLondonDateTimeParts(nowMs);
  const dateKey = `${london.year}-${String(london.month).padStart(2, "0")}-${String(london.day).padStart(2, "0")}`;

  // 1. Check Full-Day Market Holidays
  if (KNOWN_HOLIDAYS.has(dateKey)) {
    // Reopen next regular morning or Sunday
    let daysToAdd = 1;
    if (london.weekday === 5) daysToAdd = 2; // Friday holiday -> Sunday reopen
    const reopenDate = new Date(nowMs + daysToAdd * 86400000);
    const reopenParts = getLondonDateTimeParts(reopenDate.getTime());
    const targetHour = reopenParts.weekday === 0 ? 23 : 0;
    const nextOpenIso = londonTimeToUtcIso(reopenParts.year, reopenParts.month, reopenParts.day, targetHour, 0);

    return {
      is_open: false,
      status: "closed",
      session: "holiday_closed",
      next_open_at: nextOpenIso,
      description: "Market closed for exchange holiday (ICE Futures Europe).",
    };
  }

  // 2. Saturday: Entire day closed
  if (london.weekday === 6) {
    const daysUntilSunday = 1;
    const sundayDate = new Date(nowMs + daysUntilSunday * 86400000);
    const sundayParts = getLondonDateTimeParts(sundayDate.getTime());
    const nextOpenIso = londonTimeToUtcIso(sundayParts.year, sundayParts.month, sundayParts.day, 23, 0);

    return {
      is_open: false,
      status: "closed",
      session: "weekend_closed",
      next_open_at: nextOpenIso,
      description: "Market closed for weekend. Trading resumes Sunday 23:00 London time.",
    };
  }

  // 3. Sunday: Closed until 23:00 London time
  if (london.weekday === 0) {
    if (london.hour < 23) {
      const nextOpenIso = londonTimeToUtcIso(london.year, london.month, london.day, 23, 0);
      return {
        is_open: false,
        status: "closed",
        session: "weekend_closed",
        next_open_at: nextOpenIso,
        description: "Market closed for weekend. Trading resumes Sunday 23:00 London time.",
      };
    }
    // Sunday 23:00 - 24:00 is regular opening session
    return {
      is_open: true,
      status: "open",
      session: "regular",
      next_open_at: null,
      description: "Regular trading session active (Sunday weekly open).",
    };
  }

  // 4. Friday: Closes at 22:00 London time for weekend
  if (london.weekday === 5) {
    if (london.hour >= 22) {
      const daysUntilSunday = 2;
      const sundayDate = new Date(nowMs + daysUntilSunday * 86400000);
      const sundayParts = getLondonDateTimeParts(sundayDate.getTime());
      const nextOpenIso = londonTimeToUtcIso(sundayParts.year, sundayParts.month, sundayParts.day, 23, 0);

      return {
        is_open: false,
        status: "closed",
        session: "weekend_closed",
        next_open_at: nextOpenIso,
        description: "Market closed for weekend (Friday 22:00 London close). Trading resumes Sunday 23:00.",
      };
    }
    return {
      is_open: true,
      status: "open",
      session: "regular",
      next_open_at: null,
      description: "Regular trading session active (Friday pre-weekend).",
    };
  }

  // 5. Monday - Thursday:
  // - 22:00 - 23:00: Daily maintenance & settlement break (Closed)
  // - All other hours: Open
  if (london.hour === 22) {
    const nextOpenIso = londonTimeToUtcIso(london.year, london.month, london.day, 23, 0);
    return {
      is_open: false,
      status: "closed",
      session: "daily_break",
      next_open_at: nextOpenIso,
      description: "Daily maintenance and settlement break (22:00-23:00 London). Resumes at 23:00.",
    };
  }

  return {
    is_open: true,
    status: "open",
    session: "regular",
    next_open_at: null,
    description: "Regular trading session active.",
  };
}

export function isBrentMarketOpen(nowMs: number = Date.now()): boolean {
  return getMarketState(nowMs).is_open;
}
