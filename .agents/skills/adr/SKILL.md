---
name: adr
description: "Creates a numbered Architecture Decision Record in docs/decisions/ADR-NNN-<slug>.md. Use this DURING the deliberation of any technical decision that is hard to reverse or affects multiple components — not after. Triggers: 'adr', '/adr', 'create an ADR', 'document this decision', 'record this choice', 'we just decided X'. This is the LIGHTWEIGHT in-flow ADR command for daily use — different from the heavy architecture-decisions skill (which produces deep analytical ADRs for project kickoff). Use this when you need to capture a decision in 5 minutes; use architecture-decisions when you need a 1-hour deliberation."
---

# ADR (Architecture Decision Record)

## Purpose

Capture a technical decision *while it is being made*, with its context, alternatives, and consequences. ADRs are the institutional memory of the project's architecture — what a buyer reads first in due diligence.

## Output

Write to `docs/decisions/ADR-NNN-<slug>.md` where:
- `NNN` is the next sequential 3-digit number (e.g., 001, 002, 047)
- `<slug>` is kebab-case (e.g., `database-choice`, `auth-provider`, `monorepo-vs-polyrepo`)

If `docs/decisions/` does not exist, create it.

## Process

1. **Find the next ADR number.**
   - List existing files in `docs/decisions/` matching `ADR-*.md`
   - Take the highest number and increment by 1
   - If none exist, start at 001

2. **Confirm slug and decision summary with the user** if not clear from arguments.

3. **Gather context** (only if not provided):
   - Read `CLAUDE.md` and the Project Brief for high-level context
   - Read related existing ADRs (linked or topically adjacent)
   - Ask the user for: alternatives considered, who participated, current state of the system

4. **Write the ADR** in the format below.

5. **Set status to "Proposed"** by default. Only mark "Accepted" if the user confirms the decision is final.

## Format

```markdown
# ADR-NNN: <Short title in present tense — e.g., "Use Postgres as primary database">

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date:** YYYY-MM-DD
**Deciders:** <names or roles>
**Tags:** <e.g., database, auth, deployment>

## Context

What is the issue that motivated this decision? What forces are at play (technical, political, social, project)? What constraints exist?

Keep it factual. ≤4 paragraphs. State the *problem*, not the solution.

## Decision

What is the change we're making? State it in present tense, declarative: "We will use X." or "We are choosing Y over Z."

≤2 paragraphs.

## Consequences

### Positive
- ...
- ...

### Negative
- ...
- ...

### Neutral / trade-offs
- ...

What becomes easier? What becomes harder? What new constraints appear? What new options open?

## Alternatives considered

For each alternative, briefly state:
- **Option:** what it was
- **Why rejected:** the specific reason (cost, complexity, lock-in, team familiarity, etc.)

Minimum 2 alternatives. If you can't think of 2, you haven't deliberated enough.

## Links

- Related ADRs: ADR-XXX (if applicable)
- Spec: docs/specs/<slug>.md (if applicable)
- External references: papers, RFCs, blog posts that informed the decision
```

## After writing the ADR

- Show the user the path
- If status is "Proposed", remind them to flip to "Accepted" once final
- If this ADR supersedes another, update the older ADR's status to "Superseded by ADR-NNN"
- Update `CLAUDE.md` if the decision affects how Claude should work in this repo

## When NOT to use this skill

- The decision is trivial and easily reversible (variable naming, library version bump within minor) — skip the ADR
- The decision is so large it needs deep analytical work — use the `architecture-decisions` skill instead
- You haven't actually made a decision yet — deliberate first, then write the ADR
- You're documenting something that's already in the code without controversy — that's just code documentation, not an ADR

## Common pitfalls to avoid

- **Writing the ADR after the fact** with no real alternatives section — auditors see this and discount the doc
- **One-line context** that doesn't explain why the decision matters
- **No tags** — ADRs become unfindable in 6 months
- **No date** — readers can't tell if the decision is still current
- **Status stuck on "Proposed"** forever — flip to Accepted or remove the file
