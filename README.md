# GraphQL Assistant

GraphQL Assistant is a local, stateless Java API that generates sample GraphQL
operations from natural-language prompts and troubleshoots pasted operations.
It validates and formats operations against one configured GraphQL SDL schema,
but it never executes them against a GraphQL server.

> [!CAUTION]
> Full-content logging is enabled by default. Application logs contain the
> complete configured schema, prompts, pasted operations, variables, AI
> responses, and tool inputs and outputs. These values may contain secrets or
> private data. API keys and authorization headers are excluded, but you should
> still disable full-content logging before using sensitive content:
> `ASSISTANT_LOGGING_FULL_CONTENT_ENABLED=false`.
>
> OpenAI mode also sends prompts and schema-derived content to OpenAI. The
> service has no authentication and binds to `127.0.0.1`; do not expose it to an
> untrusted network without adding authentication, rate limiting, safer logging,
> and transport security.

## Requirements

- Java 21
- The included Maven Wrapper (`./mvnw`); a separate Maven installation is not
  required
- One AI provider:
  - Ollama running locally with `qwen3:8b` (default), or
  - an OpenAI API key

Confirm Java before starting:

```bash
java -version
```

The output must report Java 21.

## Quick start with Ollama

Install and start [Ollama](https://docs.ollama.com/quickstart), then download the
configured model:

```bash
ollama pull qwen3:8b
ollama ls
```

The Ollama application normally starts its local service automatically. On a
system where it does not, run `ollama serve` in a separate terminal. Its API
must be reachable at `http://localhost:11434`.

Start GraphQL Assistant from the repository root:

```bash
./mvnw spring-boot:run
```

Startup loads and validates the schema but does not contact Ollama. In another
terminal, check the application:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"UP"}
```

## Schema configuration

The default schema is
[`src/main/resources/schema.graphql`](src/main/resources/schema.graphql). Replace
that file with the SDL schema the assistant should use before starting the
application.

To keep a schema outside the application resources, set a Spring resource
location:

```bash
export ASSISTANT_SCHEMA_LOCATION=file:/absolute/path/to/schema.graphql
./mvnw spring-boot:run
```

The service stops during startup if the configured resource is missing,
unreadable, empty, or invalid SDL. It loads exactly one schema once at startup;
restart after changing it.

## Configuration

Copy the example if you prefer environment variables in a local file:

```bash
cp .env.example .env
```

Spring Boot does not load `.env` files itself. Export the file before running:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

`.env` is ignored by Git. `.env.example` contains only public defaults and a
placeholder API key.

Important settings:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `ASSISTANT_AI_PROVIDER` | `ollama` | Select `ollama` or `openai` at startup |
| `ASSISTANT_AI_OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama API base URL |
| `ASSISTANT_AI_OLLAMA_MODEL` | `qwen3:8b` | Ollama model |
| `OPENAI_API_KEY` | empty | Required when the provider is `openai` |
| `ASSISTANT_AI_OPENAI_MODEL` | `gpt-5.4-mini` | OpenAI model |
| `ASSISTANT_SCHEMA_LOCATION` | `classpath:schema.graphql` | SDL resource location |
| `ASSISTANT_LOGGING_FULL_CONTENT_ENABLED` | `true` | Log complete request, model, schema, and tool content |

### OpenAI

Set the provider and a real key in your shell or untracked `.env`:

```bash
export ASSISTANT_AI_PROVIDER=openai
export OPENAI_API_KEY=replace-with-your-openai-api-key
export ASSISTANT_AI_OPENAI_MODEL=gpt-5.4-mini
./mvnw spring-boot:run
```

The application fails at startup when OpenAI is selected without a nonblank
key. It does not silently fall back to Ollama.

## Use the API

The API accepts a nonblank UTF-8 `text/plain` request body up to 100 KB.

Generate an operation:

