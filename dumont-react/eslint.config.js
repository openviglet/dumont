import js from "@eslint/js"
import reactHooks from "eslint-plugin-react-hooks"
import reactRefresh from "eslint-plugin-react-refresh"
import globals from "globals"
import tseslint from "typescript-eslint"

/*
 * D48 — the config four installed linters were waiting for.
 *
 * `eslint`, `typescript-eslint`, `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`
 * have been devDependencies here all along, and `pnpm run lint` exited non-zero with "couldn't
 * find an eslint.config.js" — a message that reads like a broken command rather than an absent
 * gate, which is why nobody chased it.
 *
 * The rules that matter are the ones a type-check cannot see: a hook called conditionally, a
 * dependency array that lies, a module whose exports break fast refresh. `tsc` is happy with all
 * three.
 *
 * Deliberately *not* type-aware (`recommendedTypeChecked`). That would give stronger rules and a
 * much slower run, and this config has to be adoptable in one commit against a codebase that has
 * never been linted — a gate nobody can get green is a gate somebody deletes.
 */
export default tseslint.config(
  {
    // Build output, dependencies, and the connector's static resources the build writes into.
    ignores: [
      "dist/**",
      "node_modules/**",
      "../connector/connector-app/src/main/resources/public/**",
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

      /*
       * Fast refresh only works when a module exports components and nothing else. `allowConstantExport`
       * covers the `const X = ...` a few files pair with their component.
       */
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],

      /*
       * An unused parameter is usually a signature being honoured, not a mistake — the underscore
       * prefix is how a reader says so.
       */
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrors: "none" },
      ],
    },
  },

  /*
   * The debt this config inherited, named file by file rather than capped.
   *
   * A ceiling — "no more than 25 of these" — lets one change pay for another: fix a page, and the
   * budget quietly funds a new violation somewhere else. Naming the files means a **new** one
   * fails by name, and clearing a file is a diff that also deletes its line here. The same reason
   * `console-census.test.ts` names every fork instead of counting them.
   *
   * Neither rule is downgraded globally: anything not listed below is held to it.
   */
  {
    /*
     * D48 — `setState` called synchronously in an effect body (K: cleared by its own task).
     *
     * The pattern is this console's standard data load: `useEffect(() => { service.get().then(setX);
     * setIsNew(false) }, [id])`. The rule is right that it causes cascading renders, and fixing it
     * means restructuring how ~23 surfaces fetch — which is a refactor with its own risk, not a
     * side effect of turning a linter on for the first time.
     */
    files: [
      "src/app/console/admin-settings/admin.group.page.tsx",
      "src/app/console/admin-settings/admin.role.page.tsx",
      "src/app/console/admin-settings/admin.user.page.tsx",
      "src/components/ai-summary-panel.tsx",
      "src/components/dicebear-avatar-picker.tsx",
      "src/components/integration/aem.monitoring.grid.tsx",
      "src/components/integration/sn-site-select.tsx",
      "src/components/integration/wizard/steps/step-field-mapping.tsx",
      "src/components/rag-status-badge.tsx",
      "src/hooks/use-discovery.ts",
      "src/pages/integration.instance.assets.source.page.tsx",
      "src/pages/integration.instance.catalog.source.page.tsx",
      "src/pages/integration.instance.coverage.page.tsx",
      "src/pages/integration.instance.db.source.page.tsx",
      "src/pages/integration.instance.double.check.page.tsx",
      "src/pages/integration.instance.eds.source.page.tsx",
      "src/pages/integration.instance.indexing.manager.page.tsx",
      "src/pages/integration.instance.indexing.rules.page.tsx",
      "src/pages/integration.instance.monitoring.item.page.tsx",
      "src/pages/integration.instance.plugins.page.tsx",
      "src/pages/integration.instance.source.page.tsx",
      "src/pages/integration.instance.system-info.page.tsx",
      "src/pages/integration.instance.wc.source.page.tsx",
    ],
    rules: { "react-hooks/set-state-in-effect": "warn" },
  },

  {
    /*
     * D48 — `any` in six files, mostly service payloads and the grid's column plumbing. Typing
     * them is real work on real contracts, and it is not this task.
     */
    files: [
      "src/components/grid.list.tsx",
      "src/components/integration/dynamic.source.locale.tsx",
      "src/components/integration/integration.indexing.manager.form.tsx",
      "src/components/integration/wizard/aem-source-wizard.tsx",
      "src/hooks/use-grid-adapter.ts",
      "src/services/integration/integration-connector.service.ts",
    ],
    rules: { "@typescript-eslint/no-explicit-any": "warn" },
  },

  {
    // Node scripts: the census reader and anything beside it.
    files: ["scripts/**/*.mjs"],
    languageOptions: {
      globals: globals.node,
    },
  },

  {
    // Tests run under vitest's globals and mount jsdom.
    files: ["test/**/*.{ts,tsx}"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
  },
)
