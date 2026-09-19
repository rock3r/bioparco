# Cursor instructions

This file is **Cursor-only**. Do not copy these notes into `AGENTS.md`, `CLAUDE.md`,
or other shared agent docs. Shared operating rules stay in `AGENTS.md`.

## GitHub

Always use the `gh` CLI for GitHub interactions (PRs, issues, comments, reviews, CI,
labels, checks, merges). An authenticated token is already in the environment.

Typical commands:

```bash
gh pr view
gh pr create
gh pr edit
gh pr comment
gh run list
gh run view
gh release list
gh release upload recordings build/recordings/*.mp4 --clobber
```

## Recordings

Tag `vX.Y.Z` to refresh the floating `recordings` GitHub Release. Local
`./gradlew :recordings:recordSpecimens` is optional. See `docs/RECORDING.md`.
