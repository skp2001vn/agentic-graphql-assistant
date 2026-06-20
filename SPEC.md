# GraphQL AI Assistant API Specification

Status: Approved
Date: 2026-06-18

## 1. Objective

Build a local, stateless Java backend API that uses one configured GraphQL SDL
schema and an AI model to:

1. Generate a sample GraphQL query or mutation from an English natural-language
   prompt.
2. Troubleshoot a GraphQL query or mutation pasted into an English
   natural-language prompt.

The service returns operations and variables but never executes an operation
against a GraphQL server.

The AI workflow is agentic: a bounded orchestrator routes each request to a
generation or troubleshooting specialist, and the specialist can call
deterministic GraphQL tools before producing the final structured response.

## 2. Target Consumer

The consumer is an API client such as `curl`, Postman, or another application.
There is no user interface in the initial version.

## 3. Approved Technology Stack

- Java 21 LTS
  - Deliberately selected instead of Java 25. Spring Boot 4.1 supports Java 17
    through Java 26, but this project targets Java 21 as the more mature LTS
    baseline requested for local development.
- Spring Boot 4.1.0
- LangChain4j 1.16.3 as the sole AI integration framework
  - Use LangChain4j AI Services, structured outputs, tool calling, Ollama and
    OpenAI integrations, and the experimental agentic module.
  - Isolate agentic-module APIs behind application-owned interfaces because the
    module is explicitly experimental and may change between releases.
- Maven with Maven Wrapper
- Spring MVC
- Jackson
- GraphQL Java 25 for SDL parsing and GraphQL operation parsing/printing
  - `25` is the GraphQL Java library version, not the Java language/runtime
    version. GraphQL Java 25 is compatible with this project's Java 21 runtime;
    changing it to GraphQL Java 21 would select an older library release.
- springdoc-openapi 3.0.3 with Swagger UI
- JUnit 5 and Spring Boot Test
- LangChain4j agentic workflow and tool-calling APIs

Maven coordinates:

- Group: `com.example`
- Artifact: `graphql-assistant`
- Base package: `com.example.graphqlassistant`

No database, migration framework, authentication library, or GraphQL execution
server is required.

Source repository:

- GitHub: `https://github.com/skp2001vn/graphql-assistant2`
- Visibility: private
- Local `origin` is connected and authenticated with admin/push permission.
- `main` contains the reviewed planning baseline and completed task merges.
- Implementation branch: `codex/build-graphql-assistant`
- Each completed task is committed atomically and pushed to the implementation
  branch after its acceptance checks pass, reviewed through a pull request,
  merged into `main`, and synchronized back to the implementation branch.

## 4. Runtime Configuration

Configuration is selected at application startup. A request cannot override the
provider, model, or schema.

Proposed application properties:

```yaml
server:
  address: 127.0.0.1
  port: 8080

assistant:
  schema:
    location: classpath:schema.graphql
  request:
    max-size: 100KB
  ai:
    provider: ollama
    request-timeout: 60s
    warm-response-target: 30s
    max-tool-rounds: 4
    ollama:
      base-url: http://localhost:11434
      model: qwen3:8b
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: gpt-5.4-mini
```

Rules:

- `assistant.ai.provider` accepts `ollama` or `openai`.
- Ollama is the default provider.
- The OpenAI API key is read from the environment and is never committed.
- An unsupported provider or missing required provider configuration causes
  startup to fail with a clear message.
- The configured schema is loaded once at startup.
- A missing, unreadable, empty, or invalid schema causes startup to fail.
- The application does not require the selected AI provider to be reachable at
  startup.
- Agent tool-call rounds are bounded to prevent loops and protect the latency
  target.
- The end-to-end warm response target is 30 seconds. A 60-second hard request
  timeout allows the dedicated router, specialist, and bounded tool-call turns
  to complete without permitting indefinite execution.

## 5. Project Structure

