---
name: babysit-pr
description: >
  Use when asked to watch, monitor, or babysit an open bioparco pull request: poll CI and reviews until it is ready to
  merge, fix branch-caused failures, retry flaky runs, and stop when a human decision is needed.
---

# PR babysitter

This skill watches a PR until one of three things happens:

- the PR is merged or closed;
- CI is green, no review threads are unresolved, and there are no conflicts;
- something needs the owner.

The watcher script does the polling. You diagnose, fix, and triage.

It needs the `gh` CLI, installed and authenticated (`gh auth status`). Give the PR as a number or a URL, or use
`--pr auto` to infer it from the current branch. Start by telling the owner which PR you are tracking, with its link.

## Merge gates

| Gate | Required | Notes |
|---|---|---|
| `check` job (CI workflow) | Yes | `main` requires it through branch protection. |
| `recordings` job | No | It runs only on pushes to `main` and on `v*` tags, so it is skipped on every PR. The watcher ignores that skip. PRs never need new Spectre recordings. |
| Codex review | Yes | It is cleared when every Codex finding is dispositioned. |
| Local `./gradlew check` | Yes | Run it before every push. |

Because `check` is a required status, GitHub auto-merge works: `gh pr merge <n> --squash --auto` merges as soon as
the gates pass. Still let the watcher confirm that the Codex review is clear.

## The watcher

Run these from the repository root:

```bash
# Block until something needs attention, then return one snapshot (the default mode)
python3 .agents/skills/babysit-pr/scripts/gh_pr_watch.py --pr auto --once

# Take an instant snapshot, without waiting
python3 .agents/skills/babysit-pr/scripts/gh_pr_watch.py --pr auto --snapshot

# Stream snapshots as JSON lines until a terminal action
python3 .agents/skills/babysit-pr/scripts/gh_pr_watch.py --pr auto --watch

# Rerun the failed jobs of retry-eligible workflows for the current head SHA
python3 .agents/skills/babysit-pr/scripts/gh_pr_watch.py --pr auto --retry-failed-now
```

`--once` polls inside the script every 30 seconds. It returns only when `actions` contains something other than a
passive wait. Use it when the harness returns tool output only after the command exits, which is the usual case.
`--watch` streams `{"event":"snapshot",...}` objects as the state changes and ends with `{"event":"stop",...}`. Use it
only when the harness can read streamed output while the command runs. `--max-session-minutes` (default 90) limits
both modes.

The output is JSON lines. In `--once`, `--snapshot`, and `--retry-failed-now`, the top-level object has `actions`. In
`--watch`, read `payload.snapshot.actions` on `snapshot` events and `payload.actions` on `stop` events. Other useful
fields are `checks` (pending, failed, passed and skipping counts, plus `all_terminal`), `failed_runs` (with
`retry_eligible`), `codex_gate`, `hung_checks`, `new_review_items`, `blocking_review_items`, and `retry_state`.

`blocking_review_items` lists unresolved inline comments that need action. While it is not empty, the watcher never
emits `stop_ready_to_merge`.

## Actions

| `actions` value | Meaning | Ends `--watch` |
|---|---|---|
| `idle` | CI is running and there is nothing to do. | no |
| `wait_codex` | Codex is reviewing (its 👀 reaction is on the PR). Do not push or merge. | no |
| `process_review_comment` | There are new or unresolved review items from trusted humans or Codex. | no |
| `diagnose_ci_failure` | A check failed. Classify it before acting. | no |
| `diagnose_merge_conflict` | The PR is `CONFLICTING` or `DIRTY`. Resolve that before waiting on checks. | yes |
| `diagnose_hung_check` | A check has run for more than 30 minutes. | yes |
| `diagnose_skipping_checks` | A check that should run was skipped or neutral. Find out why. | yes |
| `stop_non_retryable_failure` | The failure is in a workflow the watcher does not rerun (for example CI). Fix it first. | yes |
| `stop_exhausted_retries` | Flaky reruns used the budget for this SHA (3). The owner must investigate. | yes |
| `stop_ready_to_merge` | CI is green, no review blocks it, and there are no conflicts. | yes |
| `stop_pr_closed` | The PR is merged or closed. | yes |
| `stop_session_timeout` | `--max-session-minutes` has passed. Report and stop. | yes |

