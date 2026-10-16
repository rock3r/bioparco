---
name: using-git-worktree
description: Creates or reuses an isolated Git workspace. Use before substantial feature work in the main checkout.
metadata:
  internal: true
---

# Using Git Worktrees

## Purpose

Use an isolated branch or worktree to keep implementation changes separate from the main checkout.

## Before Creating Anything

Check the current checkout before creating a worktree:

1. Determine whether the current directory is already a linked worktree.
2. Determine whether the current branch differs from `main`.

If either condition holds, keep working there. Report the existing isolation and skip worktree creation.

## Directory Selection

Choose an existing worktree directory in this order:

1. Use `.worktrees/` if it exists.
2. Otherwise, use `worktrees/` if it exists.
3. If neither exists, create `.worktrees/`.

Before creating a project-local worktree directory, check that Git ignores it.
Add an ignore rule first if needed.

## Creation Flow

1. Determine the current branch and default branch.
2. Choose the base branch.
3. Create a new worktree with a descriptive branch name.
4. Run the project's initial build check inside the worktree.

Example commands:

```bash
git rev-parse --git-common-dir
git rev-parse --git-dir
git rev-parse --abbrev-ref HEAD
git worktree add .worktrees/<branch-name> -b <branch-name> <base-branch>
./gradlew build
```

## Repository paths

- Keep `.plans/` in the root checkout.
- Use absolute paths when switching between Spectre and reference repositories under `~/src`.
- Use worktrees for substantial implementation work. A feature branch is enough for a small documentation change.