```text
.
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
├── .gitignore
├── README.md
├── EVALS.md
├── READINESS.md
├── SPEC.md
├── tasks/
│   ├── plan.md
│   └── todo.md
└── src/
    ├── main/
    │   ├── java/com/example/graphqlassistant/
    │   │   ├── GraphqlAssistantApplication.java
    │   │   ├── api/
    │   │   ├── agent/
    │   │   ├── assistant/
    │   │   ├── config/
    │   │   ├── provider/
    │   │   ├── schema/
    │   │   └── tools/
    │   └── resources/
    │       ├── application.yml
    │       └── schema.graphql
    └── test/
        ├── java/com/example/graphqlassistant/
        └── resources/evals/
```

Packages may be collapsed when a separate layer would contain only one trivial
class. The design should remain small and task-oriented.

## 6. API Conventions

- Base URL: `http://localhost:8080`
- Success and error responses use JSON.
- `POST /assistant` consumes `text/plain`.
- Character encoding is UTF-8.
- Request bodies must be nonblank and no larger than 100 KB.
- API field names use lower camel case.
- GraphQL operations use descriptive PascalCase names such as `ListCountries`
  and `GetCountry`.
- GraphQL argument values use variables instead of being embedded in the
  operation when practical.
- GraphQL operations are consistently pretty-printed with two-space indentation
  and returned as arrays containing one formatted line per element.
- `variables` is always a JSON object. It is `{}` when no variables are needed.
- Streaming is not supported.

## 7. Endpoints

### 7.1 `POST /assistant`

Consumes:

```http
Content-Type: text/plain
Accept: application/json
```

The body is an English natural-language prompt. It may include a GraphQL
operation to troubleshoot.

The service uses the AI model to classify the prompt as generation,
troubleshooting, or insufficient/ambiguous. The configured schema is trusted
application context exposed through read-only schema tools, allowing agents to
retrieve the relevant schema slice instead of repeatedly sending the entire
schema. User text is treated as untrusted content and cannot override the
response contract or system instructions.

The orchestrator delegates to exactly one specialist agent. The specialist may
call approved schema/operation tools, but it cannot access the filesystem,
shell, network destinations, application secrets, or GraphQL execution.

#### Generate response

Status: `200 OK`

```json
{
  "intent": "GENERATE",
  "query": [
    "query GetCountry($code: ID!) {",
    "  country(code: $code) {",
    "    code",
    "    name",
    "  }",
    "}"
  ],
  "variables": {
    "code": "CA"
  }
}
```

Required fields:

- `intent`: always `GENERATE`
- `query`: array containing one line of the complete, named, pretty-printed
  GraphQL query or mutation per element
- `variables`: JSON object compatible with the operation; when the prompt does
  not supply a value, the service returns a realistic type-compatible example

#### Troubleshoot response

Status: `200 OK`

```json
{
  "intent": "TROUBLESHOOT",
  "issues": [
    {
      "issue": "Unknown field 'title' on type 'Country'.",
      "details": "The schema defines 'name', not 'title'.",
      "suggestion": "Replace 'title' with 'name'."
    }
  ],
  "correctedQuery": [
    "query ListCountries {",
    "  countries {",
    "    code",
    "    name",
    "  }",
    "}"
  ],
  "variables": {}
}
```

Required fields:

- `intent`: always `TROUBLESHOOT`
- `issues`: array containing all issues identified by the AI; it is empty when
  the submitted operation is valid
- `issues[].issue`: concise issue summary
- `issues[].details`: explanation grounded in the configured schema
- `issues[].suggestion`: specific remediation
- `correctedQuery`: array containing one line per element of the submitted
  operation with the identified fixes applied, complete, named, and
  pretty-printed
- `variables`: corrected or inferred JSON variables object; unresolved values
  are replaced with realistic type-compatible examples

The AI is responsible for identifying troubleshooting issues. The Java service
does not independently construct an issue list from deterministic GraphQL
validation. It does, however, treat model output as untrusted: structured output
is parsed and validated, and returned GraphQL operations are syntax/schema
checked and pretty-printed before being sent to the client. Unusable model
output produces a provider-response error rather than an unsafe or malformed
success response.

#### Ambiguous or insufficient prompt

Status: `422 Unprocessable Content`

