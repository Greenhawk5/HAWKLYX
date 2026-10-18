import { defineConfig } from "vitest/config";
import path from "node:path";

export default defineConfig({
  resolve: {
    alias: {
      "cloudflare:workers": path.resolve(import.meta.dirname, "./test/shims/cloudflare-workers.ts"),
    },
  },
  test: {
    environment: "node",
  },
});