```bash
curl -X POST http://localhost:8080/assistant \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data 'Generate a query that lists country codes and names.'
```

Example response:

```json
{
  "intent": "GENERATE",
  "query": [
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

Troubleshoot an operation:

```bash
curl -X POST http://localhost:8080/assistant \
  -H 'Content-Type: text/plain; charset=UTF-8' \
  --data 'Fix this query: query ListCountries { countries { title } }'
```

Example response:

```json
{
  "intent": "TROUBLESHOOT",
  "issues": [
    {
      "issue": "Unknown field",
      "details": "The schema defines 'name', not 'title'.",
      "suggestion": "Replace 'title' with 'name'."
    }
  ],
  "correctedQuery": [
    "query ListCountries {",
    "  countries {",
    "    name",
    "  }",
    "}"
  ],
  "variables": {}
}
```

When a prompt does not provide a required variable value, the API returns a
realistic type-compatible example such as `"CA"` for a country code rather than
an implementation placeholder.

Ambiguous prompts return `422 CLARIFICATION_REQUIRED`. Provider, invalid model
response, and bounded agent failures return structured `502` errors. All
handled errors include a request ID:

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

## OpenAPI

With the application running:

- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

The contract documents the `text/plain` request, generation and
troubleshooting responses, standard error responses, and examples.

## How the agent works

Each request is independent:

1. A dedicated AI router classifies the prompt as generation,
   troubleshooting, or clarification required.
2. Only the selected generation or troubleshooting specialist runs.
3. The specialist may call bounded, read-only GraphQL tools:
   - `inspectSchema` returns relevant root operations, types, fields, arguments,
     and input types.
   - `validateOperation` parses and validates an operation against the
     configured schema.
   - `formatOperation` returns a canonical pretty-printed operation.
4. Java validates the structured model result and independently parses,
   schema-validates, and formats the final operation before returning it.

The router has no tools, tool arguments are validated, tool-call rounds are
bounded, no chat history is retained, and no generated operation is executed.

## Commands

| Command | Purpose |
| --- | --- |
| `./mvnw spring-boot:run` | Start the local API |
| `./mvnw test` | Run the automated tests |
| `./mvnw test -Dtest=OpenApiTest` | Verify OpenAPI and Swagger UI publication |
| `./mvnw checkstyle:check` | Run static style checks |
| `./mvnw spotless:check` | Check Java formatting |
| `./mvnw spotless:apply` | Apply Java formatting |
| `./mvnw verify` | Run the complete offline verification gate |
| `./mvnw clean package` | Build the application JAR |

Evaluation datasets, scoring, reports, and commands are documented in
[EVALS.md](EVALS.md).

## Troubleshooting

- **`java` reports another version:** set `JAVA_HOME` to a Java 21 JDK and
  reopen the terminal.
- **Startup says the schema is missing or invalid:** verify
  `ASSISTANT_SCHEMA_LOCATION`, use a `file:/absolute/path` URI for an external
  file, and validate the file as GraphQL SDL.
- **Ollama requests return `502 AI_PROVIDER_ERROR`:** confirm `ollama ls`
  includes `qwen3:8b`, start `ollama serve` if needed, and verify
  `curl http://localhost:11434/api/tags`.
- **OpenAI startup fails:** set both `ASSISTANT_AI_PROVIDER=openai` and a
  nonblank `OPENAI_API_KEY`.
- **The API returns `415`:** send `Content-Type: text/plain; charset=UTF-8`,
  not JSON.
- **The API returns `422`:** add the requested operation intent or paste the
  GraphQL operation to troubleshoot; this is a clarification response, not a
  server failure.
- **Sensitive content appears in logs:** this is the documented default. Stop
  the application, remove or secure existing logs, and restart with
  `ASSISTANT_LOGGING_FULL_CONTENT_ENABLED=false`.
- **A failure includes a request ID:** use that ID to correlate the request,
  model, tool, and completion log entries.