The error `code` is `CLARIFICATION_REQUIRED`, and `message` explains what
information the client should add. The service does not guess.

### 7.2 `GET /health`

Status: `200 OK`

```json
{
  "status": "UP"
}
```

This endpoint verifies that the application started and loaded its schema. It
does not call Ollama or OpenAI.

### 7.3 OpenAPI

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`

The OpenAPI contract documents `text/plain` input, both success response shapes,
and all standard error responses.

## 8. Error Response Format

All handled errors use one structure:

```json
{
  "timestamp": "2026-06-18T15:00:00Z",
  "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
  "status": 422,
  "code": "CLARIFICATION_REQUIRED",
  "message": "Specify what operation you want to generate or include the operation to troubleshoot.",
  "details": []
}
```

Standard mappings:

| HTTP status | Code | Condition |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | Empty or malformed request |
| `413` | `REQUEST_TOO_LARGE` | Body exceeds 100 KB |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Content type is not `text/plain` |
| `422` | `CLARIFICATION_REQUIRED` | Prompt is ambiguous or insufficient |
| `502` | `AI_PROVIDER_ERROR` | Selected provider is unavailable or rejects the call |
| `502` | `INVALID_AI_RESPONSE` | Model output cannot satisfy the structured contract |
| `502` | `AGENT_EXECUTION_ERROR` | Tool failure or bounded tool loop cannot complete safely |
| `500` | `INTERNAL_ERROR` | Unexpected internal failure |

Stack traces, credentials, and internal exception details are never returned to
clients.

## 9. Agent, Tool-Calling, and AI Provider Design

- Use LangChain4j 1.16.3 as the only AI/agent framework. Do not include Spring
  AI, Spring AI Alibaba, AgentScope Java, or another overlapping AI framework in
  the initial version.
- Build the workflow with LangChain4j primitives:
  - AI Services for typed router and specialist interfaces
  - structured outputs mapped to Java records
  - LangChain4j agentic workflow APIs for conditional routing
  - LangChain4j `@Tool` function calling for deterministic GraphQL functions
  - LangChain4j Ollama and OpenAI model integrations
- Keep LangChain4j types at the `provider` and `agent` boundaries. Controllers,
  API response records, GraphQL services, and core orchestration contracts must
  remain framework-neutral where practical.
- Define one small internal provider interface used by the assistant service.
- Create exactly one provider implementation at startup based on
  `assistant.ai.provider`.
- Use LangChain4j chat model APIs for both Ollama and OpenAI.
- Implement a dedicated AI router call that returns a typed routing decision,
  followed by one selected specialist:
  - generation agent
  - troubleshooting agent
- The router returns `GENERATE`, `TROUBLESHOOT`, or
  `CLARIFICATION_REQUIRED`, plus a concise reason and confidence.
- Java validates the router's structured result. Unknown/malformed output is an
  invalid AI response; low-confidence or insufficient requests become
  clarification responses rather than guesses.
- The router itself has no tools. Only the selected specialist receives
  GraphQL tools.
- A normal request therefore uses at least two model calls. Tool use can add
  further model turns because each tool result must be returned to the
  specialist model before it can continue.
- Keep agents stateless across HTTP requests.
- Expose deterministic, side-effect-free tools through LangChain4j tool
  calling:
  - `inspectSchema`: find root operations, types, fields, arguments, and input
    types relevant to a request
  - `validateOperation`: parse and validate a proposed operation against the
    configured schema and return structured diagnostics
  - `formatOperation`: return a canonical pretty-printed operation
- Tool inputs and outputs use typed Java records and are validated at their
  boundaries.
- Cap a request at four tool-call rounds by default.
- Record tool names, complete inputs/outputs, duration, and outcome in logs;
  credentials and authorization headers remain excluded.
- The service performs final validation even if the agent already called the
  validation tool.
- Use a strict system prompt and structured-output mapping.
- Give the model the user prompt and require schema-grounded work through the
  approved tools. Tools may return the full schema only when the bounded schema
  is small or a complete view is necessary.
- Require one machine-readable model result covering `GENERATE`,
  `TROUBLESHOOT`, or `CLARIFICATION_REQUIRED`.
- Reject missing fields, contradictory intent fields, invalid JSON variables,
  and malformed GraphQL operations.
- Do not silently fall back from Ollama to OpenAI or vice versa.
- Do not retry requests in the initial version; retries could exceed the
  latency target and duplicate provider usage.
- Do not add agents or tools unless they measurably improve an approved eval or
  satisfy a concrete boundary requirement.
- Pin the LangChain4j version. Upgrading it requires running all deterministic
  and live evals because the agentic module is experimental.

## 10. Schema and GraphQL Operation Handling

- Move the existing `resources/schema.graphql` into
  `src/main/resources/schema.graphql` during project scaffolding.
- Read the configured resource as UTF-8.
- Parse and validate the SDL at startup with GraphQL Java.
- Keep the original schema text in memory for model context.
- Parse and validate generated/corrected operations against the loaded schema
  before returning them.
- Use GraphQL Java's AST printer to produce stable, readable formatting.
- Do not execute queries, mutations, or subscriptions.
- The current sample schema contains queries only, but the implementation and
  prompts must support mutations when a future configured schema defines them.

## 11. Logging and Observability

Each request receives or generates a request ID. Logs include:

- request ID
- provider and model
- schema content
- complete user prompt
- pasted GraphQL operation
- complete raw AI response
- agent selected and tool-call trace
- normalized API response
- status, latency, and error category

This intentionally logs potentially sensitive content because it was explicitly
requested. The README and startup documentation must prominently warn that
prompts, schemas, operations, variables, and AI responses may contain secrets or
private data. OpenAI mode also sends schema and prompt content to an external
service. API keys and authorization headers must never be logged.

The initial version uses application logs only and has no metrics backend,
distributed tracing backend, or persisted audit store.

## 12. Security Boundaries

- No authentication or authorization is included.
- The server binds to `127.0.0.1` by default to reduce accidental network
  exposure.
- CORS is not enabled.
- The API must not be exposed to an untrusted network without adding
  authentication, rate limiting, safer logging, and transport security.
- Limit request bodies to 100 KB.
- Apply provider connection/read timeouts.
- Treat user prompts and model output as untrusted.
- Treat model-selected tool arguments as untrusted and validate them before
  invocation.
- Tools are read-only and operate only on the in-memory configured schema and
  submitted operation.
- Agent loops are bounded by tool-round and timeout limits.
- Never interpolate model output into executable Java, shell commands, file
  paths, or network destinations.
- Do not log provider API keys.
- Dependency versions are pinned through the Spring Boot parent, LangChain4j
  BOM, and explicit versions where those BOMs do not manage them.

## 13. Statelessness and Data Model

There is no application database and no persisted domain model.

In-memory immutable runtime state consists of:

- configured schema location
- parsed schema/type registry
- original schema text
- selected provider configuration

Each request produces a response independently. No chat history or prior prompt
context is retained.

## 14. Testing and Evaluation Strategy

Tests must not require a live Ollama server or OpenAI account.

Required coverage:

- schema resource loading and startup failure cases
- provider selection and missing configuration
- generation response mapping
- troubleshooting response mapping with multiple issues
- clarification mapping to `422`
- empty input, unsupported media type, and 100 KB limit
- provider timeout/failure mapping to `502`
- invalid or incomplete model output mapping to `502`
- generated/corrected GraphQL parsing, schema validation, and formatting
- health endpoint
- OpenAPI endpoint availability
- logging behavior without leaking API keys
- orchestrator routing and bounded tool-call behavior
- dedicated AI router behavior, confidence handling, and malformed routing
  output
- tool input validation, successful calls, failures, and loop limits

Use mocked or fake provider responses for deterministic unit and integration
tests. Add regression tests before fixing defects discovered during
implementation.

### Evaluation suites

Maintain a versioned eval dataset under `src/test/resources/evals/` with
generation, troubleshooting, ambiguity, adversarial prompt-injection, malformed
model-output, and tool-use cases.

The project has two eval modes:

1. **Deterministic evals** run during `./mvnw verify` with fake provider
   transcripts. They score routing, tool selection, contract compliance,
   GraphQL syntax/schema validity, formatting, variables, expected issue
   coverage, clarification behavior, and error mapping.
2. **Live Ollama evals** run explicitly against the local
   `qwen3:8b` model. They perform real generation and troubleshooting cases,
   record per-case output, tool traces, latency, pass/fail reasons, and summary
   scores. They are not part of the offline build because model availability and
   hardware vary.

Where deterministic grading is insufficient, use a small application-owned
LLM-as-judge evaluator implemented through a separate LangChain4j AI Service as
a supplementary score. Model-based scores never override hard failures such as
invalid GraphQL, wrong response shape, or invented schema fields.

Initial eval thresholds:

- 100% valid response contract
- 100% parseable and schema-valid returned operations
- 100% correct intent on the curated deterministic dataset
- 100% refusal/clarification for curated insufficient prompts
- at least 90% overall live Ollama case pass rate
- no individual warm live request over 30 seconds for the latency target to be
  marked met; otherwise readiness records the measured limitation

## 15. Exact Development and Verification Commands

These approved commands are enabled by the Maven project:

```text
Dev:          ./mvnw spring-boot:run
Test:         ./mvnw test
Det. evals:   ./mvnw test -Pevals
Live evals:   ./mvnw test -Pevals-live
Lint:         ./mvnw checkstyle:check
Format:       ./mvnw spotless:apply
Format check: ./mvnw spotless:check
Verify:       ./mvnw verify
Package:      ./mvnw clean package
```

`./mvnw verify` is the release gate and must run tests, formatting checks, and
static style checks. Java compilation provides the language type check; there
is no separate Java type-check command.

Manual startup verification:

```text
curl http://localhost:8080/health
curl -X POST http://localhost:8080/assistant \
  -H 'Content-Type: text/plain' \
  --data 'Generate a query that lists country codes and names.'
