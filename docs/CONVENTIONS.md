# Conventions

## Code style

- no wildcard imports
- keep lines around 120 characters
- public composables take `modifier: Modifier = Modifier`
- do not read pointer-driven motion in composition; read it in `graphicsLayer` / `offset`
  lambdas
- prose slash style is `a/b`, never `a / b`

## File placement

| What | Where |
|---|---|
| A new specimen | top-level folder, Gradle submodule, own README |
| Showcase catalog / routing | `showcase/` |
| Spectre recording tests | `recordings/` |
| Agent operating rules | `AGENTS.md` |
| Cursor-only notes | `cursor.md` + `.cursor/rules/` |
| Local plans | `.plans/` (gitignored) |
| Local worktrees | `.worktrees/` (gitignored) |

## Git

- Prefer a worktree or a feature branch for non-trivial work.
- Push to `main` only when the owner has asked for that in the current task.
- Keep commits scoped. Present-tense subjects, explain the why.

## Verification

- `./gradlew check` is the CI-shaped gate: tests + Detekt + ktfmt.
- `./gradlew ktfmtFormat` is the formatting pass.
- `./gradlew :showcase:run` is the manual catalog.
