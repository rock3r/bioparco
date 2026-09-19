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
```

## Recordings

README movies are hosted on `static.sebastiano.dev`. CI records an enclosure only when
its source fingerprint in `recordings/published.json` is stale. Upload with the
`pr-asset-upload` skill (`PR_ASSET_UPLOAD_TOKEN`). See `docs/RECORDING.md`.
