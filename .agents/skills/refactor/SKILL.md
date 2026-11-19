---
name: refactor
description: "Performs a safe step-by-step refactor with intermediate tests and commits between each step. Use when you want to restructure existing code without changing its observable behavior — extract function/module, rename, deduplicate, change abstraction level, untangle dependencies. Triggers: 'refactor', '/refactor', 'refactor this', 'clean up this code', 'extract this into', 'rename without breaking', 'restructure this'. The skill enforces: tests-first, small atomic steps, green-build-only commits, automatic revert on failure."
---

# Safe Refactor

## Purpose

Restructure code without changing observable behavior, with a safety net at every step. The output is a sequence of small commits, each with passing tests, that together accomplish the refactor goal.

**Core promise:** at no point during this refactor is the code in a broken state on disk for more than one tool call. Every committed state is green.

## Process

### Step 0 — Define scope

1. Ask the user (or infer from `$ARGUMENTS`):
   - **What** code is being refactored (file, function, module)
   - **Why** (the goal: extract, rename, deduplicate, etc.)
   - **What stays the same** (the observable behavior contract)
2. Read the target code. State in 1-2 sentences what you understood.
3. **Confirm with the user before starting.** If they push to "just do it", continue — but the confirmation is the contract.

### Step 1 — Tests must exist first

1. Identify the tests that cover the target code's observable behavior.
2. If tests exist:
   - Run them. They must pass before any refactor step.
   - If they fail: STOP. The pre-existing test failure must be resolved first, separately. Surface this to the user.
3. If tests do NOT exist:
   - **Create them first.** Write characterization tests that lock in current behavior.
   - Run the new tests. They must pass before continuing.
   - Commit: `test: add characterization tests for <target>` before any refactor begins.

### Step 2 — Plan the refactor as steps

Break the refactor into atomic steps, each:
- ≤50 lines of net change
- Self-contained: the codebase compiles and tests pass after each step
- Reversible: if step N fails, reverting step N alone is sufficient

Show the plan to the user before starting. Each step has:
- A short name
- A 1-line description
- The files touched

Example plan:
```
1. Extract `validateInput` from `handleRequest` into new private function (1 file)
2. Move `validateInput` to dedicated `validation.ts` module (2 files)
3. Add tests for `validation.ts` directly (1 new file)
4. Replace remaining inline validation in handlers with calls to `validation.ts` (3 files)
5. Remove now-dead validation helpers from `handleRequest` (1 file)
```

### Step 3 — Execute each step

For each step in order:

1. **State which step you are doing** in 1 line to the user.
2. **Apply the change.** Edit only the files in that step.
3. **Run the tests.**
4. **If tests pass (green):**
   - Commit with message `refactor: <step description>` (use the project's commit convention if `CLAUDE.md` specifies one)
   - Proceed to next step
5. **If tests fail (red):**
   - **Revert the step's changes** (use `git checkout -- <files>` or `git restore`)
   - Surface the failure to the user with: what step failed, the test output, and 1-2 hypotheses about why
   - **Stop the refactor.** Do not continue automatically. Ask the user how to proceed.

### Step 4 — Final verification

After the last step:

1. Run the full test suite (not just the targeted tests).
2. Run linter/type checker if the project uses them.
3. Report:
   - Total steps completed
   - Total commits added
   - Test count before vs after
   - Lines changed (net)
   - Any deviations from the original plan, with reason

## Rules — never break these

- **Never combine a refactor and a behavior change in the same commit.** If you find a bug during refactoring, leave it as a TODO and surface it. Fix the bug as a separate spec'd change after the refactor.
- **Never skip the test run between steps**, even if the change seems "obviously safe".
- **Never `git push` from this skill.** Commits are local. The user pushes when they're satisfied.
- **Never use `--no-verify`** to bypass hooks. If a hook fails, that's the system telling you something is wrong.
- **Never refactor across more than 5 files in a single step.** Split it further.
- **Never change a public API** as part of an internal refactor. That's a breaking change and needs an ADR + version bump.

## When NOT to use this skill

- The change is intended to alter behavior (that's a feature, use `/spec` first)
- The change is too small to need staging (renaming one variable in one file) — just do it
- There are no tests AND it's risky to write characterization tests (legacy code with side effects) — discuss strategy with user first, may need a different approach
- The refactor requires database migrations or other side effects on shared state — needs a full spec and ADR, this skill is for code only

## After the refactor

- Show the user the list of commits made: `git log --oneline -N`
- Suggest running `/review <slug>` if there's a spec to verify against
- If significant decisions were made (e.g., chose pattern X over Y during refactor), suggest creating an ADR with `/adr`
