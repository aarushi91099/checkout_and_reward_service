# Deterministic Execution Rules

The agent must behave deterministically.

Given the same requirements and repository state, the agent should produce substantially the same implementation plan, testing strategy, and code structure.

The agent must not make arbitrary decisions.

---

## Execution Order

Every task must be executed in the following order:

1. Read requirements.
2. Identify invariants.
3. Identify affected components.
4. Identify failure cases.
5. Write failing test(s).
6. Implement minimal code.
7. Run tests.
8. Refactor.
9. Run tests again.
10. Run linting.
11. Run static analysis.
12. Update documentation if required.

Steps must not be skipped.

---

## Requirement Resolution

When requirements are ambiguous:

1. Search repository documentation.
2. Search existing implementation patterns.
3. Search existing tests.
4. Search architecture decisions.
5. If ambiguity remains:
   - Choose the simplest implementation.
   - Document the assumption.
   - Continue.

Never invent undocumented behavior without recording the assumption.

---

## Deterministic Design Selection

When multiple valid implementations exist, choose using the following priority order:

1. Correctness
2. Data integrity
3. Simplicity
4. Testability
5. Maintainability
6. Performance
7. Developer convenience

This ordering must never change.

---

## Deterministic Refactoring Rules

Refactoring is permitted only when:

- All tests pass.
- No behavior changes.
- Complexity is reduced.
- Duplication is removed.
- Naming is improved.

Refactoring must not introduce new features.

---

## Deterministic Testing Rules

For every feature:

1. Happy path test.
2. Validation test.
3. Failure test.
4. Edge-case test.
5. Concurrency test (if shared mutable state exists).

If a category does not apply, document why.

---

## Deterministic Completion Criteria

A task is complete only when all conditions are true:

- Requirements implemented.
- Tests passing.
- Lint passing.
- Static analysis passing.
- No known failing tests.
- Assumptions documented.
- No TODOs without justification.

Otherwise the task remains incomplete.

---

## Agent Prohibitions

The agent must not:

- Skip tests.
- Skip linting.
- Disable failing tests.
- Suppress warnings without justification.
- Introduce unused code.
- Introduce speculative abstractions.
- Implement features not required by the current task.
- Modify unrelated code.

---

## Default Rule

If uncertain between two approaches:

Choose the simpler approach that preserves correctness and passes all tests.