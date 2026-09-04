# PR title and body policy

Read this reference when creating a PR or auditing an existing PR's title/body.

## Title

Use the repository convention:

```text
<type>[optional scope][!]: <Spanish description>
```

Common types are `feat`, `fix`, `hotfix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, and `chore`. Conventional Commits permits types beyond `feat` and `fix`; the allowed list, lowercase description, imperative style, and 72-character target are repository conventions.

Infer scope from the dominant logical module (`auth`, `billing`, `virtual`, `physical`, `shared`, `app`, `bff`, `android`, `docs`, `ci`, or `build`). Omit it when no scope clearly dominates. Use `!` when the change is breaking.

## Required body coverage

Start from `.github/pull_request_template.md` when it exists. The body must preserve its required coverage:

- `## Resumen`: what changes and why.
- `## Tipo de cambio`: mark the evidenced type.
- `## Cambios realizados`: logical behavior and relevant architecture/contracts, not an exhaustive file or commit list.
- `## Cómo probar`: exact commands or reproducible verification. State what actually ran; never invent results.
- `## Checklist`: reflect fresh evidence. Leave CI unchecked while pending or failing.
- `## Breaking changes (si aplica)`: what breaks, migration path, affected consumers, and rollback plan.
- `## Issues relacionados`: use `Closes #<n>` only when merge should close the issue; otherwise use `Relacionado con #<n>`.

Add type-specific information only when it improves review:

| Type | Useful review information |
|---|---|
| `feat` | Motivation and acceptance criteria |
| `fix` | Root cause, reproduction, and post-fix verification |
| `hotfix` | Incident/urgency, rollback, and monitoring |
| `docs` | Documentation paths and reviewer focus |
| `style` | Formatting scope and evidence of no behavior change |
| `refactor` | Motivation, preserved behavior, and regression coverage |
| `perf` | Measurement method and before/after evidence |
| `test` | Coverage gap and commands/results |
| `build` | Build/dependency impact and verification |
| `ci` | Workflow change, reason, and relevant run evidence |
| `chore` | Maintenance context, risk, and validation |

## Language and preservation

PR title/body content is Spanish because the repository template and established artifacts are Spanish. Technical identifiers, type/scope prefixes, labels, branches, commands, and paths remain unchanged. This artifact convention does not control the language of chat replies.

When synchronizing an existing PR, retain accurate custom sections and author wording. Apply the smallest edit that removes stale claims and adds materially missing review context.
