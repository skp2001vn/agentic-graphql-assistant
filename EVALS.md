# GraphQL Assistant Evaluations

The evaluation strategy has two modes: an offline deterministic suite for the
normal build and an explicit live Ollama suite for model-quality and latency
measurement.

## What the evaluations cover

The versioned dataset lives under `src/test/resources/evals/` and covers:

- generation
- troubleshooting with expected issue coverage
- ambiguous or insufficient prompts
- adversarial prompt-injection attempts
- malformed model output
- GraphQL tool selection and bounded tool use
- regressions reported through API usage: natural generation wording, multiline
  troubleshooting, argument-syntax repair, invalid-field correction, and
  realistic variable examples

Both suites assess routing, response-contract compliance, GraphQL parsing,
schema validity, formatting, variables, clarification behavior, and error
mapping. Hard failures such as an invalid response shape, invalid GraphQL, or
invented schema fields always fail a case.

## Deterministic evaluations

Run:

```bash
./mvnw test -Pevals
```

Deterministic evaluations use fake provider transcripts and require neither a
live Ollama service nor an OpenAI account. They are designed to run as part of
`./mvnw verify` and must meet these thresholds:

- 100% valid response contract
- 100% parseable and schema-valid returned operations
- 100% correct intent on the curated dataset
- 100% clarification for curated insufficient prompts

Use this suite for every implementation or prompt change. A deterministic
failure is a release blocker.

The suite writes `target/evals/deterministic-report.json`. The report contains
the stable case ID and category, normalized output, tool calls with complete
inputs and outputs, raw model transcript, latency, hard failures, and aggregate
pass rate.

## Live Ollama evaluations

Prepare the default model:

```bash
ollama pull qwen3:8b
ollama ls
```

Make sure Ollama is reachable at `http://localhost:11434`, then run:

```bash
./mvnw test -Pevals-live
```

The live suite performs real generation and troubleshooting requests with
`qwen3:8b`. It records each case's output, tool trace, latency, pass/fail
reason, and aggregate score in `target/evals/live-ollama-report.json`. It is
intentionally excluded from the offline build because model availability,
model output, and local hardware vary.

For focused debugging, select one live case by ID:

```bash
./mvnw test -Pevals-live -Deval.case=live-generation-list-countries
```

Live readiness targets are:

- at least 90% overall case pass rate
- no individual warm request over 30 seconds for the latency target to be
  marked met

If the latency target is missed, report the measured limitation rather than
hiding or retrying it. The application makes no automatic provider fallback
and no automatic request retries.

## Agent and tool expectations

The router must select `GENERATE`, `TROUBLESHOOT`, or
`CLARIFICATION_REQUIRED`. Only the selected specialist receives tools:

- `inspectSchema`
- `validateOperation`

Tool calls are read-only, inputs are validated, and tool-call rounds are
bounded. Evaluations should flag unnecessary tools, unsafe tool arguments,
invented schema fields, incomplete troubleshooting issue coverage, or a result
that differs from the final deterministic validation outcome.

Model-based judging may supplement deterministic grading for semantic quality,
but it never overrides hard contract or GraphQL-validation failures. The
current harness records a null judge score because all initial cases have
deterministic hard checks; a future judge can fill that field without changing
pass/fail precedence.

## Dataset format

The JSON Lines files under `src/test/resources/evals/` are versioned inputs.
Each case has a stable ID, category, prompt, expected intent, hard expectations,
and, for offline cases, a fake provider transcript. Keep case ordering stable
so reports remain easy to compare across prompt or tool changes.

## Interpreting failures

For each failed case, inspect:

1. expected and actual routing intent
2. provider transcript or raw live response
3. tool names, inputs, outputs, and round count
4. structured response mapping
5. final GraphQL parse, schema validation, and formatting result
6. latency and the explicit failure reason

Add a regression case before correcting a defect discovered by either suite.
