package com.example.graphqlassistant.agent;

import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.util.Objects;

public final class LangChain4jAgentFactory {

  public static final int MAX_TOOL_CALLING_ROUNDS = GraphqlAssistantTools.MAX_TOOL_CALLS + 1;

  private static final String INVALID_TOOL_ARGUMENTS =
      "INVALID_TOOL_ARGUMENTS: use the declared typed tool input";

  private static final String TOOL_EXECUTION_FAILED =
      "TOOL_EXECUTION_FAILED: correct the input or return a final response";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private LangChain4jAgentFactory() {
    throw new AssertionError("No instances");
  }

  public static AssistantRouter createRouter(ChatModel chatModel) {
    return AiServices.builder(AssistantRouter.class)
        .chatModel(Objects.requireNonNull(chatModel, "chatModel"))
        .build();
  }

  public static GenerationAgent createGenerationAgent(
      ChatModel chatModel, GraphqlAssistantTools tools) {
    GenerationToolAgent specialist =
        configureAgent(
                AgenticServices.agentBuilder(GenerationToolAgent.class),
                Objects.requireNonNull(chatModel, "chatModel"),
                Objects.requireNonNull(tools, "tools"))
            .outputKey("generationJson")
            .build();
    GenerationToolAgent workflow =
        AgenticServices.sequenceBuilder(GenerationToolAgent.class)
            .subAgents(specialist)
            .outputKey("generationJson")
            .build();
    return new ParsedGenerationAgent(workflow);
  }

  public static TroubleshootingAgent createTroubleshootingAgent(
      ChatModel chatModel, GraphqlAssistantTools tools) {
    TroubleshootingToolAgent specialist =
        configureAgent(
                AgenticServices.agentBuilder(TroubleshootingToolAgent.class),
                Objects.requireNonNull(chatModel, "chatModel"),
                Objects.requireNonNull(tools, "tools"))
            .outputKey("troubleshootingJson")
            .build();
    TroubleshootingToolAgent workflow =
        AgenticServices.sequenceBuilder(TroubleshootingToolAgent.class)
            .subAgents(specialist)
            .outputKey("troubleshootingJson")
            .build();
    return new ParsedTroubleshootingAgent(workflow);
  }

  public static SpecialistWorkflow createSpecialistWorkflow(
      GenerationAgent generationAgent, TroubleshootingAgent troubleshootingAgent) {
    UntypedAgent conditionalSpecialist =
        AgenticServices.conditionalBuilder()
            .subAgents(
                scope ->
                    scope.readState("routingIntent", RoutingIntent.CLARIFICATION_REQUIRED)
                        == RoutingIntent.GENERATE,
                Objects.requireNonNull(generationAgent, "generationAgent"))
            .subAgents(
                scope ->
                    scope.readState("routingIntent", RoutingIntent.CLARIFICATION_REQUIRED)
                        == RoutingIntent.TROUBLESHOOT,
                Objects.requireNonNull(troubleshootingAgent, "troubleshootingAgent"))
            .build();

    return AgenticServices.sequenceBuilder(SpecialistWorkflow.class)
        .subAgents(conditionalSpecialist)
        .output(
            scope ->
                switch (scope.readState("routingIntent", RoutingIntent.CLARIFICATION_REQUIRED)) {
                  case GENERATE -> scope.readState("generationResult");
                  case TROUBLESHOOT -> scope.readState("troubleshootingResult");
                  case CLARIFICATION_REQUIRED ->
                      throw new IllegalStateException("Clarification has no specialist result");
                })
        .build();
  }

  private static <T> dev.langchain4j.agentic.agent.AgentBuilder<T, ?> configureAgent(
      dev.langchain4j.agentic.agent.AgentBuilder<T, ?> builder,
      ChatModel chatModel,
      GraphqlAssistantTools tools) {
    return builder
        .chatModel(chatModel)
        .tools(tools)
        .maxToolCallingRoundTrips(MAX_TOOL_CALLING_ROUNDS)
        .toolArgumentsErrorHandler(
            (error, context) -> ToolErrorHandlerResult.text(INVALID_TOOL_ARGUMENTS))
        .toolExecutionErrorHandler(
            (error, context) -> ToolErrorHandlerResult.text(TOOL_EXECUTION_FAILED));
  }

  static SpecialistResult parseSpecialistResult(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, SpecialistResult.class);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new InvalidAgentResponseException("Specialist returned invalid structured output");
    }
  }
}
