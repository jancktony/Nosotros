---
name: review
description: "Reviews the current git diff against an existing feature spec and writes a structured review report to docs/reviews/<slug>-<date>.md. Use after implementing a feature that has a spec in docs/specs/, before opening a PR or merging. Triggers: 'review', '/review', 'review this feature', 'review the diff', 'code review', 'check my implementation', 'review against spec'. This is per-feature code review (not architecture review). Read-only — never modifies code, only produces the report."
---

# Code Review

## Purpose

Verify that the current implementation matches its spec, follows project conventions, and is safe to merge. Output is a structured review report that the user can act on.

## Output

Write to `docs/reviews/<slug>-<YYYY-MM-DD>.md` where `<slug>` matches the spec file name.

If `docs/reviews/` does not exist, create it.

## Process

1. **Identify the feature.** Use `$ARGUMENTS` as the slug. If not provided, ask the user or infer from the most recent spec in `docs/specs/`.

2. **Gather inputs** (in this order):
   - `git diff` (uncommitted) AND `git diff main...HEAD` (committed on branch) — whichever shows the implementation
   - `git status` to see what's staged vs unstaged
   - `docs/specs/<slug>.md` — the contract to verify against
   - `CLAUDE.md` and relevant `.claude/rules/*.md`
   - Related ADRs in `docs/decisions/`
   - Run the test suite if cheap (<60s); skip if expensive and report the gap

3. **Write the review** following the format below.

4. **NEVER modify code.** This skill is read-only. If you find issues that need fixing, list them. The user (or a separate `/refactor` or coding session) applies the fixes.

## Format

```markdown
# Review: <Feature Name>

**Date:** YYYY-MM-DD
**Reviewer:** Claude (automated review)
**Spec:** docs/specs/<slug>.md
**Branch:** <git branch name>
**Files changed:** N files, +X / -Y lines

## Summary

≤3 sentences. Overall verdict: ready to merge / needs fixes / blocked. State the strongest 1-2 findings.

## Critical issues (block merge)

Issues that MUST be fixed before merging. Each item includes:
- **File:line** — anchor
- **Issue:** what is wrong
- **Why critical:** security risk, correctness bug, spec violation, contract break
- **Suggested fix:** concrete direction (not full code)

If none: write "None found."

## Medium issues (debt — fix soon)

Issues that should be fixed but are not blocking. Same format as critical.

## Minor suggestions

Style, naming, micro-optimizations, readability. Same format.

## Spec coverage

- [ ] Every step in the spec's "Implementation steps" is reflected in the diff
- [ ] Every test listed in spec's "Tests required" exists and passes
- [ ] Out-of-scope items in the spec are NOT in the diff (anti-scope-creep)

Call out any gaps.

## Tests

- Tests added: N
- Tests passing locally: yes / no / not run
- Coverage of edge cases from spec: list which ones are covered, which are missing
- Missing test cases (with concrete suggestions)

## Security & data flow checks

- [ ] No secrets in diff (regex check: API keys, tokens, passwords)
- [ ] Input validation on all user-facing entry points
- [ ] Auth/authz checks on all protected endpoints
- [ ] No SQL injection / XSS / SSRF vectors introduced
- [ ] Error handling does not leak stack traces or internal state to user
- [ ] PII handling matches `docs/compliance/data-flow-diagram.md` if exists

## Convention checks

- [ ] Follows `.claude/rules/code-style.md`
- [ ] Follows `.claude/rules/api-design.md` (if API changed)
- [ ] Follows `.claude/rules/error-handling.md` (Result pattern, no throws in business logic)
- [ ] Follows `.claude/rules/testing.md` (test naming, location, coverage)
- [ ] No new dependencies added without ADR (check package.json/requirements.txt diff)

## Verdict

One of:
- **APPROVE** — ready to merge as-is
- **APPROVE with minor fixes** — merge after addressing minor suggestions
- **REQUEST CHANGES** — must fix critical/medium before merge
- **BLOCK** — fundamental problem (wrong design, spec violation, security hole)
```

## When NOT to use this skill

- No spec exists for the feature — point user to `/spec` first
- Architecture-level review (multi-feature, structural) — use `tech-audit` skill
- Security-focused review of a specific concern — use `cybersecurity` skill
- Performance-focused review — use `performance-audit` skill
