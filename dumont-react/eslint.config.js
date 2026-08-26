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
      // D49 — generated `.d.ts` for the federated remotes. Linting emitted declarations reports
      // the source's problems a second time, at a location nobody can fix them.
      "mf-types/**",
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
   * The rule is not downgraded globally: anything not listed below is held to it. There was a
   * second list here for `@typescript-eslint/no-explicit-any`; D54 emptied it, so that rule is now
   * an error across the console and the list is gone rather than kept at zero.
   */
  {
    /*
     * `setState` called synchronously in an effect body. The rule is right that it causes
     * cascading renders; D48 could not fix twenty-three surfaces as a side effect of turning a
     * linter on, so it named them instead.
     *
     * D53 cleared sixteen of the twenty-five, none of them by changing behaviour:
     *
     *   - `isNew` and the "blank entity" became values the render computes, not state an effect
     *     corrected afterwards (ten);
     *   - a loader awaited inside an async IIFE instead of called from the effect body, which is
     *     what the rule can actually see is asynchronous — it cannot follow a `useCallback` (three);
     *   - a loading flag that starts right rather than being raised in an `else` branch, a tab
     *     read from the URL instead of mirrored beside it, and a breadcrumb derived from what the
     *     page is showing (three).
     *
     * Three more went the same way afterwards, each by storing an answer against the question it
     * answers rather than correcting it on the way in: the RAG badge keeps the site beside its
     * status, the avatar picker keeps the preview a pick was made against, and the discovery hook
     * stopped branching on a warm cache it could simply await.
     *
     * Two more went the same way: the field-mapping step computes its preset and stores toggles
     * against the preset they were made for, and the monitoring grid stopped reading the clock
     * about data it does not fetch — the page that fetches now stamps the response and passes it
     * down, which also retired a `refreshInterval` prop the grid no longer had a use for.
     *
     * Double-check went the same way, and was the clearest case of all: five states the effect
     * reset on the way in, replaced by two that each carry the source they describe.
     *
     * The three below are structural, and are left deliberately. The monitoring item's history
     * paging would need its result keyed by the query *and* its "jump to the last page when the
     * tab opens" behaviour rethought — a flow with no test covering it, where the risk of breaking
     * paging outweighs a warning. The avatar picker seeds its style and seed from a prop when the
     * dialog opens, which is the remount-on-key case: a decision about the dialog's contract
     * rather than a local edit.
     */
    files: [
      "src/components/dicebear-avatar-picker.tsx",
      "src/pages/integration.instance.monitoring.item.page.tsx",
    ],
    rules: { "react-hooks/set-state-in-effect": "warn" },
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
