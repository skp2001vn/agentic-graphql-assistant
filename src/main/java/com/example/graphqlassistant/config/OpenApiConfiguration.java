package com.example.graphqlassistant.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the assistant's OpenAPI metadata and reusable request, response, and error examples.
 *
 * <p>The examples make the natural-language-to-GraphQL contract discoverable without invoking an AI
 * model and document the stable failure taxonomy for provider and agent errors.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "GraphQL Assistant API",
            version = "1.0.0",
            description = "Generates or troubleshoots GraphQL operations without executing them."))
public class OpenApiConfiguration {

  /** Example natural-language request for GraphQL generation. */
  public static final String GENERATE_REQUEST =
      "Generate a query that lists country codes and names.";

  /** Example successful response from the generation specialist. */
  public static final String GENERATE_RESPONSE =
      """
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
      """;

  /** Example successful response from the troubleshooting specialist. */
  public static final String TROUBLESHOOT_RESPONSE =
      """
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
      """;

  /** Example error for an empty, malformed, or unreadable prompt. */
  public static final String INVALID_REQUEST =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 400,
        "code": "INVALID_REQUEST",
        "message": "Request body must contain a nonblank UTF-8 prompt.",
        "details": []
      }
      """;

  /** Example error for a prompt that exceeds the bounded request size. */
  public static final String REQUEST_TOO_LARGE =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 413,
        "code": "REQUEST_TOO_LARGE",
        "message": "Request body must not exceed 100 KB.",
        "details": []
      }
      """;

  /** Example error for an unsupported request media type or character encoding. */
  public static final String UNSUPPORTED_MEDIA_TYPE =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 415,
        "code": "UNSUPPORTED_MEDIA_TYPE",
        "message": "Content-Type must be text/plain with UTF-8 encoding.",
        "details": []
      }
      """;

  /** Example confidence-gating response when the router needs more context. */
  public static final String CLARIFICATION_REQUIRED =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 422,
        "code": "CLARIFICATION_REQUIRED",
        "message": "Specify what operation you want to generate or include the operation to troubleshoot.",
        "details": []
      }
      """;

  /** Example error for an unexpected application failure. */
  public static final String INTERNAL_ERROR =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 500,
        "code": "INTERNAL_ERROR",
        "message": "An unexpected error occurred.",
        "details": []
      }
      """;

  /** Example error for a failed local or hosted model inference request. */
  public static final String AI_PROVIDER_ERROR =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 502,
        "code": "AI_PROVIDER_ERROR",
        "message": "The configured AI provider could not complete the request.",
        "details": []
      }
      """;

  /** Example error for model output that violates the structured response contract. */
  public static final String INVALID_AI_RESPONSE =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 502,
        "code": "INVALID_AI_RESPONSE",
        "message": "The AI response did not satisfy the required contract.",
        "details": []
      }
      """;

  /** Example error for a bounded router, specialist, or tool-loop execution failure. */
  public static final String AGENT_EXECUTION_ERROR =
      """
      {
        "timestamp": "2026-06-18T15:00:00Z",
        "requestId": "8cd91a0b-93c2-4e9a-9dce-f7eed67f52a5",
        "status": 502,
        "code": "AGENT_EXECUTION_ERROR",
        "message": "The assistant agent could not complete the request safely.",
        "details": []
      }
      """;
}
