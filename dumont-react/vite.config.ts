import { federation } from "@module-federation/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { vigletBootLoader } from "@viglet/viglet-design-system/vite";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";

// Redirect CJS use-sync-external-store imports to ESM shims.
// React 19 has useSyncExternalStore built-in, but the CJS shim
// does require('react') which breaks Rolldown's browser CJS interop
// under Module Federation (top-level await in MF virtual modules).
function syncExternalStoreShimPlugin(): Plugin {
  const shimDir = path.resolve(__dirname, "./src/shims");
  return {
    name: "use-sync-external-store-shim",
    enforce: "pre",
    resolveId(source) {
      if (source === "use-sync-external-store/shim/with-selector") {
        return path.join(shimDir, "use-sync-external-store-with-selector.js");
      }
      if (source === "use-sync-external-store/shim") {
        return path.join(shimDir, "use-sync-external-store-shim.js");
      }
      if (
        source === "use-sync-external-store/with-selector" ||
        source === "use-sync-external-store/with-selector.js"
      ) {
        return path.join(shimDir, "use-sync-external-store-with-selector.js");
      }
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    syncExternalStoreShimPlugin(),
    vigletBootLoader({
      title: "Viglet Dumont",
      subtitle: "Content Connector",
      color: "#006400",
      colorDark: "#10b981",
      prefix: "dumont",
      storageKey: "dumont-ui-theme",
      testFlag: "__DUMONT_LOADING_TEST__",
    }),
    react(),
    tailwindcss(),
    federation({
      name: "dumont_react",
      filename: "remoteEntry.js",
      exposes: {
        "./register": "./src/i18n/register.ts",
        "./manifest": "./src/manifest.ts",
        "./DumontRoutes": "./src/DumontRoutes.tsx",
      },
      shared: {
        react: { singleton: true, requiredVersion: "*" },
        "react-dom": { singleton: true, requiredVersion: "*" },
        "react-router-dom": { singleton: true, requiredVersion: "*" },
        "react-i18next": { singleton: true, requiredVersion: "*" },
        "react-hook-form": { singleton: true, requiredVersion: "*" },
        i18next: { singleton: true, requiredVersion: "*" },
        axios: { singleton: true, requiredVersion: "*" },
        sonner: { singleton: true, requiredVersion: "*" },
        "next-themes": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/router": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/i18n": { singleton: true, requiredVersion: "*" },
        "@viglet/viglet-design-system/floating-formulas-bg": { singleton: true, requiredVersion: "*" },
      },
      /*
       * DU7 — the type declarations the host consumes (ported from Dumont 2026.3 D49).
       *
       * Every build printed `#TYPE-001` and exited 0, so the remote shipped no types across the
       * federation boundary. The generated DTS config extended the root `tsconfig.json`, a
       * solution file (`files: []` plus references) with no `jsx`, so every `.tsx` failed.
       */
      dts: {
        // `outputDir` is honoured by the DTS plugin but missing from the bundled options type,
        // so the object is widened here rather than the option dropped.
        generateTypes: {
          // The real compiler options live here; the root config only points at projects.
          tsConfigPath: "./tsconfig.app.json",
          // `import.meta.env` is declared in `vite-env.d.ts`, which nothing imports, and the
          // generated config lists its files explicitly.
          additionalFilesToCompile: ["src/vite-env.d.ts"],
          // Outside `build.outDir`: that is the connector's `public/`, which `emptyOutDir` wipes
          // after the generator has written into it.
          outputDir: "mf-types",
          // Fail the build when the generator reports an error, instead of printing and exiting 0.
          abortOnError: true,
        } as Record<string, unknown>,
      },
    }),
  ],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:30130",
        changeOrigin: true,
      },
    },
  },
  base: "/",
  build: {
    emptyOutDir: true,
    chunkSizeWarningLimit: 1000,
    outDir: "../connector/connector-app/src/main/resources/public/",
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            if (id.includes("@radix-ui") || id.includes("radix-ui")) return "vendor-ui";
            return "vendor-lib";
          }
        },
      },
    },
  },
  resolve: {
    dedupe: ["react", "react-dom", "react/jsx-runtime", "react/jsx-dev-runtime"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
