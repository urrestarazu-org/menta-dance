---
name: prcreator
description: >
  Create GitHub Pull Requests using the local gh CLI with Conventional Commits v1.0.0
  formatted descriptions, branch-protection awareness, and selectable PR types
  (feat, fix, hotfix, chore, docs, refactor, test, style, perf, build, ci).
---

# PRcreator

## Purpose

Orchestrate the creation of GitHub Pull Requests from the local terminal using the
official `gh` CLI. Enforces [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)
formatting in the PR title and description, respects repository branch-protection
rules, and lets the user select the PR type so the body follows the appropriate
template.

## When to use

Trigger this skill when the user says any of the following (or equivalents in
Spanish / English):
- "create a PR"
- "open a pull request"
- "gh pr create"
- "make a PR for this branch"
- "I need to merge this via PR"
- "crear un PR"
- "hacer pull request"
- Any variation involving creating / submitting a PR using `gh`

## Prerequisites

- `gh` CLI installed and authenticated (`gh auth status` must succeed)
- Working directory inside a Git repository with a GitHub remote
- The current branch has been pushed to the remote (the skill will push it if
  necessary)

---

## Execution Flow

Execute these steps sequentially. Stop and report errors immediately.

```
┌─────────────────────────────────────────────────────────────────┐
│  STEP 1: Environment Check                                      │
├─────────────────────────────────────────────────────────────────┤
│  • gh auth status                                               │
│  • git remote -v                                                │
│  • git branch --show-current                                    │
│  → FAIL: Abort with instructions if auth or remote is missing  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  STEP 2: Branch Analysis                                        │
├─────────────────────────────────────────────────────────────────┤
│  • Detect current branch name                                   │
│  • Infer base branch from Git Flow:                             │
│    - feature/* → develop                                        │
│    - hotfix/*  → main                                           │
│    - release/* → main                                           │
│    - fix/*     → develop                                        │
│  • Extract issue number from branch (e.g., feature/123-desc)    │
│  • Check if branch is pushed; push if needed                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  STEP 3: Commit Analysis                                        │
├─────────────────────────────────────────────────────────────────┤
│  • git log <base>..<head> --oneline                             │
│  • Infer PR type from commits/branch prefix                     │
│  • Detect BREAKING CHANGE or ! in commits                       │
│  • List changed files: git diff --name-only <base>..<head>      │
│  • Auto-detect scope from changed file paths (see Scope Rules)  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  STEP 4: User Confirmation                                      │
├─────────────────────────────────────────────────────────────────┤
│  • Show detected values (type, scope, base, title)              │
│  • Ask for confirmation or adjustments                          │
│  • Confirm linked issue (if detected)                           │
│  → WAIT for user response before proceeding                     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  STEP 5: PR Creation                                            │
├─────────────────────────────────────────────────────────────────┤
│  • Build title: <type>(<scope>): <description>                  │
│  • Build body from type-specific template                       │
│  • Execute: gh pr create --title "..." --body "..." --base ...  │
│  • Capture PR URL from output                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  STEP 6: Post-Creation                                          │
├─────────────────────────────────────────────────────────────────┤
│  • Display PR URL prominently                                   │
│  • Show next steps (review, CI status)                          │
│  • Offer auto-merge option if applicable                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Output Format

Use this visual format when reporting each step to the user.

### Step Indicators

```
✓  Completed successfully
⠋  In progress (spinner)
✗  Failed
⚠  Warning / needs attention
→  Action required from user
```

### Example Output

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 🚀 PRcreator
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✓ Environment Check
  ├─ gh CLI: authenticated as @username
  ├─ Remote: origin → github.com/owner/menta-dance
  └─ Branch: feature/123-user-auth

✓ Branch Analysis
  ├─ Current:  feature/123-user-auth
  ├─ Base:     develop (Git Flow: feature/* → develop)
  ├─ Issue:    #123 (extracted from branch name)
  └─ Pushed:   ✓ up to date with origin

✓ Commit Analysis
  ├─ Commits:  3 commits ahead of develop
  │   • abc1234 feat(auth): add login endpoint
  │   • def5678 feat(auth): add token validation
  │   • ghi9012 test(auth): add login tests
  ├─ Type:     feat (inferred from commits)
  └─ Breaking: No

✓ Scope Analysis
  ├─ Changed files: 5
  │   ├─ api/auth/       → 4 files (80%)
  │   └─ api/auth/test/  → 1 file  (20%)
  ├─ Dominant:  auth (100% in api/auth/**)
  └─ Scope:     auth

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 📋 PR Preview
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Title:  feat(auth): add user authentication system
  Base:   develop
  Head:   feature/123-user-auth
  Labels: enhancement
  Issue:  Closes #123

→ ¿Confirmar estos valores? [Y/n/edit]
```

