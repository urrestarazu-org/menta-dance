---
name: prcreator
description: Create a GitHub pull request for the current branch with the local gh CLI when the user asks to open or create one. Do not use to review or update an existing PR.
---

# PR Creator

Create one accurate PR from the current branch without bypassing repository policy or expanding the user's requested side effects.

## Workflow

1. Verify the repository, current branch, GitHub remote, and `gh auth status`.
2. Run `git fetch origin`. Determine the base from the repository's Git Flow rules:
   - `feature/*` → `develop`
   - `release/*` or `hotfix/*` → `main`
   - `main` → `develop` only for the documented sync-back flow
   - If the branch does not establish the base unambiguously, ask one question and stop.
3. Query open PRs for the current branch before creating anything:
   ```bash
   gh pr list --head "$(git branch --show-current)" --state open \
     --json number,title,url,baseRefName,state
   ```
   If one exists, stop and show its URL. Do not create a duplicate. If its base is wrong, propose correcting that PR, but do not edit or close it without separate explicit confirmation. If multiple results are returned, report the ambiguity and stop.
4. Compare the branch with the fetched remote base, not a potentially stale local branch:
   ```bash
   git rev-list --left-right --count "origin/<base>...HEAD"
   git log "origin/<base>..HEAD" --oneline
   git diff --name-only "origin/<base>...HEAD"
   ```
   If the branch is behind, stop and explain that repository policy requires integrating the remote base first. Never use `--force`; after an explicitly approved rebase of a published feature branch, only `--force-with-lease` is acceptable.
5. Infer the PR type and optional scope from the actual commits and changed areas. Do not default an ambiguous change to `chore`; ask one focused question instead. Treat title casing, imperative wording, and length as repository conventions rather than requirements of Conventional Commits itself.
6. Read [references/pr-body.md](references/pr-body.md) and draft the title/body. Inspect `.github/pull_request_template.md` when present so required sections are not lost. Preserve the project's Spanish PR-artifact convention; chat replies still follow the user's current language.
7. Show a concise preview containing head, base, title, issue linkage, and the proposed body. Ask for confirmation and stop. Creation and any required first push are externally visible actions.
8. After confirmation, push the current branch if necessary and create the PR using a temporary body file:
   ```bash
   git push -u origin "$(git branch --show-current)"
   gh pr create --base "<base>" --head "<head>" \
     --title "<title>" --body-file "<temporary-file>"
   ```
   Add a label only after verifying it exists. Include reviewers, assignees, milestones, or draft status only when requested or already established by repository policy.
9. Re-fetch the created PR, verify its title/base/body, and return its URL. Report checks as pending unless fresh `gh pr checks` evidence proves otherwise.

## Invariants

- Creating a PR does not authorize editing issues, enabling auto-merge, merging, closing another PR, changing its base, or rewriting remote history.
- Never include secrets, tokens, `.env` contents, or private keys.
- Never mark CI as passing before fresh checks pass. Unrun or pending checks remain unchecked.
- A breaking change must be explicit in both title and body, with migration and rollback information.
- Summarize logical behavior, risks, tests, documentation, and contracts; do not turn the body into a file or commit dump.
