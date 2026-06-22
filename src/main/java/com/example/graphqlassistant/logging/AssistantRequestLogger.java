package com.example.graphqlassistant.logging;

import com.example.graphqlassistant.agent.RoutingIntent;
import com.example.graphqlassistant.api.AssistantResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits structured observability events across the AI request lifecycle.
 *
 * <p>The logger correlates prompts, routing, model inference, tool calls, and normalized responses.
 * Full-content logging is explicitly configurable, and configured provider credentials are redacted
 * before serialization to reduce secret exposure.
 */
public final class AssistantRequestLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssistantRequestLogger.class);

  private final boolean enabled;

  private final boolean fullContentEnabled;

  private final ObjectMapper objectMapper;

  private final String providerApiKey;

  /**
   * Creates an enabled structured logger.
   *
   * @param fullContentEnabled whether schemas, prompts, model output, and tool payloads are logged
   * @param objectMapper serializer for structured model and tool data
   * @param providerApiKey credential to redact from logged content
   */
  public AssistantRequestLogger(
      boolean fullContentEnabled, ObjectMapper objectMapper, String providerApiKey) {
    this(true, fullContentEnabled, objectMapper, providerApiKey);
  }

  private AssistantRequestLogger(
      boolean enabled,
      boolean fullContentEnabled,
      ObjectMapper objectMapper,
      String providerApiKey) {
    this.enabled = enabled;
    this.fullContentEnabled = fullContentEnabled;
    this.objectMapper = objectMapper;
    this.providerApiKey = providerApiKey;
  }

  /**
   * Creates a no-op logger for tests or compositions that do not require AI telemetry.
   *
   * @return disabled logger instance
   */
  public static AssistantRequestLogger disabled() {
    return new AssistantRequestLogger(false, false, new ObjectMapper(), null);
  }

  /**
   * Logs provider metadata and optionally the schema and prompt entering the LLM workflow.
   *
   * @param provider selected inference provider
   * @param model selected model identifier
   * @param schema GraphQL SDL supplied as grounding context
   * @param prompt user request supplied to the agent pipeline
   */
  public void requestStarted(String provider, String model, String schema, String prompt) {
    if (!enabled) {
      return;
    }
    var event =
        LOGGER
            .atInfo()
            .addKeyValue("event", "assistant_request_started")
            .addKeyValue("requestId", AssistantRequestContext.requestId())
            .addKeyValue("provider", provider)
            .addKeyValue("model", model)
            .addKeyValue("fullContentEnabled", fullContentEnabled);
    if (fullContentEnabled) {
      event.addKeyValue("schema", redact(schema)).addKeyValue("prompt", redact(prompt));
    }
    event.log("Assistant request started");
  }

  /**
   * Logs the confidence-gated routing branch selected for the request.
   *
   * @param intent structured intent emitted by the router
   */
  public void agentSelected(RoutingIntent intent) {
    if (!enabled) {
      return;
    }
    String selectedAgent =
        switch (intent) {
          case GENERATE -> "GENERATION";
          case TROUBLESHOOT -> "TROUBLESHOOTING";
          case CLARIFICATION_REQUIRED -> "NONE";
        };
    AssistantRequestContext.selectAgent(selectedAgent);
    LOGGER
        .atInfo()
        .addKeyValue("event", "assistant_agent_selected")
        .addKeyValue("requestId", AssistantRequestContext.requestId())
        .addKeyValue("selectedAgent", selectedAgent)
        .log("Assistant agent selected");
  }

  /**
   * Logs completion of a provider inference call and optionally its raw response.
   *
   * @param provider inference provider
   * @param model model identifier
   * @param response LangChain4j response including model metadata and content
   */
  public void aiResponse(String provider, String model, ChatResponse response) {
    if (!enabled) {
      return;
    }
    var event =
        LOGGER
            .atInfo()
            .addKeyValue("event", "assistant_ai_response")
            .addKeyValue("requestId", AssistantRequestContext.requestId())
            .addKeyValue("provider", provider)
            .addKeyValue("model", model);
    if (fullContentEnabled) {
      event.addKeyValue("rawAiResponse", redact(String.valueOf(response)));
    }
    event.log("Assistant AI response received");
  }

  /**
   * Measures and logs a deterministic tool invocation around an AI agent action.
   *
   * @param toolName stable tool identifier
   * @param input structured tool input
   * @param invocation tool operation to execute
   * @param <T> tool output type
   * @return tool output
   */
  public <T> T toolCall(String toolName, Object input, Supplier<T> invocation) {
    if (!enabled) {
      return invocation.get();
    }
    long startedAt = System.nanoTime();
    try {
      T output = invocation.get();
      logToolCall(toolName, input, output, "SUCCESS", elapsedMillis(startedAt));
      return output;
    } catch (RuntimeException exception) {
      logToolCall(toolName, input, null, "ERROR", elapsedMillis(startedAt));
      throw exception;
    }
  }

  /**
   * Logs the final response after model output has been normalized and schema-validated.
   *
   * @param response public assistant response
   */
  public void normalizedResponse(AssistantResponse response) {
    if (!enabled) {
      return;
    }
    var event =
        LOGGER
            .atInfo()
            .addKeyValue("event", "assistant_response_normalized")
            .addKeyValue("requestId", AssistantRequestContext.requestId());
    if (fullContentEnabled) {
      event.addKeyValue("normalizedResponse", serializeAndRedact(response));
    }
    event.log("Assistant response normalized");
  }

  private void logToolCall(
      String toolName, Object input, Object output, String outcome, long durationMillis) {
    var event =
        LOGGER
            .atInfo()
            .addKeyValue("event", "assistant_tool_completed")
            .addKeyValue("requestId", AssistantRequestContext.requestId())
            .addKeyValue("toolName", toolName)
            .addKeyValue("outcome", outcome)
            .addKeyValue("durationMs", durationMillis);
    if (fullContentEnabled) {
      event
          .addKeyValue("toolInput", serializeAndRedact(input))
          .addKeyValue("toolOutput", serializeAndRedact(output));
    }
    event.log("Assistant tool completed");
  }

  private String serializeAndRedact(Object value) {
    String serialized;
    try {
      serialized = objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException | RuntimeException exception) {
      serialized = String.valueOf(value);
    }
    return redact(serialized);
  }

  private String redact(String value) {
    if (value == null
        || providerApiKey == null
        || providerApiKey.isBlank()
        || !value.contains(providerApiKey)) {
      return value;
    }
    return value.replace(providerApiKey, "[REDACTED]");
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
