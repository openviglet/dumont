/*
 * D42 — the console's test runner.
 *
 * Deliberately a separate config from `vite.config.ts` rather than a `test` block inside it. The
 * build config mounts module federation, the Tailwind plugin and the design system's boot loader;
 * federation rewrites shared imports with async init wrappers and exposes three entry points, none
 * of which a unit test wants and any of which can make a test file fail for a reason that has
 * nothing to do with the surface under test. What a test does need is exactly two things from the
 * build — the `@` alias and the React plugin — so those are what it takes.
 */
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: {
    dedupe: ["react", "react-dom", "react/jsx-runtime", "react/jsx-dev-runtime"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./test/setup.ts"],
    include: ["test/**/*.test.{ts,tsx}"],
    // A page that never resolves its service call would otherwise hang the run rather than fail
    // it, and a suite that hangs is a suite people stop running.
    testTimeout: 10_000,
  },
});
