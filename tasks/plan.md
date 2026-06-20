# GraphQL AI Assistant Implementation Plan

Status: Approved
Specification: [`SPEC.md`](../SPEC.md)
Date: 2026-06-18

## Planning Principles

- Implement only the approved scope.
- Work in vertical slices that leave the project verifiable.
- Write or update tests before behavior-changing implementation.
- Keep provider, schema, and HTTP boundaries small and explicit.
- Treat AI output as untrusted.
- Use bounded specialist agents and read-only tools only where they improve
  correctness or observability.
- Treat eval results as implementation feedback, not end-of-project decoration.
- Do not require a live Ollama or OpenAI service for automated tests.
- Run explicit live smoke tests and evals against the user's running Ollama.
- Keep implementation commits integrated with
  `https://github.com/skp2001vn/graphql-assistant2`.
- Within each task, use incremental test and verification checkpoints without
  creating intermediate commits.
- After every task, run its targeted checks, update `tasks/todo.md`, create one
  atomic task commit, push `codex/build-graphql-assistant`, open and review a
  pull request, merge it into `main`, and synchronize the implementation branch
  with `main`.
- Run `./mvnw verify` before declaring the project ready.

## Task 1 — Scaffold the Java 21 Application and Health Endpoint

Size: Medium
Dependencies: Approved `SPEC.md`

### Outcome

Create a minimal Spring Boot 4.1.0/Maven project that compiles on Java 21 and
exposes the local health endpoint.

### Likely files

- `pom.xml`
- `.mvn/wrapper/*`
- `mvnw`
- `mvnw.cmd`
- `.gitignore`
- `src/main/java/com/example/graphqlassistant/GraphqlAssistantApplication.java`
- `src/main/java/com/example/graphqlassistant/api/HealthController.java`
- `src/main/java/com/example/graphqlassistant/api/HealthResponse.java`
- `src/main/resources/application.yml`
- `src/test/java/com/example/graphqlassistant/api/HealthControllerTest.java`

### Acceptance criteria

- Maven coordinates and Java version match the approved specification.
- Spring Boot, LangChain4j, GraphQL Java, and springdoc versions are pinned as
  specified.
- Maven Wrapper is present.
- Server binds to `127.0.0.1:8080` by default.
- `GET /health` returns `200` and `{"status":"UP"}`.
- Health does not call an AI provider.
- Checkstyle and Spotless are configured for later release verification.
- The initialized repository tracks
  `https://github.com/skp2001vn/graphql-assistant2`.
- Implementation occurs on `codex/build-graphql-assistant`, not directly on
  `main`.
- The task commit is pushed only after the task's acceptance checks pass.

### Verification

```text
./mvnw test -Dtest=HealthControllerTest
./mvnw spotless:check
./mvnw checkstyle:check
```

## Task 2 — Load and Validate the Configured GraphQL Schema

Size: Medium
Dependencies: Task 1

### Outcome

Load one configurable classpath schema at startup, parse it with GraphQL Java,
retain its text and parsed representation, and fail clearly for invalid input.

### Likely files

- `src/main/resources/schema.graphql`
- `src/main/resources/application.yml`
- `src/main/java/com/example/graphqlassistant/config/AssistantProperties.java`
- `src/main/java/com/example/graphqlassistant/schema/GraphqlSchemaContext.java`
- `src/main/java/com/example/graphqlassistant/schema/GraphqlSchemaLoader.java`
- `src/test/resources/*`
- `src/test/java/com/example/graphqlassistant/schema/GraphqlSchemaLoaderTest.java`
- `src/test/java/com/example/graphqlassistant/schema/SchemaStartupTest.java`

### Acceptance criteria

- Existing `resources/schema.graphql` is placed in the standard Maven resources
  directory.
- `assistant.schema.location` defaults to `classpath:schema.graphql`.
- The configured resource is read as UTF-8 exactly once at startup.
- Empty, missing, unreadable, or invalid SDL prevents startup with a useful
  exception message.
- Runtime state exposes immutable schema text and parsed schema information.
- The application does not execute GraphQL operations.

### Verification

```text
./mvnw test -Dtest=GraphqlSchemaLoaderTest,SchemaStartupTest
```

## Task 3 — Establish API Models, Error Handling, and Request Boundaries

Size: Medium
Dependencies: Tasks 1–2

### Outcome