```

## 16. Success Criteria

The initial release is complete when:

1. The application starts locally on Java 21 with the configured valid schema.
2. Invalid or missing schemas stop startup with a useful error.
3. Ollama is the default provider using `qwen3:8b` at
   `http://localhost:11434`.
4. OpenAI can be selected at startup and defaults to `gpt-5.4-mini`.
5. `/assistant` generates named, schema-valid, pretty-printed operations and
   variables from English prompts.
6. `/assistant` reports all AI-identified troubleshooting issues and returns the
   fully corrected, schema-valid, pretty-printed operation.
7. Ambiguous prompts return a structured `422` response.
8. Provider and malformed-output failures return structured `502` responses.
9. Requests larger than 100 KB are rejected.
10. `/health`, OpenAPI JSON, and Swagger UI are available.
11. Requests remain stateless and no operation is executed.
12. Warm `/assistant` requests complete in under 30 seconds when the selected
    provider itself can meet that target.
13. `./mvnw verify` passes without requiring live AI services.
14. README documentation explains setup, provider selection, example requests,
    response contracts, and the full-content logging risk.
15. Agent routing and read-only tool calling are bounded, observable, and
    covered by tests.
16. Deterministic evals meet all hard thresholds.
17. Live generation and troubleshooting smoke tests run against the user's
    local Ollama `qwen3:8b`, and measured results are recorded.
18. Each completed implementation task is committed to
    `codex/build-graphql-assistant`, reviewed through a pull request, merged into
    `main`, and synchronized back to the implementation branch.

## 17. Out of Scope

- GraphQL operation execution
- Uploading or selecting schemas per request
- Multiple schema files or schema merging
- Authentication and authorization
- Database persistence or chat history
- Streaming responses
- Non-English prompts
- Web or IDE user interface
- Docker and cloud deployment
- Automatic provider fallback
- Unbounded autonomous agents or tool loops
- Tools with filesystem, shell, arbitrary network, secret, or GraphQL execution
  access
- Deterministic Java-generated troubleshooting issue reports
- Production network hardening, rate limiting, or TLS termination