### After PR Creation

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 ✅ PR Created Successfully
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  🔗 https://github.com/owner/menta-dance/pull/42

  Next steps:
  ├─ ⏳ CI checks running...
  ├─ 👀 Request review from team
  └─ 🔀 Merge when approved

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Error Output

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 ✗ PRcreator Error
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ✗ Environment Check Failed

  Error: gh CLI not authenticated

  Fix: Run the following command:

    gh auth login

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Scope Auto-Detection

Infer the scope from changed file paths using the project's module structure.

### Module Mapping

| Path Pattern              | Scope      | Description                    |
|---------------------------|------------|--------------------------------|
| `api/auth/**`             | `auth`     | Authentication module          |
| `api/billing/**`          | `billing`  | Payments and subscriptions     |
| `api/virtual/**`          | `virtual`  | Online courses                 |
| `api/physical/**`         | `physical` | In-person classes              |
| `api/shared/**`           | `shared`   | Cross-module utilities         |
| `api/app/**`              | `app`      | Application assembly           |
| `bff/**`                  | `bff`      | Backend for Frontend (web)     |
| `android/**`              | `android`  | Mobile application             |
| `docs/**`                 | `docs`     | Documentation                  |
| `.github/**`              | `ci`       | CI/CD configuration            |
| `*.gradle.kts`, `gradle/**` | `build` | Build configuration            |

### Detection Algorithm

```
1. Get changed files: git diff --name-only <base>..<head>

2. Extract module from each path:
   - api/auth/src/... → auth
   - api/billing/domain/... → billing
   - bff/templates/... → bff

3. Count files per module

4. Apply scope rules:
   ┌─────────────────────────────────────────────────────────┐
   │  IF 100% files in ONE module                           │
   │  → scope = that module                                 │
   │                                                        │
   │  IF 80%+ files in ONE module (dominant)                │
   │  → scope = dominant module                             │
   │  → mention other modules in PR body                    │
   │                                                        │
   │  IF multiple modules with no dominant (< 80%)          │
   │  → scope = omit (no scope in title)                    │
   │  → list all affected modules in PR body                │
   │                                                        │
   │  IF only docs/ changed                                 │
   │  → type = docs, scope = omit or topic                  │
   │                                                        │
   │  IF only .github/ or gradle changed                    │
   │  → type = ci or build, scope = omit                    │
   └─────────────────────────────────────────────────────────┘
```

### Example Detection Output

```
✓ Scope Analysis
  ├─ Changed files: 8
  │   ├─ api/auth/   → 6 files (75%)
  │   ├─ api/shared/ → 1 file  (12.5%)
  │   └─ docs/       → 1 file  (12.5%)
  ├─ Dominant:  auth (75% > threshold)
  └─ Scope:     auth
      ⚠ Also touches: shared, docs
```

### Layer Detection (Optional)

For more granular scopes, detect the Clean Architecture layer:

| Path Pattern                        | Layer Suffix |
|-------------------------------------|--------------|
| `api/{module}/domain/**`            | `-domain`    |
| `api/{module}/application/**`       | `-app`       |
| `api/{module}/infrastructure/**`    | `-infra`     |

Examples:
- `feat(auth-domain): add User entity validation`
- `fix(billing-infra): correct JPA mapping for Payment`

**Note**: Layer suffixes are optional. Use them only when the change is isolated to a single layer AND the distinction adds clarity.

---

## Breaking Changes Detection

Scan commits for breaking change indicators per Conventional Commits v1.0.0.

### Detection Patterns

```bash
# Check for BREAKING CHANGE in commit body/footer
git log <base>..<head> --grep="BREAKING CHANGE" --oneline

# Check for ! in commit type (e.g., feat!:, fix!:)
git log <base>..<head> --oneline | grep -E "^[a-f0-9]+ \w+!:"
```

### Indicators

| Pattern                          | Location       | Example                              |
|----------------------------------|----------------|--------------------------------------|
| `BREAKING CHANGE:` or `BREAKING-CHANGE:` | Footer  | `BREAKING CHANGE: remove legacy API` |
| `!` after type                   | Title          | `feat!: new auth flow`               |
| `!` after scope                  | Title          | `feat(api)!: change response format` |

### Output When Detected