Define the externally visible response contracts and consistently enforce the
`text/plain`, nonblank, 100 KB request boundary.

### Likely files

- `src/main/java/com/example/graphqlassistant/api/AssistantController.java`
- `src/main/java/com/example/graphqlassistant/api/AssistantResponse.java`
- `src/main/java/com/example/graphqlassistant/api/GenerateResponse.java`
- `src/main/java/com/example/graphqlassistant/api/TroubleshootResponse.java`
- `src/main/java/com/example/graphqlassistant/api/TroubleshootingIssue.java`
- `src/main/java/com/example/graphqlassistant/api/ApiError.java`
- `src/main/java/com/example/graphqlassistant/api/GlobalExceptionHandler.java`
- `src/main/java/com/example/graphqlassistant/api/RequestIdFilter.java`
- `src/test/java/com/example/graphqlassistant/api/AssistantRequestValidationTest.java`
- `src/test/java/com/example/graphqlassistant/api/GlobalExceptionHandlerTest.java`

### Acceptance criteria

- `POST /assistant` consumes only UTF-8 `text/plain` and produces JSON.
- Blank input returns `400 INVALID_REQUEST`.
- Bodies over 100 KB return `413 REQUEST_TOO_LARGE`.
- Other content types return `415 UNSUPPORTED_MEDIA_TYPE`.
- Success models match the approved `GENERATE` and `TROUBLESHOOT` shapes.
- Errors use the approved fields and include a request ID.
- No internal stack trace or exception detail appears in a client response.
- Controller behavior is tested without a real AI provider.

### Verification

```text
./mvnw test -Dtest=AssistantRequestValidationTest,GlobalExceptionHandlerTest
```

## Task 4 — Implement LangChain4j Provider Selection and Configuration

Size: Large
Dependencies: Tasks 1–3

### Outcome

Create the minimal provider abstraction and select either Ollama or OpenAI once
at startup through validated configuration.

### Likely files

- `src/main/java/com/example/graphqlassistant/config/AssistantProperties.java`
- `src/main/java/com/example/graphqlassistant/config/AiProviderConfiguration.java`
- `src/main/java/com/example/graphqlassistant/provider/AssistantAiProvider.java`
- `src/main/java/com/example/graphqlassistant/provider/LangChain4jAssistantProvider.java`
- `src/main/java/com/example/graphqlassistant/provider/AiProviderException.java`
- `src/test/java/com/example/graphqlassistant/config/AiProviderConfigurationTest.java`
- `src/test/java/com/example/graphqlassistant/provider/LangChain4jAssistantProviderTest.java`

### Acceptance criteria

- `ollama` is selected by default.
- Ollama defaults to `http://localhost:11434` and `qwen3:8b`.
- OpenAI defaults to `gpt-5.4-mini` and requires a nonblank API key.
- Unsupported provider values and missing selected-provider settings fail
  startup clearly.
- Exactly one provider/chat model is active.
- LangChain4j Ollama and OpenAI integrations are used; Spring AI is not present
  on the classpath.
- Requests cannot override provider or model.
- No automatic cross-provider fallback or retry exists.
- Provider exceptions are translated to internal typed failures.
- API keys cannot appear in logs or exception messages.

### Verification

```text
./mvnw test -Dtest=AiProviderConfigurationTest,LangChain4jAssistantProviderTest
```

## Task 5 — Add Bounded Agents and GraphQL Tool Calling

Size: Large
Dependencies: Tasks 2–4

### Outcome

Create a stateless orchestrator with generation and troubleshooting specialist
agents, backed by typed, deterministic GraphQL tools.

### Likely files

- `src/main/java/com/example/graphqlassistant/agent/AssistantOrchestrator.java`
- `src/main/java/com/example/graphqlassistant/agent/AssistantRouter.java`
- `src/main/java/com/example/graphqlassistant/agent/RoutingDecision.java`
- `src/main/java/com/example/graphqlassistant/agent/GenerationAgent.java`
- `src/main/java/com/example/graphqlassistant/agent/TroubleshootingAgent.java`
- `src/main/java/com/example/graphqlassistant/tools/GraphqlAssistantTools.java`
- `src/main/java/com/example/graphqlassistant/tools/SchemaInspectionTool.java`
- `src/main/java/com/example/graphqlassistant/tools/OperationValidationTool.java`
- `src/main/java/com/example/graphqlassistant/tools/OperationFormattingTool.java`
- `src/test/java/com/example/graphqlassistant/agent/AssistantOrchestratorTest.java`
- `src/test/java/com/example/graphqlassistant/tools/GraphqlAssistantToolsTest.java`

