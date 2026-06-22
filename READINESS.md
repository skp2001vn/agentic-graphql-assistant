# GraphQL Assistant Readiness Report

Status: GO for the approved local release
Date: 2026-06-19
Verification baseline: `0afb31b461b68a830f88589f718da270a746c881`

## Recommendation

GraphQL Assistant is ready for its approved local, stateless API use case. The
offline release gate, packaging, deterministic evaluations, live Ollama
evaluations, application startup, health endpoint, OpenAPI publication, Swagger
UI, generation, and troubleshooting smoke tests passed.

This recommendation does not extend to exposing the service on an untrusted
network. Authentication, rate limiting, TLS termination, and production network
hardening are explicitly outside the approved scope.

## Verified environment

- Java: OpenJDK 21.0.9 LTS
- Maven Wrapper: Apache Maven 3.9.12
- Operating system: macOS 26.5.1 on `aarch64`
- Default provider: Ollama at `http://localhost:11434`
- Default model: `qwen3:8b`, available locally
- GitHub repository: `skp2001vn/agentic-graphql-assistant`
- GitHub access: authenticated, reachable, and verified with `ADMIN` permission
- Starting branch state: local `HEAD`, `main`,
  `origin/codex/build-graphql-assistant`, and `origin/main` all at
  `0afb31b461b68a830f88589f718da270a746c881`

## Automated verification

| Command | Result |
| --- | --- |
| `./mvnw verify` | PASS in 7.676s; 97 tests, 0 failures, 0 errors, 0 skipped; JAR packaging, Spotless, and Checkstyle passed |
| `./mvnw clean package` | PASS in 7.071s; clean compilation, 97 tests, and executable JAR packaging passed |
| `./mvnw test -Pevals` | PASS in 1.598s; deterministic report passed 7 of 7 cases |
| `./mvnw test -Pevals-live` | PASS in 54.686s; live report passed 4 of 4 cases and met the latency target |

The deterministic suite achieved a 100% pass rate across generation,
variables, single-issue troubleshooting, multiple-issue troubleshooting,
clarification, prompt injection, and malformed model output. All hard response
contract, intent, GraphQL validity, schema validity, and clarification
thresholds passed.

## Live Ollama evaluation

The live suite used the default local `qwen3:8b` model with temperature `0`,
thinking disabled, no retries, and a 60-second request timeout.

- Warmup latency: 5.116s
- Overall result: 4 of 4 cases passed (100%)
- Warm latency target: met; every evaluated request completed under 30 seconds

| Case | Result | Latency |
| --- | --- | ---: |
| Generate country codes and names | PASS | 10.196s |
| Generate continents | PASS | 8.535s |
| Troubleshoot one unknown field | PASS | 15.066s |
| Troubleshoot multiple unknown fields | PASS | 13.804s |

All live cases returned the expected intent and contract, produced parseable
schema-valid GraphQL, and had no hard failures.

## Manual runtime verification

`./mvnw spring-boot:run` started successfully on `127.0.0.1:8080` with the
default Ollama configuration. Startup completed in 1.252s during the recorded
verification run.

| Check | Result |
| --- | --- |
| `GET /health` | PASS; HTTP 200 with `{"status":"UP"}` |
| `GET /v3/api-docs` | PASS; HTTP 200, title `GraphQL Assistant API`, version `1.0.0`, assistant and health paths present |
| `GET /swagger-ui.html` | PASS; redirected to the Swagger UI and rendered HTTP 200 HTML |
| Generation smoke | PASS in 7.501s; returned a named query selecting country `code` and `name` |
| Troubleshooting smoke | PASS in 12.862s; reported the invalid `title` field and returned a schema-valid correction using `name` |

Both measured warm smoke requests completed under the 30-second target.

## Known limitations and risks

- Model output is nondeterministic. The live measurements describe this local
  model and hardware at the verification time, not a universal latency or
  quality guarantee.
- The pinned LangChain4j agentic implementation throws once a model consumes
  all four configured tool-calling rounds, even if the next model response is a
  final answer. A deliberately difficult troubleshooting prompt reproduced a
  safe structured `502 AGENT_EXECUTION_ERROR` after four tool calls. The
  required troubleshooting smoke and all live evaluation cases completed in
  three or fewer tool-calling rounds. The hard bound remains unchanged rather
  than weakening loop protection. LangChain4j documents the API limit as the
  number of model responses containing tool calls:
  <https://docs.langchain4j.dev/apidocs/dev/langchain4j/service/AiServices.html#maxToolCallingRoundTrips(int)>.
- Full-content logging is disabled by default. Enabling it with
  `ASSISTANT_LOGGING_FULL_CONTENT_ENABLED=true` can retain schemas, prompts,
  operations, variables, AI responses, and tool data.
- OpenAI configuration is covered by automated tests but was not live-tested
  because the approved final verification targets the default Ollama provider.
- No production deployment, authentication, authorization, persistence,
  GraphQL execution, TLS, or public-network hardening was performed because
  those capabilities are outside the approved specification.

## Rollback

This release changes no database or persistent state. If the final task
documentation must be rolled back, revert its merge commit and verify
`/health`. For application behavior, the previously verified baseline is
`0afb31b461b68a830f88589f718da270a746c881`.
