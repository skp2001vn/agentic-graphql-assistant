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

/**
 * Builds the LangChain4j router and specialist agents used by the assistant.
 *
 * <p>The factory centralizes prompt-bound agent topology, bounded tool-calling rounds, structured
 * output parsing, and graceful tool error feedback. Generation and troubleshooting use separate
 * specialist prompts so each model context stays focused on one business objective.
 */
public final class LangChain4jAgentFactory {

  /** Maximum model/tool round trips, including the final model response after the tool budget. */
  public static final int MAX_TOOL_CALLING_ROUNDS = GraphqlAssistantTools.MAX_TOOL_CALLS + 1;

  private static final String INVALID_TOOL_ARGUMENTS =
      "INVALID_TOOL_ARGUMENTS: use the declared typed tool input";

  private static final String TOOL_EXECUTION_FAILED =
      "TOOL_EXECUTION_FAILED: correct the input or return a final response";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private LangChain4jAgentFactory() {
    throw new AssertionError("No instances");
  }

  /**
   * Creates the lightweight LLM intent-classification service.
   *
   * @param chatModel configured provider-neutral chat model
   * @return router that emits structured routing decisions
   */
  public static AssistantRouter createRouter(ChatModel chatModel) {
    return AiServices.builder(AssistantRouter.class)
        .chatModel(Objects.requireNonNull(chatModel, "chatModel"))
        .build();
  }

  /**
   * Creates a schema-grounded generation agent with a bounded ReAct-style tool loop.
   *
   * @param chatModel configured provider-neutral chat model
   * @param tools deterministic schema inspection and validation capabilities
   * @return typed adapter over the model's JSON structured output
   */
  public static GenerationAgent createGenerationAgent(
      ChatModel chatModel, GraphqlAssistantTools tools) {
    GenerationModelAgent specialist =
        configureAgent(
                AgenticServices.agentBuilder(GenerationModelAgent.class),
                Objects.requireNonNull(chatModel, "chatModel"),
                new SpecialistTools(Objects.requireNonNull(tools, "tools")))
            .outputKey("generationJson")
            .build();
    GenerationModelAgent workflow =
        AgenticServices.sequenceBuilder(GenerationModelAgent.class)
            .subAgents(specialist)
            .outputKey("generationJson")
            .build();
    return new ParsedGenerationAgent(workflow);
  }

  /**
   * Creates a troubleshooting agent that iteratively diagnoses and repairs GraphQL operations.
   *
   * @param chatModel configured provider-neutral chat model
   * @param tools deterministic schema inspection and validation capabilities
   * @return typed adapter over the model's JSON structured output
   */
  public static TroubleshootingAgent createTroubleshootingAgent(
      ChatModel chatModel, GraphqlAssistantTools tools) {
    TroubleshootingModelAgent specialist =
        configureAgent(
                AgenticServices.agentBuilder(TroubleshootingModelAgent.class),
                Objects.requireNonNull(chatModel, "chatModel"),
                new SpecialistTools(Objects.requireNonNull(tools, "tools")))
            .outputKey("troubleshootingJson")
            .build();
    TroubleshootingModelAgent workflow =
        AgenticServices.sequenceBuilder(TroubleshootingModelAgent.class)
            .subAgents(specialist)
            .outputKey("troubleshootingJson")
            .build();
    return new ParsedTroubleshootingAgent(workflow);
  }

  /**
   * Creates the conditional multi-agent workflow that dispatches only the routed specialist.
   *
   * @param generationAgent specialist for natural-language-to-GraphQL generation
   * @param troubleshootingAgent specialist for diagnosis and correction
   * @return workflow that selects a specialist from the routing intent held in agent state
   */
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
      dev.langchain4j.agentic.agent.AgentBuilder<T, ?> builder, ChatModel chatModel, Object tools) {
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
