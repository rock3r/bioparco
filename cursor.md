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

Regenerate with `./gradlew :recordings:recordSpecimens` on a machine that can show a
window and grant Screen Recording / portal capture. Host the MP4s on the floating
`recordings` GitHub Release. See `docs/RECORDING.md`.
