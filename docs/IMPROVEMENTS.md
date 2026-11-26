# Improvements

## Block A — Source configuration moves between instances

### §DU5 Frontend lint has no config

`dumont-react/package.json` declares `"lint": "eslint ."` and depends on ESLint 10 with
`eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`, but the 2026.2 tree has
no `eslint.config.js`. ESLint 9+ reads only the flat config, so `npm run lint` exits 2
with "couldn't find an eslint.config file" before checking a single file. Found while
gating DU1: the frontend gate is `tsc -b` alone.

Dumont 2026.3 has `dumont-react/eslint.config.js`. Port it, run `npm run lint`, and fix
or explicitly disable what it reports in the 2026.2 sources, so the gate is green and
stays one.

### §DU6 Source names unchecked on the server

DU3 made the four console forms that set a source name (AEM source, AEM wizard, DB,
assets) refuse whitespace through `sourceNameHasNoWhitespace` in
`dumont-react/src/lib/source-name.ts`. Nothing on the server checks it: `POST
/api/v2/aem/source`, `/api/v2/db/source`, `/api/v2/assets/source`, the AEM `POST
/import` added by DU2 and the first-start import of `export/*.json` all save a name with
a space, and that source then fails on `/api/v2/connector/index/{name}` and the other
name-keyed URLs.

Build: one check in connector-commons, called on create and update by each source API
and by the import paths. The API answers 400 with a message naming the rule; an import
logs and skips the source rather than failing the whole file. Web-crawler sources are
keyed by id and stay out of scope.

### §DU7 Federation type declarations fail

`npm run compile:build` in `dumont-react` prints `[ Module Federation DTS ] Error:
Failed to generate type declaration (#TYPE-001)` on every run and still exits 0, so the
failure is easy to miss. Reproduced with the command the plugin names (`npx tsc
--project node_modules/.federation/tsconfig.<hash>.json`): the generated tsconfig sets
no `jsx`, so every `.tsx` the exposed module reaches fails with TS6142, and the
design-system and tabler typings add TS2307 and TS2305. Found while gating DU4; the
app's own `tsc -b` is clean.

What it costs: the remote publishes no type declarations, so turing-react, the host that
mounts Dumont, consumes it untyped.

Build: give the `@module-federation/vite` plugin's `dts` option a tsconfig that inherits
the app's (`compilerOptions.jsx`, `skipLibCheck`), or turn `dts` off explicitly when the
host does not consume the types. Then the build prints no DTS error.