### Acceptance criteria

- Every nonblank request first receives a dedicated AI router call.
- The router produces a typed `GENERATE`, `TROUBLESHOOT`, or
  `CLARIFICATION_REQUIRED` decision with reason and confidence.
- The router has no tools and cannot produce the final API answer.
- The orchestration layer validates routing output and converts
  low-confidence/insufficient requests into clarification rather than guessing.
- The orchestrator routes each accepted request to exactly one specialist
  agent.
- The agents are stateless between requests.
- LangChain4j 1.16.3 AI Services and agentic workflow APIs are used.
- Experimental LangChain4j agentic APIs are isolated behind application-owned
  router/orchestrator interfaces.
- LangChain4j tool calling exposes `inspectSchema`, `validateOperation`, and
  `formatOperation`.
- Tools use typed, validated inputs and structured outputs.
- Tools are read-only and cannot access files, shell commands, arbitrary
  network endpoints, secrets, or GraphQL execution.
- Tool calls are capped at four rounds by default and respect the overall
  timeout.
- Invalid tool arguments and tool-loop exhaustion fail predictably.
- The final application boundary independently validates returned operations.
- Tests cover routing, tool selection, tool output, invalid arguments, and loop
  limits.
- Tests prove that the router runs before the specialist and malformed router
  output is rejected.

### Verification

```text
./mvnw test -Dtest=AssistantOrchestratorTest,GraphqlAssistantToolsTest
```

## Task 6 — Generate GraphQL Operations End to End

Size: Large
Dependencies: Tasks 2–5

### Outcome

Support natural-language generation prompts from HTTP request through model
interaction to a validated `GENERATE` response containing a pretty-printed
operation as an array of formatted lines.

### Likely files

- `src/main/java/com/example/graphqlassistant/assistant/AssistantService.java`
- `src/main/java/com/example/graphqlassistant/assistant/AssistantPromptFactory.java`
- `src/main/java/com/example/graphqlassistant/assistant/AiAssistantResult.java`
- `src/main/java/com/example/graphqlassistant/schema/GraphqlOperationProcessor.java`
- `src/main/java/com/example/graphqlassistant/api/AssistantController.java`
- `src/test/java/com/example/graphqlassistant/assistant/GenerationServiceTest.java`
- `src/test/java/com/example/graphqlassistant/api/GenerationApiTest.java`
- `src/test/java/com/example/graphqlassistant/schema/GraphqlOperationProcessorTest.java`

### Acceptance criteria

- The model receives strict system instructions and the user prompt, then uses
  read-only tools to retrieve relevant trusted schema context.
- The orchestrator selects the generation agent, which can inspect the schema
  and validate/format its proposed operation through tools.
- A valid generation result returns `intent`, a named operation, and variables.
- Query and mutation operations are supported according to the configured
  schema.
- Argument values use variables when practical.
- Missing runtime values are represented by realistic type-compatible examples,
  not implementation placeholders.
- Returned operations parse, validate against the loaded schema, and are
  consistently pretty-printed as arrays containing one line per element.
- Invalid, incomplete, contradictory, or non-schema-valid model output returns
  `502 INVALID_AI_RESPONSE`.
- Provider failures return `502 AI_PROVIDER_ERROR`.
- Tests use a fake provider and cover list and argument-based generation.

### Verification

```text
./mvnw test -Dtest=GenerationServiceTest,GenerationApiTest,GraphqlOperationProcessorTest
```

## Task 7 — Troubleshoot GraphQL Operations End to End

Size: Large
Dependencies: Task 6

### Outcome

Support troubleshooting prompts containing GraphQL operations and return all
AI-identified issues plus the corrected operation.

### Likely files

- `src/main/java/com/example/graphqlassistant/assistant/AssistantService.java`
- `src/main/java/com/example/graphqlassistant/assistant/AssistantPromptFactory.java`
- `src/main/java/com/example/graphqlassistant/assistant/AiAssistantResult.java`
- `src/main/java/com/example/graphqlassistant/api/TroubleshootResponse.java`
- `src/test/java/com/example/graphqlassistant/assistant/TroubleshootingServiceTest.java`
- `src/test/java/com/example/graphqlassistant/api/TroubleshootingApiTest.java`

