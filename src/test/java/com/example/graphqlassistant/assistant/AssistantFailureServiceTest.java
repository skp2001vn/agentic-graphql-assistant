package com.example.graphqlassistant.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.agent.AgentExecutionException;
import com.example.graphqlassistant.agent.AgentTimeoutException;
import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.ClarificationRequiredException;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.RoutingDecision;
import com.example.graphqlassistant.agent.RoutingIntent;
import com.example.graphqlassistant.agent.SpecialistResult;
import com.example.graphqlassistant.config.AssistantProperties;
import com.example.graphqlassistant.provider.AiProviderException;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import dev.langchain4j.service.output.OutputParsingException;
import graphql.schema.idl.SchemaParser;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssistantFailureServiceTest {

  private GraphqlSchemaContext schemaContext;

  private GraphqlAssistantTools tools;

  @BeforeEach
  void setUp() {
    String schema = "type Query { greeting: String! }";
    schemaContext = new GraphqlSchemaContext(schema, new SchemaParser().parse(schema));
    tools = GraphqlAssistantTools.from(schemaContext);
  }

  @Test
  void requestsClarificationForExplicitlyInsufficientPrompts() {
    AssistantService service =
        service(
            prompt ->
                new RoutingDecision(
                    RoutingIntent.CLARIFICATION_REQUIRED, "Missing request details", 1.0),
            (prompt, intent) -> {
              throw new AssertionError("No specialist expected");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Help"))
        .isInstanceOf(ClarificationRequiredException.class)
        .hasMessage(
            "Specify what operation you want to generate or include the operation to troubleshoot.");
  }

  @Test
  void requestsClarificationForLowConfidencePrompts() {
    AssistantService service =
        service(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Uncertain request", 0.4),
            (prompt, intent) -> {
              throw new AssertionError("No specialist expected");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Maybe GraphQL"))
        .isInstanceOf(ClarificationRequiredException.class);
  }

  @Test
  void preservesProviderFailuresFromSpecialists() {
    AssistantService service =
        service(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0),
            (prompt, intent) -> {
              throw new AiProviderException("ollama", "qwen3:8b");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(AiProviderException.class);
  }

  @Test
  void mapsStructuredOutputParsingFailuresToInvalidResponses() {
    AssistantService service =
        service(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0),
            (prompt, intent) -> {
              throw new OutputParsingException(
                  "invalid structured output", new IllegalArgumentException("malformed"));
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void preservesInvalidResponsesRaisedInsideTheSpecialistWorkflow() {
    AssistantService service =
        service(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0),
            (prompt, intent) -> {
              throw new InvalidAgentResponseException("invalid specialist output");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void wrapsUnexpectedRouterFailuresAsControlledAgentErrors() {
    AssistantService service =
        service(
            prompt -> {
              throw new IllegalStateException("sensitive router detail");
            },
            (prompt, intent) -> {
              throw new AssertionError("No specialist expected");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(AgentExecutionException.class)
        .hasMessage("Assistant agent execution failed");
  }

  @Test
  void preservesTheEndToEndTimeoutAsAControlledFailure() {
    AssistantService service =
        service(
            prompt -> {
              try {
                Thread.sleep(Duration.ofSeconds(1));
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
              return new RoutingDecision(RoutingIntent.GENERATE, "Late", 1.0);
            },
            (prompt, intent) -> {
              throw new AssertionError("No specialist expected");
            },
            Duration.ofMillis(25));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(AgentTimeoutException.class);
  }

  @Test
  void routerAndSpecialistShareOneEndToEndTimeoutBudget() {
    AssistantService service =
        service(
            prompt -> {
              sleep(Duration.ofMillis(100));
              return new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0);
            },
            (prompt, intent) -> {
              sleep(Duration.ofMillis(100));
              return new SpecialistResult("query Greeting { greeting }");
            },
            Duration.ofMillis(150));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(AgentTimeoutException.class);
  }

  @Test
  void defaultsToTheApprovedTimeoutAndWarmResponseTarget() {
    AssistantProperties.Ai properties = new AssistantProperties().getAi();

    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
    assertThat(properties.getWarmResponseTarget()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void wrapsToolAndAgentFailuresWithoutExposingTheirInternals() {
    AssistantService service =
        service(
            prompt -> new RoutingDecision(RoutingIntent.GENERATE, "Clear request", 1.0),
            (prompt, intent) -> {
              throw new IllegalStateException("sensitive tool-loop detail");
            },
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> service.assist("Generate a greeting query"))
        .isInstanceOf(AgentExecutionException.class)
        .hasMessage("Assistant agent execution failed");
  }

  private AssistantService service(
      com.example.graphqlassistant.agent.AssistantRouter router,
      com.example.graphqlassistant.agent.SpecialistWorkflow workflow,
      Duration timeout) {
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(router, workflow, tools, timeout, 0.7);
    return new AssistantService(orchestrator, new GraphqlOperationProcessor(schemaContext));
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
