package com.example.graphqlassistant.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.LangChain4jAgentFactory;
import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.provider.AiProviderException;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import graphql.schema.idl.SchemaParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GenerationServiceTest {

  private static final String SCHEMA =
      """
      type Query {
        countries: [Country!]!
        country(code: ID!): Country
      }

      type Country {
        code: ID!
        name: String!
      }
      """;

  @Test
  void generatesAValidatedListQueryAfterInspectingTheSchema() {
    List<ChatRequest> requests = new ArrayList<>();
    AssistantService service =
        service(
            request -> {
              requests.add(request);
              return switch (requests.size()) {
                case 1 ->
                    response(
                        """
                        {"intent":"GENERATE","reason":"New operation requested","confidence":0.99}
                        """);
                case 2 ->
                    ChatResponse.builder()
                        .aiMessage(
                            AiMessage.from(
                                ToolExecutionRequest.builder()
                                    .id("inspect-country")
                                    .name("inspectSchema")
                                    .arguments("{\"typeNames\":[\"Country\"]}")
                                    .build()))
                        .build();
                case 3 ->
                    response(
                        """
                        {
                          "intent":"GENERATE",
                          "operation":"query ListCountries{countries{code name}}",
                          "variables":{}
                        }
                        """);
                default -> throw new AssertionError("Unexpected model call");
              };
            });

    AssistantResponse response = service.assist("generate the query to get the list of country");

    assertThat(response).isInstanceOf(GenerateResponse.class);
    GenerateResponse result = (GenerateResponse) response;
    assertThat(result.intent()).isEqualTo("GENERATE");
    assertThat(result.query())
        .containsExactly(
            "query ListCountries {", "  countries {", "    code", "    name", "  }", "}");
    assertThat(result.variables()).isEmpty();
    assertThat(requests).hasSize(3);
    assertThat(requests.get(1).toolSpecifications())
        .extracting(specification -> specification.name())
        .containsExactlyInAnyOrder("inspectSchema", "validateOperation");
    assertThat(requests.get(2).messages()).anyMatch(ToolExecutionResultMessage.class::isInstance);
  }

  @Test
  void generatesAnArgumentBasedQueryWithVariables() {
    AssistantService service =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "operation":"query GetCountry($code:ID!){country(code:$code){code name}}",
                      "variables":{"code":"CA"}
                    }
                    """)));

    GenerateResponse result = (GenerateResponse) service.assist("Generate a query for country CA.");

    assertThat(result.query().getFirst()).isEqualTo("query GetCountry($code: ID!) {");
    assertThat(result.variables()).containsExactlyEntriesOf(Map.of("code", "CA"));
  }

  @Test
  void repairsAnArgumentVariableForNaturalCountryByCodeWording() {
    AssistantService service =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                toolCall(
                    "validate-literal",
                    "validateOperation",
                    """
                    {"operation":"query GetCountryByCode { country(code: \\"$code\\") { code name } }"}
                    """),
                toolCall(
                    "validate-repair",
                    "validateOperation",
                    """
                    {"operation":"query GetCountryByCode($code: ID!) { country(code: $code) { code name } }"}
                    """),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "issues":[],
                      "operation":"query GetCountryByCode($code: ID!) { country(code: $code) { code name } }",
                      "variables":{"code":"CA"}
                    }
                    """)));

    GenerateResponse result =
        (GenerateResponse) service.assist("generate the query to get the country by code");

    assertThat(result.query().getFirst()).isEqualTo("query GetCountryByCode($code: ID!) {");
    assertThat(result.variables()).containsExactlyEntriesOf(Map.of("code", "CA"));
  }

  @Test
  void rejectsGenerationResultsProducedWithoutSchemaInspection() {
    AssistantService service =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "operation":"query ListCountries { countries { code } }",
                      "variables":{}
                    }
                    """)));

    assertThatThrownBy(() -> service.assist("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void rejectsContradictoryAndSchemaInvalidModelResults() {
    AssistantService contradictory =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "operation":"query ListCountries { countries { code } }",
                      "variables":{}
                    }
                    """)));
    AssistantService invalid =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "operation":"query ListCountries { countries { missing } }",
                      "variables":{}
                    }
                    """)));

    assertThatThrownBy(() -> contradictory.assist("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class);
    assertThatThrownBy(() -> invalid.assist("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void rejectsIncompleteStructuredModelResults() {
    AssistantService service =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "operation":"query ListCountries { countries { code } }"
                    }
                    """)));

    assertThatThrownBy(() -> service.assist("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void rejectsOversizedSpecialistOperationsAsInvalidAiResponses() {
    String oversizedOperation = "query Oversized { countries { code } }" + " ".repeat(100 * 1024);
    AssistantService service =
        service(
            sequentialResponses(
                response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """),
                inspectCountrySchema(),
                response(
                    """
                    {
                      "intent":"GENERATE",
                      "operation":"%s",
                      "variables":{}
                    }
                    """
                        .formatted(oversizedOperation))));

    assertThatThrownBy(() -> service.assist("Generate a query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void preservesProviderFailuresForApiErrorMapping() {
    AssistantService service =
        service(
            request -> {
              throw new AiProviderException("ollama", "qwen3:8b");
            });

    assertThatThrownBy(() -> service.assist("Generate a query"))
        .isInstanceOf(AiProviderException.class);
  }

  @Test
  void unwrapsProviderFailuresFromTheGenerationAgent() {
    AtomicInteger calls = new AtomicInteger();
    AssistantService service =
        service(
            request -> {
              if (calls.getAndIncrement() == 0) {
                return response(
                    """
                    {"intent":"GENERATE","reason":"New operation requested","confidence":0.95}
                    """);
              }
              throw new AiProviderException("ollama", "qwen3:8b");
            });

    assertThatThrownBy(() -> service.assist("Generate a query"))
        .isInstanceOf(AiProviderException.class);
  }

  private AssistantService service(Function<ChatRequest, ChatResponse> responder) {
    GraphqlSchemaContext schemaContext =
        new GraphqlSchemaContext(SCHEMA, new SchemaParser().parse(SCHEMA));
    GraphqlAssistantTools tools = GraphqlAssistantTools.from(schemaContext);
    AssistantAiProvider provider = new FakeProvider(responder);
    var generationAgent = LangChain4jAgentFactory.createGenerationAgent(provider, tools);
    var troubleshootingAgent = LangChain4jAgentFactory.createTroubleshootingAgent(provider, tools);
    var workflow =
        LangChain4jAgentFactory.createSpecialistWorkflow(generationAgent, troubleshootingAgent);
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            LangChain4jAgentFactory.createRouter(provider),
            workflow,
            tools,
            Duration.ofSeconds(2),
            0.7);
    return new AssistantService(orchestrator, new GraphqlOperationProcessor(schemaContext));
  }

  private static Function<ChatRequest, ChatResponse> sequentialResponses(
      ChatResponse... responses) {
    AtomicInteger index = new AtomicInteger();
    return request -> responses[index.getAndIncrement()];
  }

  private static ChatResponse response(String text) {
    return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
  }

  private static ChatResponse inspectCountrySchema() {
    return ChatResponse.builder()
        .aiMessage(
            AiMessage.from(
                ToolExecutionRequest.builder()
                    .id("inspect-country")
                    .name("inspectSchema")
                    .arguments("{\"typeNames\":[\"Country\"]}")
                    .build()))
        .build();
  }

  private static ChatResponse toolCall(String id, String name, String arguments) {
    return ChatResponse.builder()
        .aiMessage(
            AiMessage.from(
                ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build()))
        .build();
  }

  private record FakeProvider(Function<ChatRequest, ChatResponse> responder)
      implements AssistantAiProvider {

    @Override
    public String providerName() {
      return "fake";
    }

    @Override
    public String modelName() {
      return "fake-model";
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
      return responder.apply(request);
    }
  }
}
