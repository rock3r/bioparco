---
name: addressing-detekt-violations
description: Fixes the cause of Detekt findings. Use when Detekt reports a violation in this repository.
metadata:
  internal: true
---

# Addressing Detekt Violations

## Fix the cause

Fix the design or code-structure problem that Detekt reports.
Adding `@Suppress`, a baseline, or a weaker rule requires the owner's explicit approval.

## What To Do First

When Detekt reports a violation:

1. Check whether the finding describes a design or code-structure problem.
2. Extract supporting classes or simplify control flow when those changes address the cause.
3. Run the smallest relevant Detekt task again after the fix.

## Changes to avoid

Reject changes that hide a finding without addressing its cause:

- Do not add `@Suppress` without a clear boundary reason and approval.
- Do not move methods solely to reduce a rule's count.
- Do not hide complex functions from Detekt by storing them as lambdas in properties.
- Do not weaken the configuration or generate a baseline without explicit user approval.

## Repository tools and rules

- Use `ktfmt` for formatting. Do not add Detekt formatting plugins or change Detekt configuration to handle formatting.
- Compose modules also run `io.nlopez.compose.rules:detekt`. Address those findings through API or design changes where possible.
- If a rule seems unsuitable, explain the problem and request approval before changing `config/detekt`.
- Read `docs/STATIC-ANALYSIS.md` for repository policy and commands.
