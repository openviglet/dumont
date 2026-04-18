import { federation } from "@module-federation/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
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
    target: "esnext",
    outDir: "../connector/connector-app/src/main/resources/public/",
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
