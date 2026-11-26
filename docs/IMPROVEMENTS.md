# Improvements

## Block A — Source configuration moves between instances

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

### §DU8 Hooks lint debt in named files

`dumont-react/eslint.config.js` (ported from Dumont 2026.3) makes `npm run lint` a
working gate: it exits 0 with warnings only. The inherited debt of a console that had
never been linted is held as rules turned off in named files; `no-explicit-any`,
`purity` and `refs` are already cleared and their lists deleted.

Left: two lists, one rule per commit. `react-hooks/set-state-in-effect` (19 files) is
mostly a fetch-then-set effect or state derived from props;
`react-hooks/exhaustive-deps` (15 files) is mostly a missing `t`, or a service built
from `id` that has to move into a `useMemo` before an honest array stops refetching
forever. Fix a file, delete its line from the list, and delete the block once it is
empty rather than keep it at zero. Dumont 2026.3 D53 and D56 cleared the same two rules
and are the reference for each pattern.