### Acceptance criteria

- The troubleshooting specialist, rather than deterministic validation logic,
  identifies and explains issues.
- The orchestrator selects the troubleshooting agent, which can inspect the
  schema and validate/format the correction through tools.
- A successful response includes every reported issue, its details and
  suggestion, a complete corrected operation, and variables.
- The corrected operation preserves the submitted operation's purpose while
  applying the identified fixes.
- The corrected operation is named, parsed, schema-validated, and returned as a
  pretty-printed array containing one line per element.
- Empty issue lists are returned for valid submitted operations. Missing
  corrections and invalid corrected operations produce `502 INVALID_AI_RESPONSE`.
- Multiple-issue troubleshooting is covered by deterministic tests.

### Verification

```text
./mvnw test -Dtest=TroubleshootingServiceTest,TroubleshootingApiTest
```

## Task 8 — Handle Clarification and AI Failure Paths

Size: Medium
Dependencies: Tasks 6–7

### Outcome

Complete the public API behavior for ambiguous prompts, timeouts, unavailable
providers, rejected calls, and unusable structured output.

### Likely files

- `src/main/java/com/example/graphqlassistant/assistant/AssistantService.java`
- `src/main/java/com/example/graphqlassistant/api/GlobalExceptionHandler.java`
- `src/main/java/com/example/graphqlassistant/provider/*`
- `src/test/java/com/example/graphqlassistant/api/AssistantFailureApiTest.java`
- `src/test/java/com/example/graphqlassistant/assistant/AssistantFailureServiceTest.java`

### Acceptance criteria

- Ambiguous or insufficient prompts return `422 CLARIFICATION_REQUIRED` with
  actionable guidance.
- Provider connection failures, timeouts, and provider rejection return
  `502 AI_PROVIDER_ERROR`.
- Invalid structured output returns `502 INVALID_AI_RESPONSE`.
- No partial AI output is returned as a successful response.
- The end-to-end hard timeout is configurable and defaults to 60 seconds; the
  warm response target is 30 seconds.
- Router, specialist, and tool-follow-up model calls share the remaining
  end-to-end timeout budget.
- Tool failures and exhausted tool-round limits map to controlled errors without
  exposing internals.
- Automated tests cover each mapping without live external services.

### Verification

```text
./mvnw test -Dtest=AssistantFailureApiTest,AssistantFailureServiceTest
```

## Task 9 — Add Full-Content Agent and Request Logging Safely

Size: Medium
Dependencies: Tasks 3–8

### Outcome

Log operational metadata by default and support explicitly enabled full
request/response content while protecting provider credentials.

### Likely files

- `src/main/java/com/example/graphqlassistant/api/RequestIdFilter.java`
- `src/main/java/com/example/graphqlassistant/assistant/AssistantService.java`
- `src/main/java/com/example/graphqlassistant/provider/*`
- `src/main/resources/application.yml`
- `src/test/java/com/example/graphqlassistant/logging/AssistantLoggingTest.java`

### Acceptance criteria

- Default logs contain request ID, provider/model, selected agent, tool names,
  status, latency, and error category.
- Explicitly enabled full-content logs also contain schema, prompt, raw AI
  response, normalized response, and complete tool inputs/outputs.
- Logs never contain OpenAI API keys or authorization headers.
- Full-content logging is disabled by default, and its opt-in privacy risk is
  documented in configuration and the README.
- Request ID is returned in errors and participates in all request log entries.
- Logging tests verify required content and credential exclusion.

### Verification

```text
./mvnw test -Dtest=AssistantLoggingTest
```

## Task 10 — Publish OpenAPI and Local Usage Documentation

Size: Medium
Dependencies: Tasks 1–9

### Outcome

Make the API self-documenting and provide exact local setup and usage
instructions for Ollama and OpenAI.

### Likely files

- `src/main/java/com/example/graphqlassistant/config/OpenApiConfiguration.java`
- API controller/model annotations where useful
- `README.md`
- `EVALS.md`
- `.env.example`
- `src/test/java/com/example/graphqlassistant/api/OpenApiTest.java`

### Acceptance criteria

- `/v3/api-docs` and `/swagger-ui.html` are available.
- OpenAPI documents `text/plain`, both success variants, all standard errors,
  and examples.
- README covers Java 21, Maven Wrapper, schema placement, Ollama preparation,
  OpenAI configuration, commands, curl examples, and troubleshooting.
