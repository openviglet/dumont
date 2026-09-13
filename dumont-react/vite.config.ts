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
        /*
         * The bento tree, offered beside the console one (D37). Turing mounts Dumont under its own
         * prefix, and its bento shell is at `/bento` — so the host already supplies the chrome
         * there and needs the *routes* that belong to it. Without this entry the parallel tree
         * would exist only in standalone mode, which is not where anyone uses Dumont.
         *
         * Additive and inert until a host imports it: `./DumontRoutes` keeps serving every route
         * it did, and a host that mounts neither is unaffected.
         */
        "./BentoRoutes": "./src/BentoRoutes.tsx",
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
       * D49 — the type declarations the host consumes, which had never once been generated.
       *
       * Every build printed `#TYPE-001` and then exited 0, so a remote shipped with no types
       * across the federation boundary — the one place a mismatch is most expensive, because the
       * host and the remote are built separately and a prop that stopped matching surfaces at
       * runtime in someone else's product.
       *
       * The cause was the tsconfig it inherited. The generated DTS config extends this project's
       * root `tsconfig.json`, which is a *solution* file: `files: []` plus references, carrying no
       * `jsx`, no `lib` and no `target`. So the declaration build compiled every `.tsx` with JSX
       * disabled and failed on the first one.
       */
      dts: {
        /*
         * `outputDir` is honoured by the DTS plugin but missing from the `DtsRemoteOptions` type
         * this version of `@module-federation/vite` bundles, so the object is widened here rather
         * than the option dropped. Narrowly scoped: everything else in it is type-checked.
         */
        generateTypes: {
          // The real compiler options live here; the root config only points at projects.
          tsConfigPath: "./tsconfig.app.json",
          /*
           * `import.meta.env` is declared in `vite-env.d.ts`, and the generated config lists its
           * files explicitly with an empty `include` — so an ambient declaration nothing imports
           * is never part of the program unless it is named.
           */
          additionalFilesToCompile: ["src/vite-env.d.ts"],
          /*
           * Emit outside the Vite output directory.
           *
           * `build.outDir` is the connector's `public/`, and `emptyOutDir: true` wipes it — after
           * the generator has written into it. So even a *successful* generation left nothing
           * behind, which is the second half of why no release has ever shipped these.
           */
          outputDir: "mf-types",
          /*
           * Fail the build when the generator reports an error. Necessary and *not sufficient*:
           * pointed at the wrong tsconfig it reports nothing, writes the wrong layout and still
           * exits 0 — which is why `scripts/check-mf-types.mjs` checks the artefact instead.
           */
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
    /*
     * D55 — no `manualChunks` here, and it is not an omission.
     *
     * There used to be one splitting `node_modules` into `vendor-ui` and `vendor-lib`. Module
     * Federation discarded it on every build and said so on stdout: it wraps shared imports in
     * async init, and grouping those into one chunk creates circular dependencies that hang the
     * application silently. No `vendor-*` chunk has ever been emitted.
     *
     * So the config was dead and its refusal printed on every run — a standing line a reader
     * learns to skip, next to the ones that matter. Chunking here is Module Federation's to
     * decide; anything written above it is a belief, not a setting.
     */
  },
  resolve: {
    dedupe: ["react", "react-dom", "react/jsx-runtime", "react/jsx-dev-runtime"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
