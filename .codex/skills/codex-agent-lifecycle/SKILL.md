---
name: codex-agent-lifecycle
description: Repository-local adapter for the production-grade Codex development lifecycle used by GraphQL Assistant.
---

# Codex Agent Lifecycle

This versioned adapter is the lifecycle foundation for this repository. It is
derived from `https://github.com/addyosmani/agent-skills` version `0.6.2`,
commit `d187883b7d761265309cdcc0f202cc76b4b3fb06`. A local checkout may exist at
`/Users/thanhnguyen/Documents/Codex/agent-skills`.

The adapter remains authoritative for this project when the external source
pack is unavailable or has advanced to a different version. When the exact
pinned source pack is available, read the relevant upstream `SKILL.md` before
acting. Do not silently inherit changes from a newer upstream version. See
`references/source-map.md`.

## Instruction Precedence

Apply instructions in this order:

1. Platform, system, developer, and explicit user instructions
2. The approved `SPEC.md`, `tasks/plan.md`, and current task scope
3. `AGENTS.md`
4. This adapter
5. Generic upstream skill defaults

Project-specific rules intentionally override generic lifecycle examples for
branching, commit cadence, review recording, and Git safety.

## Lifecycle Gates

Do not move past a rejected or unresolved gate:

1. Intent confirmed
2. `SPEC.md` explicitly approved
3. `tasks/plan.md` explicitly approved
4. Current task implemented and verified
5. Review complete
6. Pull request merged and implementation branch synchronized

Only clear language such as `approve`, `approved`, `yes`, or `go` counts as
approval. If document status conflicts with conversation state, surface the
conflict before implementation.

## Task Context Gate

Before implementing each task, load only:

- `AGENTS.md` and this adapter
- status headers from `SPEC.md`, `tasks/plan.md`, and `tasks/todo.md`
- the current task, its acceptance criteria, and verification commands
- specification sections relevant to that task
- files likely to change and related tests
- one existing project pattern to follow
- focused failure output when debugging

Do not read the entire specification or plan by default when focused sections
are sufficient. If sources conflict, stop and ask for direction.

## Implementation Phases

1. Read upstream `skills/using-agent-skills/SKILL.md`.
2. Use `skills/context-engineering/SKILL.md` before each task.
3. Use `skills/source-driven-development/SKILL.md` when framework or library
   behavior matters; prefer current official documentation.
4. For API and request-boundary work, also use
   `skills/api-and-interface-design/SKILL.md` and
   `skills/security-and-hardening/SKILL.md`.
5. Build with `skills/incremental-implementation/SKILL.md` and
   `skills/test-driven-development/SKILL.md`.
6. On unexpected failures, stop feature work and use
   `skills/debugging-and-error-recovery/SKILL.md`.
7. Review with `skills/code-review-and-quality/SKILL.md`.
8. Simplify only after tests pass, only within changed code, and only when the
   result is materially clearer.
9. Use `skills/documentation-and-adrs/SKILL.md` for durable decisions.
10. Use `skills/shipping-and-launch/SKILL.md` for final release readiness or
    production deployment, not for every task merge.

## Project Workflow Overrides

- Implement in small, tested slices, but use those slices as local checkpoints.
  Create one atomic commit for the completed approved task.
- Continue on `codex/build-graphql-assistant`. After every pull request merge,
  fast-forward it to `main` and push it so the branch does not diverge.
- Every repository change must be pushed, reviewed through a pull request,
  merged into `main`, and synchronized back to the implementation branch.
- Use a fresh-context reviewer or subagent for nontrivial changes when
  available. If GitHub blocks self-approval, record the review findings in the
  pull request discussion.
- Never inherit destructive Git examples from a generic skill. Preserve user
  changes and require explicit user authorization before destructive actions.

## Stop and Ask

Stop before:

- product behavior not covered by the approved specification
- authentication or authorization decisions
- secrets or credentials
- payments or billing
- destructive data changes or migrations
- production deployment
- new paid external services
- major dependencies

## Done Means

A task is not done until:

- acceptance tests and relevant checks pass
- build, formatting, and static checks pass where applicable
- manual or runtime verification is complete when needed
- the task checklist is updated after verification
- review has no unresolved blocking findings
- the pull request is merged
- `codex/build-graphql-assistant`, local `HEAD`, and `origin/main` are
  synchronized
- remaining risks are summarized
