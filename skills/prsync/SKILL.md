---
name: prsync
description: Audit and, after explicit confirmation, update an existing GitHub pull request's title or body to match its current diff and checks. Do not use to create a PR.
---

# PR Sync

Keep an existing PR accurate while preserving intentional author content. This skill never creates, closes, merges, or retargets a PR.

## Workflow

1. Verify the repository, current branch, GitHub remote, and `gh auth status`, then run `git fetch origin` before inspecting or updating the PR.
2. Locate candidates across all states:
   ```bash
   gh pr list --head "$(git branch --show-current)" --state all \
     --json number,title,url,body,baseRefName,headRefName,state
   ```
   Continue only when exactly one open PR is unambiguous. If none is open, report that there is nothing to sync. If multiple open PRs match, show them and stop rather than guessing.
3. Fetch fresh ground truth from GitHub:
   ```bash
   gh pr view <number> --json number,title,body,url,baseRefName,headRefName,headRefOid,commits
   gh pr diff <number> --name-only
   gh pr checks <number>
   ```
   Compare the fetched head against `origin/<base>`. Do not rely on a stale local base. If the remote base or PR head changes during the audit, re-fetch before proposing an update.
4. Read [../prcreator/references/pr-body.md](../prcreator/references/pr-body.md) and inspect `.github/pull_request_template.md` when present. Audit:
   - title type/scope and breaking-change marker;
   - summary of logical behavior and affected modules/contracts;
   - risks, verification commands/results, documentation, and linked issues;
   - checklist claims against fresh evidence, especially CI state;
   - stale, contradicted, or unverifiable statements.

   Do not require one body bullet per commit or path. Fixup, revert, and mechanical commits should be represented by their net logical effect.
5. If the PR is already accurate, say so and stop without editing it. Otherwise, show a focused before/after diff of the proposed title/body changes. Preserve custom sections and wording unless contradicted by evidence. Never silently rebuild a mostly-correct body from a template.
6. Ask for explicit confirmation and stop. After confirmation, write the complete proposed body to a temporary file and update only the fields shown:
   ```bash
   gh pr edit <number> --body-file "<temporary-file>"
   ```
   Include `--title` only if the preview explicitly proposed a title correction.
7. Re-fetch the PR and verify the changed fields exactly. Return the URL and current check state.

## Invariants

- User recollection is context, not evidence for test counts or CI status. Verify a claim from a fresh command/run or remove/qualify it.
- Pending, skipped, and failed checks are not “CI pasa”.
- Updating the description does not authorize changing the base, closing the PR, pushing commits, editing issues, enabling auto-merge, or merging.
- PR artifacts follow the project's Spanish convention; chat replies follow the user's current language.
- Never invent intent that cannot be established from the net diff, commits, issue, or user-provided context.
