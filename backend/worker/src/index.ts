import { HawklyxCoordinator } from "./coordinator";
import { CoordinatorClient, Env } from "./types";

export { HawklyxCoordinator };

function jsonResponse(data: unknown, status: number = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Cache-Control": "no-store, no-cache, must-revalidate",
      ...extraHeaders,
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const pathname = url.pathname;

    try {
      // 1. Handle CORS Preflight
      if (request.method === "OPTIONS") {
        return new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type",
            "Access-Control-Max-Age": "86400",
          },
        });
      }

      // 2. Restrict HTTP methods to GET (and OPTIONS above)
      if (request.method !== "GET") {
        return jsonResponse(
          {
            error: "Method Not Allowed",
            message: `HTTP method ${request.method} is not permitted.`,
          },
          405,
          { Allow: "GET, OPTIONS" }
        );
      }

      // 3. Request URL abuse protection
      if (request.url.length > 2048) {
        return jsonResponse(
          {
            error: "URI Too Long",
            message: "Request URL exceeds the maximum permitted length of 2048 characters.",
          },
          414
        );
      }

      // 4. Verify route mapping before coordinator invocation
      const isPriceRoute = pathname === "/api/v1/price/latest" || pathname === "/" || pathname === "/price";
      const isHealthRoute = pathname === "/health";

      if (!isPriceRoute && !isHealthRoute) {
        return jsonResponse(
          {
            error: "Not Found",
            message: `Route ${pathname} not found on HAWKLYX Edge Service`,
          },
          404
        );
      }

      // 5. Enforce Durable Object coordinator requirement
      // Fail closed with a safe 503 if COORDINATOR binding is missing; NEVER fall back to in-memory state
      if (!env.COORDINATOR) {
        const nowIso = new Date().toISOString();
        if (isHealthRoute) {
          return jsonResponse(
            {
              status: "exhausted",
              service: "HAWKLYX Edge Service",
              version: "1.0.0",
              configured_accounts: 0,
              active_accounts: 0,
              total_requests_today: 0,
              daily_budget: parseInt(env.GLOBAL_DAILY_BUDGET || "200", 10),
              has_cached_observation: false,
              last_successful_fetch: null,
              cache_age_seconds: null,
              accounts: [],
              message: "Coordination service binding is unavailable.",
            },
            503
          );
        }

        return jsonResponse(
          {
            status: "no_data",
            data: null,
            message: "Coordination service is unavailable. Please try again later.",
            next_refresh_allowed_at: nowIso,
            server_time: nowIso,
          },
          503
        );
      }

      const coordinator = env.COORDINATOR.getByName("global") as unknown as CoordinatorClient;

      // 6. Route: /health
      if (isHealthRoute) {
        const health = await coordinator.getHealth();
        return jsonResponse(health, 200);
      }

      // 7. Route: Price endpoints
      const forceParam = url.searchParams.get("force");
      const isForced = forceParam === "true" || forceParam === "1";

      const result = await coordinator.handlePriceRequest({ force: isForced });
      return jsonResponse(result.body, result.status);
    } catch {
      // Top-level defense: fail closed safely without leaking internal paths or secrets
      const nowIso = new Date().toISOString();
      if (pathname === "/health") {
        return jsonResponse(
          {
            status: "exhausted",
            service: "HAWKLYX Edge Service",
            version: "1.0.0",
            configured_accounts: 0,
            active_accounts: 0,
            total_requests_today: 0,
            daily_budget: parseInt(env.GLOBAL_DAILY_BUDGET || "200", 10),
            has_cached_observation: false,
            last_successful_fetch: null,
            cache_age_seconds: null,
            accounts: [],
            message: "Coordination service is temporarily unavailable.",
          },
          503
        );
      }

      return jsonResponse(
        {
          status: "no_data",
          data: null,
          message: "Coordination service is temporarily unavailable. Please try again later.",
          next_refresh_allowed_at: nowIso,
          server_time: nowIso,
        },
        503
      );
    }
  },
};
