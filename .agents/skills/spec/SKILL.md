---
name: spec
description: "Generates a feature-level technical spec in docs/specs/<slug>.md BEFORE writing any code. Use this when starting work on a non-trivial feature (anything >100 lines, anything that adds an API endpoint, anything that touches the DB schema, anything that affects user-facing behavior). Triggers: 'spec', '/spec', 'create a spec', 'spec out this feature', 'before I code', 'plan this feature'. This is a LIGHTWEIGHT per-feature spec — different from the heavy SRD/FRD produced by the technical-specs skill. Use this for daily flow; use technical-specs for full system specifications at project kickoff."
---

# Feature Spec

## Purpose

Produce a focused technical spec for **one feature** before any code is written. Forces explicit thinking about problem, design, trade-offs, and tests upfront. Reduces re-work and serves as the contract that `/review` will check the implementation against.

## Output

Write to `docs/specs/<slug>.md` where `<slug>` is the feature name in kebab-case (e.g., `auth-flow`, `payment-webhook`, `user-profile-edit`).

If `docs/specs/` does not exist, create it.

## Process

1. **Read context first** (in this order, only as needed):
   - `CLAUDE.md` at project root
   - `.claude/rules/*.md` files relevant to the feature
   - `docs/decisions/ADR-*.md` that constrain this feature
   - Existing related code (use Grep/Glob, not full file reads)

2. **Confirm the slug** with the user if not provided in arguments.

3. **Write the spec** following the format below.

4. **NEVER write code.** This skill produces only the spec. If the user pushes to code immediately, push back: "spec first, code after — that's the contract."

## Format

```markdown
# Spec: <Feature Name>

**Status:** Draft | Approved | Implemented
**Author:** <user>
**Date:** YYYY-MM-DD
**Related ADRs:** ADR-XXX, ADR-YYY (if any)

## Problem

What user/business problem does this solve? Who has the pain? What evidence supports it?
Keep it ≤3 paragraphs. No solution language here.

## Solution (high level)

The chosen approach in ≤2 paragraphs. What we will build, in human terms.

## Technical design

### API contract (if applicable)
- Endpoints, methods, request/response shapes (use Zod-style or TypeScript types)
- Status codes and error shape

### Data model (if applicable)
- Tables, columns, indices, constraints
- Migration strategy if changing existing schema

### UI (if applicable)
- Screens, components, states (loading/empty/error/success)
- Reference Design Document if exists

### Sequence
- Step-by-step flow from user action to final state
- Include error paths

## Trade-offs

What we explicitly chose NOT to do and why. Min 2 trade-offs.

## Implementation steps

Numbered, atomic, ≤8 steps. Each step should be commit-sized.

1. ...
2. ...

## Tests required

### Happy path
- ...

### Edge cases (min 3)
- ...

### Failure modes
- ...

## Risks

What could go wrong in production? What's the blast radius? What's the rollback plan?

## Out of scope

What is explicitly NOT being built in this feature. Anti-scope-creep boundary.
```

## After writing the spec

- Show the user the path to the spec file
- Ask: "Spec written. Review it, then say 'approved' to update status from Draft to Approved before I start implementing."
- Do not start implementation until status is Approved.

## When NOT to use this skill

- Trivial changes (<50 lines, rename, fix typo, dependency bump) — just code it
- Bug fixes where the problem is already understood — go straight to fixing
- Refactors that don't change behavior — use `/refactor` instead
- Major architectural decisions — use `architecture-decisions` skill for full ADR first
