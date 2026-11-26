# Improvements

## Block A — Source configuration moves between instances

### §DU8 Hooks lint debt in named files

`dumont-react/eslint.config.js` (ported from Dumont 2026.3) makes `npm run lint` a
working gate: it exits 0 with warnings only. The inherited debt of a console that had
never been linted is held as rules turned off in named files; `no-explicit-any`,
`purity` and `refs` are already cleared and their lists deleted.

`react-hooks/exhaustive-deps` is cleared too. Left: `react-hooks/set-state-in-effect`
(19 files), mostly a fetch-then-set effect or state derived from props. Fix a file,
delete its line from the list, and delete the block once it is empty. Dumont 2026.3 D53
cleared the same rule and is the reference for each pattern.
