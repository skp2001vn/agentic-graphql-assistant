# Codex Defaults

For coding, code review, debugging, refactoring, or new project creation, use
the `karpathy-guidelines` skill when it is available.

Default behavior:

- Think before coding: surface assumptions, ambiguity, and tradeoffs before
  making non-trivial changes.
- Simplicity first: implement the minimum code that solves the actual request.
- Surgical changes: touch only what is needed and avoid drive-by refactors.
- Goal-driven execution: define success criteria and verify with tests or the
  closest practical check.

For trivial one-line tasks, use judgment and keep the response lightweight.

## Task Workflow

For each approved implementation task:

1. Implement only that task's approved scope.
2. Run its acceptance checks and the closest practical verification.
3. Update `tasks/todo.md` only after verification passes.
4. Create one atomic commit with a short, descriptive summary of the work.
5. Push `codex/build-graphql-assistant`.
6. Create a pull request with a concise title and summary.
7. Review the task diff against its acceptance criteria. Record a review
   comment when GitHub prevents the PR author from self-approving.
8. Merge the pull request after the review and required checks pass.
9. Use a short, descriptive merge subject and body. Do not use generic text
   such as `Merge pull request #...`.
10. Synchronize `codex/build-graphql-assistant` with `main` before starting the
    next task.

## Task Threads

Use a fresh Codex thread for each implementation task to preserve context
capacity. When the user says `Start Task N in a new thread`, create that thread
and seed it with instructions to:

- work in `/Users/thanhnguyen/Documents/Codex/GraphQLAssistant`;
- read `AGENTS.md`, `SPEC.md`, `tasks/plan.md`, and `tasks/todo.md` first;
- continue on `codex/build-graphql-assistant`;
- implement Task N only; and
- follow the complete task workflow above.

Use a fork only for parallel exploration of an alternative approach, not as the
default way to begin the next task.
