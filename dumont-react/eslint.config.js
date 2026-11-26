import js from "@eslint/js"
import reactHooks from "eslint-plugin-react-hooks"
import reactRefresh from "eslint-plugin-react-refresh"
import globals from "globals"
import tseslint from "typescript-eslint"

/*
 * DU5 — the config the installed linters were waiting for (ported from Dumont 2026.3).
 *
 * `eslint`, `typescript-eslint`, `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`
 * are devDependencies, and `npm run lint` exited 2 with "couldn't find an eslint.config.js"
 * before checking a file. ESLint 9+ reads only this flat config.
 *
 * The rules that matter are the ones a type-check cannot see: a hook called conditionally, a
 * dependency array that lies, a module whose exports break fast refresh. `tsc` is happy with all
 * three. Deliberately not type-aware (`recommendedTypeChecked`): stronger rules, a much slower
 * run, and a gate nobody can get green is a gate somebody deletes.
 */
export default tseslint.config(
  {
    // Build output, dependencies, and the connector's static resources the build writes into.
    ignores: [
      "dist/**",
      "node_modules/**",
      "../connector/connector-app/src/main/resources/public/**",
      // Generated `.d.ts` for the federated remote: linting emitted declarations reports the
      // source's problems a second time, at a location nobody can fix them.
      "mf-types/**",
      // The Module Federation plugin's own generated shared-import map.
      ".__mf__temp/**",
    ],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2024,
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      // A dependency array that describes a narrower effect than the one written is a bug,
      // usually a missing `t` that keeps a message in the language it was built in.
      "react-hooks/exhaustive-deps": "error",

      // Fast refresh only works when a module exports components and nothing else.
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],

      // An unused parameter is usually a signature being honoured; the underscore says so.
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrors: "none" },
      ],
    },
  },

  /*
   * DU5 — inherited debt, held as NAMED FILES and not as a ceiling.
   *
   * This config arrived against a console that had never been linted. The small errors were
   * fixed with it; these rules carried the rest. Each is off only in the files listed: a
   * new violation anywhere else fails by name, and clearing a file means deleting its line
   * here, which is a diff somebody reviews. When a list is empty, delete its block rather than
   * keep it at zero. Emptying them is DU8.
   */
  {
    files: [
      "src/app/console/admin-settings/admin.group.page.tsx",
      "src/app/console/admin-settings/admin.role.page.tsx",
      "src/app/console/admin-settings/admin.user.page.tsx",
      "src/components/ai-summary-panel.tsx",
      "src/components/dicebear-avatar-picker.tsx",
      "src/components/integration/sn-site-select.tsx",
      "src/components/integration/wizard/steps/step-field-mapping.tsx",
      "src/hooks/use-connector-provider.ts",
      "src/hooks/use-discovery.ts",
      "src/pages/integration.instance.assets.source.page.tsx",
      "src/pages/integration.instance.db.source.page.tsx",
      "src/pages/integration.instance.double.check.page.tsx",
      "src/pages/integration.instance.indexing.manager.page.tsx",
      "src/pages/integration.instance.indexing.rules.page.tsx",
      "src/pages/integration.instance.monitoring.item.page.tsx",
      "src/pages/integration.instance.plugins.page.tsx",
      "src/pages/integration.instance.source.page.tsx",
      "src/pages/integration.instance.system-info.page.tsx",
      "src/pages/integration.instance.wc.source.page.tsx",
    ],
    rules: { "react-hooks/set-state-in-effect": "off" },
  },
)