The watcher only reruns workflows whose names match `RETRY_ELIGIBLE_WORKFLOW_KEYWORDS`. Failures in the `check` job
are almost always lint, static analysis, or code problems, so diagnose them first. `references/heuristics.md` lists
how to tell a branch-caused failure from a flaky one, and when to stop and ask. `references/github-api-notes.md`
documents the `gh` calls and JSON fields the script uses.

The script also understands an optional extra review gate that bioparco does not use. It only activates when a PR
carries the `pr-af` label, so it stays inactive here.

## Codex

Codex has no CI check. `chatgpt-codex-connector[bot]` adds a 👀 reaction to the PR while it reviews and removes it
when it is done. The watcher reads the reactions into `codex_gate.reviewing` and emits `wait_codex`.

- Reaction gone, no comments: Codex is satisfied.
- Reaction gone, comments posted: triage them like any other review finding.

Codex also keeps a "Codex Review Summary" status table as a PR comment and edits it on every review. It is not a
finding, so the watcher ignores it.

Trusted humans are authors with the `OWNER`, `MEMBER`, or `COLLABORATOR` association.

## Push discipline

Every push starts a new Codex review. Push once per fix cycle, when all of these hold:

1. Every known issue is fixed locally: failed CI logs, Codex findings, and human comments.
2. Codex is not in the middle of a review, so its comments arrive in the same batch.
3. `./gradlew check` is green.

Start fixing branch-caused failures as soon as you have diagnosed them. Only the push waits. If the PR is
`CONFLICTING` or `DIRTY`, rebase onto `origin/main`, resolve the conflicts, fold in any outstanding review fixes, run
`check`, and push once.

After the push, resolve every bot thread on GitHub. If nothing changed for a comment, reply with the reason first. No
bot thread may be open at merge time.

## Triage every finding before fixing it

A true finding is not automatically a fix for this PR. Classify it on two axes, and say which box it landed in when you
reply:

| | Blocker | Improvement |
|---|---|---|
| In scope | Fix now. | Fix now if trivial, otherwise file an issue. |
| Out of scope | File an issue, and say so on the PR. | File an issue. |

A finding is in scope when it is about the behaviour this PR set out to change. Ask: would this defect exist on `main`
without my change? If it would, it is pre-existing and belongs in an issue, however real it is.

A blocker means shipping would cause visible harm, data loss, or a security hole. Stricter validation, hardening an
adjacent path, and "this could also be wrong if" are improvements, however confidently a bot reports them.

Stop the review loop and ask the owner when any of these hold:

- Three rounds in a row produce no in-scope blocker.
- Twice in a row, a finding is about code that only exists because of an earlier finding in this PR.
- The diff has grown well beyond the task. Check with `git diff --stat` against the base, not from memory.
- Every push produces new findings, so the "no outstanding findings" gate can never be met.

## Post-merge cleanup

When the PR is merged (`stop_pr_closed`), clean up local state. Skip any step whose branch or worktree does not exist.

1. If you are on the PR branch, switch to `main`.
2. Delete the local branch only when nothing would be lost. Squash merges leave the branch looking unmerged, so
   `git branch -d` refuses and `git branch -D` is needed. Force-deleting a branch is a destructive history change, so
   first prove that the local tip is exactly the head that was merged:

   ```bash
   merged_head=$(gh pr view <n> --json headRefOid --jq .headRefOid)
   test "$(git rev-parse <head_branch>)" = "$merged_head" && git branch -D <head_branch>
   ```

   If the local tip differs, it has commits that were never merged. Keep the branch, and tell the owner.
3. If `git worktree list` shows the branch in a worktree, run `git worktree remove <path>` from the main checkout,
   never from inside the worktree you are removing. `git worktree remove` refuses when the worktree has uncommitted
   changes. Do not force it; tell the owner instead.
4. Run `git pull --ff-only` on `main`.
