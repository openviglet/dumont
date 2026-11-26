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
   * No inherited-debt list here any more, and its absence is the point. DU5 and DU8 cleared the
   * rules this config arrived turned off in named files, and each list was deleted rather than
   * kept at zero: an empty list is an invitation to add a line to it. If a rule ever needs that
   * treatment again, name the files and say why at each one.
   */
)
