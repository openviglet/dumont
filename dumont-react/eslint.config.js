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
   * No inherited-debt list here any more, and its absence is the point.
   *
   * This config arrived with 105 problems in a console that had never been linted, and two rules
   * carried the bulk of them. Both were held as **named files** rather than a ceiling: a ceiling
   * lets one change pay for another, while a named list means a new violation fails by name and
   * clearing a file is a diff somebody reviews.
   *
   * D54 emptied the `@typescript-eslint/no-explicit-any` list; D53 emptied
   * `react-hooks/set-state-in-effect`, twenty-five violations across twenty-three files. Both
   * rules are now errors across the console, and each list was deleted rather than kept at zero —
   * an empty list is an invitation to add a line to it.
   *
   * If a rule ever needs that treatment again, name the files and say why at each one.
   */

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
