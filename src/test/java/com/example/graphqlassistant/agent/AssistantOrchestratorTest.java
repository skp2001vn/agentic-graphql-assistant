package com.example.graphqlassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import graphql.schema.idl.SchemaParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssistantOrchestratorTest {

  private GraphqlAssistantTools tools;

  @BeforeEach
  void setUp() {
    String schema = "type Query { greeting: String! }";
    tools =
        GraphqlAssistantTools.from(
            new GraphqlSchemaContext(schema, new SchemaParser().parse(schema)));
  }

  @Test
  void runsTheRouterBeforeExactlyOneGenerationSpecialist() {
    List<String> calls = new ArrayList<>();
    AssistantRouter router =
        prompt -> {
          calls.add("router");
          return new RoutingDecision(RoutingIntent.GENERATE, "New operation requested", 0.95);
        };
    GenerationAgent generationAgent =
        prompt -> {
          calls.add("generation");
          return new SpecialistResult("query Greeting { greeting }");
        };
    TroubleshootingAgent troubleshootingAgent =
        prompt -> {
          calls.add("troubleshooting");
          return new SpecialistResult("query Greeting { greeting }");
        };
    AssistantOrchestrator orchestrator =
        orchestrator(
            router,
            (prompt, intent) ->
                switch (intent) {
                  case GENERATE -> generationAgent.generate(prompt);
                  case TROUBLESHOOT -> troubleshootingAgent.troubleshoot(prompt);
                  case CLARIFICATION_REQUIRED -> throw new AssertionError("No specialist expected");
                });

    OrchestrationOutcome outcome = orchestrator.handle("Generate a greeting query");

    assertThat(outcome).isInstanceOf(SpecialistOutcome.class);
    assertThat(((SpecialistOutcome) outcome).result().operation())
        .isEqualTo("query Greeting { greeting }");
    assertThat(calls).containsExactly("router", "generation");
  }

  @Test
  void routesTroubleshootingRequestsToOnlyTheTroubleshootingSpecialist() {
    AtomicInteger generationCalls = new AtomicInteger();
    AtomicInteger troubleshootingCalls = new AtomicInteger();
    AssistantOrchestrator orchestrator =
        orchestrator(
            prompt ->
                new RoutingDecision(RoutingIntent.TROUBLESHOOT, "Existing operation supplied", 0.9),
            (prompt, intent) -> {
              if (intent == RoutingIntent.GENERATE) {
                generationCalls.incrementAndGet();
              } else if (intent == RoutingIntent.TROUBLESHOOT) {
                troubleshootingCalls.incrementAndGet();
              }
              return new SpecialistResult("query Greeting { greeting }");
            });

    OrchestrationOutcome outcome = orchestrator.handle("Why does query { greeting } fail?");

    assertThat(outcome).isInstanceOf(SpecialistOutcome.class);
    assertThat(generationCalls).hasValue(0);
    assertThat(troubleshootingCalls).hasValue(1);
  }

  @Test
  void turnsLowConfidenceAndInsufficientRequestsIntoClarification() {
    AtomicInteger specialistCalls = new AtomicInteger();
    GenerationAgent specialist =
        prompt -> {
          specialistCalls.incrementAndGet();
          return new SpecialistResult("query Greeting { greeting }");
        };
    AssistantOrchestrator lowConfidence =
        orchestrator(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Uncertain", 0.4),
            (prompt, intent) -> specialist.generate(prompt));
    AssistantOrchestrator insufficient =
        orchestrator(
            prompt ->
                new RoutingDecision(
                    RoutingIntent.CLARIFICATION_REQUIRED, "Missing request details", 1.0),
            (prompt, intent) -> specialist.generate(prompt));

    assertThat(lowConfidence.handle("Maybe GraphQL")).isInstanceOf(ClarificationOutcome.class);
    assertThat(insufficient.handle("Help")).isInstanceOf(ClarificationOutcome.class);
    assertThat(specialistCalls).hasValue(0);
  }

  @Test
  void rejectsMalformedRouterOutputBeforeCallingASpecialist() {
    AtomicInteger specialistCalls = new AtomicInteger();
    GenerationAgent specialist =
        prompt -> {
          specialistCalls.incrementAndGet();
          return new SpecialistResult("query Greeting { greeting }");
        };
    AssistantOrchestrator orchestrator =
        orchestrator(prompt -> null, (prompt, intent) -> specialist.generate(prompt));

    assertThatThrownBy(() -> orchestrator.handle("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class)
        .hasMessage("Router returned an invalid decision");
    assertThat(specialistCalls).hasValue(0);
  }

  @Test
  void independentlyRejectsInvalidSpecialistOperations() {
    AssistantOrchestrator orchestrator =
        orchestrator(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 0.95),
            (prompt, intent) -> new SpecialistResult("query Greeting { missing }"));

    assertThatThrownBy(() -> orchestrator.handle("Generate a greeting query"))
        .isInstanceOf(InvalidAgentResponseException.class)
        .hasMessageContaining("specialist returned an invalid GraphQL operation");
  }

  @Test
  void boundsTheOverallWorkflowByTheConfiguredTimeout() {
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            prompt -> {
              try {
                Thread.sleep(Duration.ofSeconds(1));
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
              return new RoutingDecision(RoutingIntent.GENERATE, "Late", 1.0);
            },
            (prompt, intent) -> new SpecialistResult("query Greeting { greeting }"),
            tools,
            Duration.ofMillis(25),
            0.7);

    assertThatThrownBy(() -> orchestrator.handle("Generate a greeting query"))
        .isInstanceOf(AgentTimeoutException.class)
        .hasMessage("Assistant workflow exceeded its 25 ms timeout");
  }

  @Test
  void rejectsBlankRequestsWithoutCallingTheRouter() {
    AtomicInteger routerCalls = new AtomicInteger();
    AssistantOrchestrator orchestrator =
        orchestrator(
            prompt -> {
              routerCalls.incrementAndGet();
              return new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0);
            },
            (prompt, intent) -> new SpecialistResult("query Greeting { greeting }"));

    assertThatThrownBy(() -> orchestrator.handle(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("prompt must not be blank");
    assertThat(routerCalls).hasValue(0);
  }

  @Test
  void buildsAToollessTypedRouterWithLangChain4jAiServices() {
    AtomicReference<ChatRequest> request = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              request.set(chatRequest);
              return response(
                  """
              {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
              """);
            });

    RoutingDecision decision =
        LangChain4jAgentFactory.createRouter(model).route("Generate a greeting query");

    assertThat(decision.intent()).isEqualTo(RoutingIntent.GENERATE);
    assertThat(request.get().toolSpecifications()).isEmpty();
  }

  @Test
  void buildsAgenticSpecialistsWithOnlyTheApprovedGraphqlTools() {
    AtomicReference<ChatRequest> request = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              request.set(chatRequest);
              return response(generationResult("query Greeting { greeting }"));
            });

    SpecialistResult result =
        LangChain4jAgentFactory.createGenerationAgent(model, tools)
            .generate("Generate a greeting query");

    assertThat(result.operation()).isEqualTo("query Greeting { greeting }");
    assertThat(request.get().toolSpecifications())
        .extracting(specification -> specification.name())
        .containsExactlyInAnyOrder("inspectSchema", "validateOperation", "formatOperation");
    assertThat(request.get().messages().toString()).contains("multi-turn tool loop");
    assertThat(request.get().messages().toString()).contains("MUST use a declared variable");
    assertThat(request.get().messages().toString())
        .doesNotContain("You must answer strictly in the following JSON format");
  }

  @Test
  void requiresTroubleshootingPlaceholdersForMissingRuntimeValues() {
    AtomicReference<ChatRequest> request = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              request.set(chatRequest);
              return response(
                  """
                  {
                    "intent":"TROUBLESHOOT",
                    "issues":[{
                      "issue":"Unknown field.",
                      "details":"The field is not defined.",
                      "suggestion":"Use a defined field."
                    }],
                    "operation":"query Greeting { greeting }",
                    "variables":{}
                  }
                  """);
            });

    LangChain4jAgentFactory.createTroubleshootingAgent(model, tools)
        .troubleshoot("Troubleshoot a query");

    assertThat(request.get().messages().toString())
        .contains("\"variables\":{\"code\":\"<runtime value>\"}");
  }

  @Test
  void requiresGenerationPlaceholdersForMissingRuntimeValues() {
    AtomicReference<ChatRequest> request = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              request.set(chatRequest);
              return response(
                  generationResult(
                      "query GetCountry($code: ID!) { country(code: $code) { code name } }"));
            });

    LangChain4jAgentFactory.createGenerationAgent(model, tools)
        .generate("Generate a query to get a country based on code");

    assertThat(request.get().messages().toString())
        .contains("\"variables\":{\"code\":\"<runtime value>\"}");
  }

  @Test
  void usesTheAgenticConditionalWorkflowToSelectExactlyOneSpecialist() {
    AtomicInteger modelCalls = new AtomicInteger();
    AtomicReference<ChatRequest> request = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              modelCalls.incrementAndGet();
              request.set(chatRequest);
              return response(generationResult("query Greeting { greeting }"));
            });
    GenerationAgent generationAgent = LangChain4jAgentFactory.createGenerationAgent(model, tools);
    TroubleshootingAgent troubleshootingAgent =
        LangChain4jAgentFactory.createTroubleshootingAgent(model, tools);
    SpecialistWorkflow workflow =
        LangChain4jAgentFactory.createSpecialistWorkflow(generationAgent, troubleshootingAgent);

    SpecialistResult result = workflow.handle("Generate a greeting query", RoutingIntent.GENERATE);

    assertThat(result.operation()).isEqualTo("query Greeting { greeting }");
    assertThat(modelCalls).hasValue(1);
    assertThat(request.get().messages().toString())
        .contains("Generate exactly one named GraphQL query or mutation");
  }

  @Test
  void stopsAgenticToolLoopsWhenNoFinalResponseArrives() {
    AtomicInteger modelCalls = new AtomicInteger();
    ChatModel model =
        chatModel(
            chatRequest -> {
              int call = modelCalls.incrementAndGet();
              ToolExecutionRequest toolRequest =
                  ToolExecutionRequest.builder()
                      .id("inspect-" + call)
                      .name("inspectSchema")
                      .arguments("{\"typeNames\":[]}")
                      .build();
              return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest)).build();
            });
    GenerationAgent generationAgent = LangChain4jAgentFactory.createGenerationAgent(model, tools);

    assertThatThrownBy(() -> generationAgent.generate("Keep inspecting forever"))
        .isInstanceOf(RuntimeException.class)
        .rootCause()
        .hasMessageContaining("exceeded 5 tool calling round trips");
    assertThat(modelCalls).hasValue(6);
  }

  @Test
  void returnsPredictableErrorsForInvalidModelSelectedToolArguments() {
    AtomicInteger modelCalls = new AtomicInteger();
    AtomicReference<String> toolError = new AtomicReference<>();
    ChatModel model =
        chatModel(
            chatRequest -> {
              if (modelCalls.getAndIncrement() == 0) {
                ToolExecutionRequest toolRequest =
                    ToolExecutionRequest.builder()
                        .id("inspect-invalid")
                        .name("inspectSchema")
                        .arguments("{\"typeNames\":[\"not a GraphQL name\"]}")
                        .build();
                return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest)).build();
              }
              chatRequest.messages().stream()
                  .filter(ToolExecutionResultMessage.class::isInstance)
                  .map(ToolExecutionResultMessage.class::cast)
                  .map(ToolExecutionResultMessage::text)
                  .findFirst()
                  .ifPresent(toolError::set);
              return response(generationResult("query Greeting { greeting }"));
            });

    SpecialistResult result =
        LangChain4jAgentFactory.createGenerationAgent(model, tools)
            .generate("Generate a greeting query");

    assertThat(result.operation()).isEqualTo("query Greeting { greeting }");
    assertThat(toolError.get())
        .isEqualTo("TOOL_EXECUTION_FAILED: correct the input or return a final response");
  }

  private AssistantOrchestrator orchestrator(
      AssistantRouter router, SpecialistWorkflow specialistWorkflow) {
    SpecialistWorkflow schemaGroundedWorkflow =
        (prompt, intent) -> {
          if (intent == RoutingIntent.GENERATE) {
            tools.inspectSchema(List.of());
          }
          return specialistWorkflow.handle(prompt, intent);
        };
    return new AssistantOrchestrator(
        router, schemaGroundedWorkflow, tools, Duration.ofSeconds(1), 0.7);
  }

  private static ChatResponse response(String text) {
    return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
  }

  private static String generationResult(String operation) {
    return """
        {"intent":"GENERATE","operation":"%s","variables":{}}
        """
        .formatted(operation);
  }

  private static ChatModel chatModel(Function<ChatRequest, ChatResponse> response) {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest request) {
        return response.apply(request);
      }
    };
  }
}