```
✓ Commit Analysis
  ├─ Commits:  2 commits ahead of develop
  │   • abc1234 feat(api)!: change user response format
  │   • def5678 docs: update API migration guide
  ├─ Type:     feat
  └─ Breaking: ⚠ YES
      └─ abc1234: "change user response format"

⚠ Breaking Change Detected
  This PR contains breaking changes. The following will be added:

  Title: feat(api)!: change user response format
                  ↑ exclamation mark indicates breaking

  Body section:
  ┌────────────────────────────────────────────────────┐
  │ ## ⚠️ Breaking Changes                             │
  │                                                    │
  │ - **What breaks**: User response format changed    │
  │ - **Migration**: See docs/migration-v2.md          │
  │ - **Affected consumers**: Mobile app, BFF          │
  └────────────────────────────────────────────────────┘
```

### PR Body Template for Breaking Changes

When breaking changes are detected, add this section to the PR body:

```markdown
## ⚠️ Breaking Changes

### What breaks
- [Description of what API/behavior changed]

### Migration path
- [Steps to migrate existing code]

### Affected consumers
- [ ] Mobile app (android/)
- [ ] BFF (bff/)
- [ ] External integrations

### Rollback plan
- [How to revert if needed]
```

---

## PR Types (Conventional Commits v1.0.0)

Before creating the PR, ask the user (or infer from context / branch name / recent
commits) which type applies. Use the exact Conventional Commits prefix in the PR
title and the corresponding body section.

| Type       | Prefix   | Label suggestion | Description focus                             |
|------------|----------|------------------|-----------------------------------------------|
| `feat`     | `feat:`  | `enhancement`    | New feature; motivation, acceptance criteria  |
| `fix`      | `fix:`   | `bug`            | Bug fix; root cause, reproduction, verification |
| `hotfix`   | `hotfix:`| `hotfix`         | Production hotfix; incident link, rollback    |
| `docs`     | `docs:`  | `documentation`  | Documentation only changes                    |
| `style`    | `style:` | `style`          | Formatting, missing semi-colons, etc.; no code change |
| `refactor` | `refactor:`| `refactor`     | Code change that neither fixes a bug nor adds a feature |
| `perf`     | `perf:`  | `performance`    | Performance improvement                         |
| `test`     | `test:`  | `testing`        | Adding or correcting tests                    |
| `build`    | `build:` | `build`          | Build system or external dependency changes     |
| `ci`       | `ci:`    | `ci`             | CI configuration files and scripts              |
| `chore`    | `chore:` | `chore`          | Other changes that don't modify src or test files |

Title format (strict Conventional Commits v1.0.0):
```
<type>[optional scope]: <description>
```

- **Type**: mandatory, one of the prefixes above (without the colon)
- **Scope**: optional, noun describing a section of the codebase (e.g. `auth`, `api`, `billing`)
- **Description**: mandatory, short summary in imperative mood, lowercase, no period at the end
- **Max title length**: 72 characters (ideal ≤ 50)

Examples:
- `feat(auth): add OAuth2 login with state validation`
- `fix(billing): prevent double-charge on retry`
- `docs: update local dev setup instructions`
- `chore(deps): bump spring boot to 3.2.0`

## Procedure

### 1. Validate environment

```bash
gh auth status
git remote -v
```

If authentication fails, abort and instruct the user to run `gh auth login`.

### 2. Determine branch context

- **Current branch**: `git branch --show-current`
- **Base branch**: usually `main` or `develop`. Ask if unsure. Check the repo's
  `.github/pull_request_template.md` or documented conventions if available.
- Ensure the current branch is pushed:
  ```bash
  git push -u origin $(git branch --show-current)
  ```

### 3. Check branch protection (optional but recommended)

```bash
gh api repos/{owner}/{repo}/branches/{base-branch} --jq '.protected'
```

If the base branch is protected, remind the user that:
- CI checks may be required before merge
- Code reviews may be mandatory
- Force-push is likely disabled
- Direct commits to the base branch will be rejected

### 4. Select PR type and draft content

Build the PR metadata based on the selected Conventional Commits type.

#### Body sections

1. **Summary** — one-paragraph overview of the change.
2. **Changes** — bullet list of the files / logic modified.
3. **Type-specific section** — see table below.
4. **Checklist** (use `[x]` to mark items):
   - [ ] Tests added or updated
   - [ ] Documentation updated
   - [ ] No breaking changes (or breaking changes documented)
   - [ ] CI passes
   - [ ] Self-review completed
