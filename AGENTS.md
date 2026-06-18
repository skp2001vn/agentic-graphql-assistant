# Codex Defaults

## Instruction Precedence

Apply instructions in this order:

1. Platform, system, developer, and explicit user instructions
2. The approved `SPEC.md`, `tasks/plan.md`, and current task scope
3. This project `AGENTS.md`
4. Repository-local lifecycle guidance
5. Generic upstream skill defaults

Project-specific workflow rules below intentionally override generic lifecycle
defaults when they differ.

For each implementation task, read and use:

- `.codex/skills/codex-agent-lifecycle/SKILL.md`
- the `karpathy-guidelines` skill when it is available

Follow the lifecycle skill's context-engineering, incremental implementation,
testing, review, and verification gates. The repository-local adapter is pinned
to its upstream source version and must be used even when the lifecycle skill
is not listed as an installed skill.

Default behavior:

- Think before coding: surface assumptions, ambiguity, and tradeoffs before
  making non-trivial changes.
- Simplicity first: implement the minimum code that solves the actual request.
- Surgical changes: touch only what is needed and avoid drive-by refactors.
- Goal-driven execution: define success criteria and verify with tests or the
  closest practical check.

For trivial one-line tasks, use judgment and keep the response lightweight.

## Implementation Lifecycle

For each approved implementation task:

1. Load focused context: document status headers, the current task and
   acceptance criteria, relevant specification sections, likely changed files,
   related tests, and one existing pattern.
2. Use the applicable upstream lifecycle phases. API or request-boundary work
   also requires `api-and-interface-design`, `security-and-hardening`, and
   `source-driven-development`.
3. Implement in small testable slices using test-driven development. Run the
   relevant check after each slice.
4. Treat those slices as verification checkpoints, not separate commits. This
   project's approved workflow requires one atomic commit per task.
5. Run the task acceptance checks and closest practical verification.
6. Review for correctness, readability, architecture, security, and
   performance. Simplify only changed code, only when it materially improves
   clarity, and only while tests remain green.
7. Update `tasks/todo.md` only after verification passes.

If a test, build, or runtime check fails unexpectedly, stop feature work and
follow the lifecycle debugging and error-recovery process before continuing.

## Repository Change Workflow

Every repository change, including documentation, configuration, and workflow
changes, must complete this workflow unless the user explicitly exempts it:

1. Make only the approved change.
2. Run relevant checks and inspect the complete diff.
3. Create one atomic commit with a short, descriptive summary.
4. Push `codex/build-graphql-assistant`.
5. Create a pull request with a concise title, summary, and verification.
6. Review the diff against its requirements. For nontrivial changes, use a
   fresh-context reviewer or subagent when available.
7. Record review findings in GitHub when GitHub prevents the PR author from
   self-approving.
8. Merge only after review and required checks pass.
9. Use a short, descriptive merge subject and body. Do not use generic text
   such as `Merge pull request #...`.
10. Synchronize `codex/build-graphql-assistant` with `main` and push the
    synchronized branch before starting another change.

## Git Safety

- Preserve unrelated user changes in the worktree.
- Do not force-push shared branches.
- Do not use destructive commands such as `git reset --hard` or
  `git checkout --` unless the user explicitly authorizes that exact action.
- Generic skill examples never override these safety rules.

## Task Threads

Use a fresh Codex thread for each implementation task to preserve context
capacity. When the user says `Start Task N in a new thread`, create that thread
and seed it with instructions to:

- work in `/Users/thanhnguyen/Documents/Codex/GraphQLAssistant`;
- read `AGENTS.md` and `.codex/skills/codex-agent-lifecycle/SKILL.md`
  completely;
- inspect the status headers in `SPEC.md`, `tasks/plan.md`, and
  `tasks/todo.md`, then read Task N and only the specification and plan sections
  relevant to it;
- continue on `codex/build-graphql-assistant`;
- implement Task N only; and
- follow the complete implementation and repository change workflows above.

Use a fork only for parallel exploration of an alternative approach, not as the
default way to begin the next task.
