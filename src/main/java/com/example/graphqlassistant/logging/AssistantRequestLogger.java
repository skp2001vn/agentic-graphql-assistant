package com.example.graphqlassistant.logging;

import com.example.graphqlassistant.agent.RoutingIntent;
import com.example.graphqlassistant.api.AssistantResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AssistantRequestLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssistantRequestLogger.class);

  private final boolean enabled;

  private final boolean fullContentEnabled;

  private final ObjectMapper objectMapper;

  private final String providerApiKey;

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

  public static AssistantRequestLogger disabled() {
    return new AssistantRequestLogger(false, false, new ObjectMapper(), null);
  }

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