- README prominently warns that full schemas, prompts, variables, operations,
  and AI responses are logged.
- README and `EVALS.md` explain the agent flow, available tools, deterministic
  evals, and live Ollama evals.
- `.env.example` contains placeholders only.

### Verification

```text
./mvnw test -Dtest=OpenApiTest
./mvnw spring-boot:run
curl http://localhost:8080/v3/api-docs
```

## Task 11 — Build Deterministic and Live Ollama Evaluations

Size: Large
Dependencies: Tasks 5–10

### Outcome

Create a versioned evaluation dataset and executable scoring harness for agent
routing, tool use, generation, troubleshooting, ambiguity, safety, and latency.

### Likely files

- `src/test/resources/evals/generation.jsonl`
- `src/test/resources/evals/troubleshooting.jsonl`
- `src/test/resources/evals/clarification.jsonl`
- `src/test/resources/evals/adversarial.jsonl`
- `src/test/java/com/example/graphqlassistant/evals/DeterministicEvalTest.java`
- `src/test/java/com/example/graphqlassistant/evals/LiveOllamaEvalTest.java`
- `src/test/java/com/example/graphqlassistant/evals/EvalScorer.java`
- `EVALS.md`
- `target/evals/*` generated reports

### Acceptance criteria

- The dataset includes list generation, argument/variables generation,
  troubleshooting with one and multiple issues, ambiguous prompts, malformed
  model output, prompt injection, and required tool-use cases.
- `./mvnw test -Pevals` runs deterministic fake-provider transcripts and is part
  of the release gate.
- Deterministic evals enforce 100% response-contract, intent, GraphQL validity,
  and curated clarification thresholds.
- `./mvnw test -Pevals-live` calls the user's live Ollama `qwen3:8b`.
- Live evals record outputs, tool traces, latency, hard-check failures, optional
  LangChain4j LLM-as-judge scores, per-case results, and aggregate pass rate.
- Hard GraphQL/contract failures cannot be overridden by model-based scores.
- The live target is at least 90% case pass rate, with each warm request under
  30 seconds for latency readiness.
- Eval reports are reproducible enough to compare prompt or tool changes.

### Verification

```text
./mvnw test -Pevals
./mvnw test -Pevals-live
```

## Task 12 — Final Verification, GitHub Publication, and Readiness Report

Size: Medium
Dependencies: Tasks 1–11

### Outcome

Run the complete release gate, verify local behavior with the default Ollama
configuration when available, and record readiness honestly.

### Likely files

- `tasks/todo.md`
- `README.md` if verification reveals documentation gaps
- `READINESS.md`
- tests or source files only when a verified defect requires a focused fix

### Acceptance criteria

- All automated tests pass without a live AI provider.
- Format, style, compilation, and packaging checks pass.
- Application startup and `/health` are manually verified.
- Swagger UI and OpenAPI JSON are manually verified.
- If local Ollama with `qwen3:8b` is available, generation and troubleshooting
  smoke tests and the live eval profile run.
- Warm response latency is measured and recorded; the result is not fabricated
  if local hardware/provider conditions prevent the under-30-second target.
- Any discovered defect receives a regression test before its fix.
- `READINESS.md` records commands, results, known limitations, and a GO/NO-GO
  recommendation.
- All checklist items in `tasks/todo.md` reflect actual status.
- Changes are organized into intentional commits on the feature branch.
- The branch is pushed to `skp2001vn/graphql-assistant2` and a draft PR is
  opened after the final task.
- GitHub authentication, remote reachability, and admin/push permission were
  verified before implementation.

### Verification

```text
./mvnw verify
./mvnw test -Pevals-live
./mvnw clean package
./mvnw spring-boot:run
curl http://localhost:8080/health
curl -X POST http://localhost:8080/assistant \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data 'generate the query to get the list of country'
curl --location 'http://localhost:8080/assistant' \
  --header 'Content-Type: text/plain' \
  --data 'debug the below query:
query CountryQuery($code: ID!) {
  country(code: $code) {
    code
    name1
    native1
    emoji
    capital
    currency
    continent {
      code
      name
    }
    languages {
      code
      name
    }
  }
}'
```

## Final Definition of Done

The work is done only when all approved specification success criteria are met,
`./mvnw verify` passes, readiness evidence is recorded, and no task remains
incorrectly marked complete.
