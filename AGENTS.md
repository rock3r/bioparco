# AGENTS.md

## Project Overview

bioparco is a public collection of cute Compose Desktop experiments. The Italian name is
the joke: specimens gathered like a bioparco, each in its own enclosure.

Sebastiano Poggi (`rock3r`) is the owner. The showcase is a Jewel Desktop app that routes
into the specimens. Each specimen is a top-level Gradle submodule with its own README.

If this file and the checked-in code ever disagree, treat the checked-in code and `docs/`
as the source of truth.

## Source Of Truth Docs

Keep this file focused on operating rules.

| Document | Use it for |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module map and dependency direction |
| [docs/TESTING.md](docs/TESTING.md) | TDD where it fits, unit vs recording tests |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md) | File placement, style, git/build workflow |
| [docs/STATIC-ANALYSIS.md](docs/STATIC-ANALYSIS.md) | Detekt, ktfmt, and the `check` gate |
| [docs/RECORDING.md](docs/RECORDING.md) | Spectre-driven recordings and how to regenerate them |
| [README.md](README.md) | Specimen index and how to run the showcase |

## Non-Negotiables

### TDD Where It Fits

Physics, hit-testing, keyboard maps, and other plain Kotlin behaviour: write the failing
test first, prove it red, then make the minimum production change.

Visual motion and spring feel are the specimen. Do not invent unit tests that freeze
animation constants unless the change is actually about those numbers.

### Worktree Policy

Before writing tracked files from a clean `main` checkout, check whether isolation already
exists. If you are already inside a worktree or already on a non-main branch, keep working
there. Otherwise prefer a worktree so the main checkout stays clean.

Plans belong in `.plans/` at the repo root, which should stay gitignored.

Use the local `using-git-worktree` skill when setting up an isolated workspace.

### Pre-push checklist

- [ ] `./gradlew check` passes (detekt + ktfmt + unit tests).
- [ ] After rebase: verified key changes survived.

`./gradlew :recordings:recordSpecimens` is **not** part of `check`. The README movies
come from the `recordings` CI job on `v*` tags (`xvfb-run`; a push to `main` is a
backup feed). Local regeneration is optional.

**Always run `./gradlew check` before the actual push.**

### Regressions And Scope Discipline

- Keep changes scoped to the requested specimen or house rule.
- Treat unrequested behavioural changes as regressions.
- Do not regress a 60fps path to make a test or a linter quieter.
- If implementation is intentionally phased, say what remains.

### Detekt And ktfmt

- Do not edit `config/detekt/detekt.yml`, add suppressions broadly, or create baselines
  unless the owner explicitly asks. `MagicNumber` is already off: specimens are numbers.
- Treat Compose Rules findings as first-class Detekt findings.
- Use `ktfmt` as the formatting source of truth.
- Prefer fixing the cause of a finding over reshuffling code to game the rule.

## Actions Requiring Explicit User Approval

Do not do these without clear instruction in the current task:

- opening a PR
- merging or closing a PR
- pushing any branch
- destructive git history changes
- committing unrelated changes outside the task’s scope

Working on `main` is allowed only when the owner has said so for that task.

## Working Style

- Minimize interruptions. Read the specimen README before changing its motion.
- Be explicit about directory changes when working across repos such as `~/src/spectre`.
- Prefer repo-local docs and checked-out source over memory for Compose/Jewel/Spectre.
- Before concluding that something is unsupported, check `.agents/skills/`.
- Prose slash style: `a/b`, never `a / b`.

## Build & Run

```bash
./gradlew check
./gradlew :showcase:run
./gradlew :grabby-stepper:run
./gradlew :chat-bubble-transition:run
./gradlew :processing-field:run
./gradlew :recordings:recordSpecimens
```

## Local Skills

- `using-git-worktree`
- `addressing-detekt-violations`
