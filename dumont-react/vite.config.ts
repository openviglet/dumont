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
