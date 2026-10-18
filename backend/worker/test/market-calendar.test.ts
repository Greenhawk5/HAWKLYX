import { describe, expect, it } from "vitest";
import { getMarketState, isBrentMarketOpen } from "../src/market-calendar";

describe("ICE Brent Crude Market Calendar (Europe/London)", () => {
  it("recognizes normal active trading hours during the week", () => {
    // Wednesday Oct 14, 2026 at 14:00 UTC (15:00 BST)
    const wedTime = new Date("2026-10-14T14:00:00Z").getTime();
    expect(isBrentMarketOpen(wedTime)).toBe(true);

    const state = getMarketState(wedTime);
    expect(state.is_open).toBe(true);
    expect(state.status).toBe("open");
    expect(state.session).toBe("regular");
    expect(state.next_open_at).toBeNull();
  });

  it("identifies Friday market closure at 22:00 London time", () => {
    // In BST (UTC+1):
    // 20:59 UTC = 21:59 BST -> Open
    // 21:00 UTC = 22:00 BST -> Friday 22:00 close!
    const fridayOpen = new Date("2026-10-16T20:59:00Z").getTime();
    expect(isBrentMarketOpen(fridayOpen)).toBe(true);

    const fridayClosed = new Date("2026-10-16T21:00:00Z").getTime();
    expect(isBrentMarketOpen(fridayClosed)).toBe(false);

    const state = getMarketState(fridayClosed);
    expect(state.is_open).toBe(false);
    expect(state.status).toBe("closed");
    expect(state.session).toBe("weekend_closed");
    expect(state.description).toContain("Friday 22:00 London close");
    expect(state.next_open_at).toBeDefined();
  });

  it("identifies Saturday as closed for the entire day", () => {
    const saturdayNoon = new Date("2026-10-17T12:00:00Z").getTime();
    expect(isBrentMarketOpen(saturdayNoon)).toBe(false);

    const state = getMarketState(saturdayNoon);
    expect(state.is_open).toBe(false);
    expect(state.status).toBe("closed");
    expect(state.session).toBe("weekend_closed");
  });

  it("identifies Sunday reopening at 23:00 London time", () => {
    // In BST (UTC+1):
    // 21:59 UTC = 22:59 BST -> Closed
    // 22:00 UTC = 23:00 BST -> Reopening!
    const sundayClosed = new Date("2026-10-18T21:59:00Z").getTime();
    expect(isBrentMarketOpen(sundayClosed)).toBe(false);

    const sundayOpen = new Date("2026-10-18T22:00:00Z").getTime();
    expect(isBrentMarketOpen(sundayOpen)).toBe(true);

    const state = getMarketState(sundayOpen);
    expect(state.is_open).toBe(true);
    expect(state.status).toBe("open");
    expect(state.session).toBe("regular");
  });

  it("identifies daily maintenance break between 22:00 and 23:00 London time (Mon-Thu)", () => {
    // Tuesday in BST (UTC+1):
    // 21:30 UTC = 22:30 BST -> in maintenance break
    const tuesdayBreak = new Date("2026-10-13T21:30:00Z").getTime();
    expect(isBrentMarketOpen(tuesdayBreak)).toBe(false);

    const state = getMarketState(tuesdayBreak);
    expect(state.is_open).toBe(false);
    expect(state.status).toBe("closed");
    expect(state.session).toBe("daily_break");
    expect(state.description).toContain("Daily maintenance and settlement break");

    // After 23:00 London time (22:00 UTC):
    const tuesdayReopen = new Date("2026-10-13T22:01:00Z").getTime();
    expect(isBrentMarketOpen(tuesdayReopen)).toBe(true);
  });

  it("handles London daylight saving transitions (BST to GMT in late October)", () => {
    // In GMT (winter, UTC+0), e.g. Wednesday in January 2026:
    // 14:00 UTC = 14:00 GMT -> open
    const winterWed = new Date("2026-01-14T14:00:00Z").getTime();
    expect(isBrentMarketOpen(winterWed)).toBe(true);

    // In GMT, Friday 22:00 London is 22:00 UTC:
    const winterFridayBeforeClose = new Date("2026-01-16T21:59:00Z").getTime();
    expect(isBrentMarketOpen(winterFridayBeforeClose)).toBe(true);

    const winterFridayAfterClose = new Date("2026-01-16T22:00:00Z").getTime();
    expect(isBrentMarketOpen(winterFridayAfterClose)).toBe(false);
  });

  it("identifies official exchange holidays (New Year's Day, Christmas Day, Good Friday)", () => {
    // New Year's Day Jan 1, 2026 (Thursday)
    const newYearsDay = new Date("2026-01-01T12:00:00Z").getTime();
    expect(isBrentMarketOpen(newYearsDay)).toBe(false);
    expect(getMarketState(newYearsDay).session).toBe("holiday_closed");

    // Christmas Day Dec 25, 2026 (Friday)
    const christmasDay = new Date("2026-12-25T12:00:00Z").getTime();
    expect(isBrentMarketOpen(christmasDay)).toBe(false);
    expect(getMarketState(christmasDay).session).toBe("holiday_closed");

    // Good Friday April 3, 2026
    const goodFriday = new Date("2026-04-03T12:00:00Z").getTime();
    expect(isBrentMarketOpen(goodFriday)).toBe(false);
    expect(getMarketState(goodFriday).session).toBe("holiday_closed");
  });
});
