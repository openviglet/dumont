# Improvements

## Block A — Source configuration moves between instances

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