5. **Linked issues** — `Closes #<issue>` or `Relates to #<issue>` if applicable.

Type-specific body templates:

- **feat**
  - Motivation / user story
  - Acceptance criteria
  - Screenshots, loom link, or demo steps
- **fix**
  - Root cause analysis
  - Reproduction steps (before the fix)
  - Verification steps (after the fix)
- **hotfix**
  - Incident / ticket link
  - Urgency justification
  - Rollback plan
  - Post-merge monitoring checklist
- **docs**
  - What documentation changed and why
  - Preview URL or path
  - Reviewer notes (what to focus on)
- **style**
  - Scope of formatting changes
  - Tool used (e.g. prettier, checkstyle)
  - No functional change confirmation
- **refactor**
  - Scope of the refactor
  - Motivation (tech-debt, performance, readability)
  - Test plan and performance impact (if any)
- **perf**
  - Benchmarks (before / after)
  - Profiling method
  - Risk / regression assessment
- **test**
  - What gap is being covered
  - How to run the new tests
  - Coverage delta or results
- **build**
  - What changed in the build system
  - Dependencies added / removed
  - Build verification steps
- **ci**
  - What workflow or pipeline changed
  - Why the change was needed
  - Test runs or validations
- **chore**
  - Context (dependency bump, file move, etc.)
  - Risk assessment
  - Validation steps

### 5. Open the PR

```bash
gh pr create \
  --title "<type>(<scope>): <description>" \
  --body "<body>" \
  --base <base-branch> \
  --head <current-branch> \
  [--draft] \
  [--label <label1>,<label2>] \
  [--reviewer <username>] \
  [--assignee <username>] \
  [--milestone <title>]
```

Flags to consider:
- `--draft` — if the user wants early feedback before formal review
- `--reviewer @username` — if the reviewer is known
- `--assignee @username` — if assignment is required
- `--milestone <title>` — if the PR belongs to a milestone

If the repository contains `.github/pull_request_template.md`, read it first and
merge its contents with the structured sections above rather than replacing it.

### 6. Post-creation verification

After the PR is created, capture the PR URL and optionally:
- Link related issues:
  ```bash
  gh issue edit <issue-number> --body "Addressed in <pr-url>"
  ```
- Enable auto-merge (if desired and allowed):
  ```bash
  gh pr merge <pr-number> --auto --squash
  ```
- Copy the PR URL to the clipboard for sharing.

## Error handling

| Error | Cause | Fix |
|-------|-------|-----|
| `no commits between <base> and <head>` | Branches are identical | Switch to the correct branch or verify the diff |
| `Authentication error` | `gh` session expired | Run `gh auth login` |
| `could not resolve to a repository` | Missing / wrong remote | Run `gh repo set-default <owner>/<repo>` |
| `branch protection blocks direct push` | Protected base branch | The PR workflow is correct; ensure CI is green |
| `pull request already exists` | Duplicate PR | Show the existing PR URL instead |

## Safety rules

- **NEVER** force-push to a protected branch.
- **NEVER** bypass required reviews unless explicitly requested by a repository admin
  and documented in the PR body.
- **NEVER** include secrets, tokens, `.env` contents, or private keys in the PR
  description or title.
- Always respect the repository's `PULL_REQUEST_TEMPLATE.md` if it exists.
- Prefer `--fill` only when commits are already perfectly formatted; otherwise
  craft the title and body explicitly to guarantee quality.

## Example

**User:** "I need to create a PR for the auth fix"

**Skill execution:**

```bash
gh pr create \
  --title "fix(auth): resolve null pointer on oauth callback" \
  --body "## Summary
Fixes a null pointer exception when the OAuth provider returns an empty state parameter.

## Root Cause
The OAuth2StateFilter assumed state was always non-null after PR #123.

## Changes
- Added null-check in OAuth2StateFilter.java
- Added unit test for empty state scenario

## Verification
- [x] Reproduced locally with provider X
- [x] Unit test passes
- [ ] QA on staging

## Checklist
- [x] Tests added
- [ ] Documentation updated
- [x] No breaking changes
- [x] Self-review completed

Closes #456" \
  --base develop \
  --head fix/auth-null-state \
  --label bug
```

## Notes

- For **stacked / dependent PRs**, mention the dependency in the body:
  `Depends on #<pr-number>`.
- If the user does not know the PR type, default to `chore` and let them adjust
  before submitting.
- Keep the body concise but complete; reviewers should understand the change
  without opening the code diff.
- Refer to [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)
  for any edge cases or specification details.
