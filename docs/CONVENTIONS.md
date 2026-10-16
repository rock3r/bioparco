# Conventions

## Code style

- Use explicit imports.
- Keep lines around 120 characters.
- Give public composables a `modifier: Modifier = Modifier` parameter.
- Read pointer-driven motion in `graphicsLayer` and `offset` lambdas. Composition must not read these values.
- Mark expensive work with `Tracing.section("name") { … }` from `:tracing`.
  Disabled sections only read a field, so keep them in the code. See [TRACING.md](TRACING.md).
- Write slashes without spaces, as in `a/b`.

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

- Use a worktree or feature branch for substantial changes.
- Push to `main` only with the owner's approval for the current task.
- Keep each commit focused. Write present-tense subjects that explain the reason for the change.
  Omit conventional commit prefixes.

## Verification

- Run `./gradlew check` to run the tests, Detekt, and ktfmt checks that CI requires.
- Run `./gradlew ktfmtFormat` to format the code.
- Run `./gradlew :showcase:run` to inspect specimens in the catalog.
