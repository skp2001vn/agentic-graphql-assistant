package com.example.graphqlassistant.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.LangChain4jAgentFactory;
import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.api.TroubleshootResponse;
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

class TroubleshootingServiceTest {

  private static final String SCHEMA =
      """
      type Query {
        countries: [Country!]!
        country(code: ID!): Country
      }

      type Country {
        code: ID!
        name: String!
        native: String!
        emoji: String!
        capital: String
        currency: String
        continent: Continent!
        languages: [Language!]!
      }

      type Continent {
        code: ID!
        name: String!
      }

      type Language {
        code: ID!
        name: String
      }
      """;

  @Test
  void returnsAllAiIdentifiedIssuesAndAValidatedCorrection() {
    List<ChatRequest> requests = new ArrayList<>();
    AssistantService service =
        service(
            request -> {
              requests.add(request);
              return switch (requests.size()) {
                case 1 ->
                    response(
                        """
                        {"intent":"TROUBLESHOOT","reason":"Existing operation supplied","confidence":0.99}
                        """);
                case 2 ->
                    toolCall("inspect-country", "inspectSchema", "{\"typeNames\":[\"Country\"]}");
                case 3 ->
                    toolCall(
                        "validate-correction",
                        "validateOperation",
                        """
                        {"operation":"query ListCountries { countries { code name } }"}
                        """);
                case 4 ->
                    toolCall(
                        "format-correction",
                        "formatOperation",
                        """
                        {"operation":"query ListCountries { countries { code name } }"}
                        """);
                case 5 ->
                    response(
                        """
                        {
                          "intent":"TROUBLESHOOT",
                          "issues":[
                            {
                              "issue":"Operation name must use PascalCase.",
                              "details":"The submitted operation is named listCountries.",
                              "suggestion":"Rename it to ListCountries."
                            },
                            {
                              "issue":"Unknown fields id and title on Country.",
                              "details":"The schema defines code and name instead.",
                              "suggestion":"Replace id and title with code and name."
                            }
                          ],
                          "operation":"query ListCountries{countries{code name}}",
                          "variables":{}
                        }
                        """);
                default -> throw new AssertionError("Unexpected model call");
              };
            });

    AssistantResponse response =
        service.assist(
            "Troubleshoot query listCountries { countries { id title } } and keep listing countries.");

    assertThat(response).isInstanceOf(TroubleshootResponse.class);
    TroubleshootResponse result = (TroubleshootResponse) response;
    assertThat(result.issues())
        .extracting(issue -> issue.issue())
        .containsExactly(
            "Operation name must use PascalCase.", "Unknown fields id and title on Country.");
    assertThat(result.correctedQuery())
        .containsExactly(
            "query ListCountries {", "  countries {", "    code", "    name", "  }", "}");
    assertThat(result.variables()).isEmpty();
    assertThat(requests).hasSize(5);
    assertThat(requests.get(1).toolSpecifications())
        .extracting(specification -> specification.name())
        .contains("inspectSchema", "validateOperation", "formatOperation");
    assertThat(toolResultText(requests.get(2))).contains("Country").doesNotContain("INVALID_");
    assertThat(toolResultText(requests.get(3)))
        .contains("valid", "true")
        .doesNotContain("INVALID_");
    assertThat(toolResultText(requests.get(4)))
        .contains("query ListCountries")
        .doesNotContain("INVALID_");
  }

  @Test
  void acceptsFinalResponseAfterFourTroubleshootingToolCalls() {
    AssistantService service =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                toolCall("inspect-country", "inspectSchema", "{\"typeNames\":[\"Country\"]}"),
                toolCall(
                    "validate-original",
                    "validateOperation",
                    """
                    {"operation":"query CountryQuery($code: ID!) { country(code) { code name1 } }"}
                    """),
                toolCall(
                    "validate-first-correction",
                    "validateOperation",
                    """
                    {"operation":"query CountryQuery($code: ID!) { country(code: $code) { code name1 } }"}
                    """),
                toolCall(
                    "validate-final-correction",
                    "validateOperation",
                    """
                    {"operation":"query CountryQuery($code: ID!) { country(code: $code) { code name } }"}
                    """),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[
                        {
                          "issue":"Missing variable reference in field argument.",
                          "details":"The country field does not pass the declared code variable.",
                          "suggestion":"Use country(code: $code)."
                        },
                        {
                          "issue":"Unknown field name1 on Country.",
                          "details":"The schema defines name instead.",
                          "suggestion":"Replace name1 with name."
                        }
                      ],
                      "operation":"query CountryQuery($code: ID!) { country(code: $code) { code name } }",
                      "variables":{"code":"<runtime value>"}
                    }
                    """)));

    TroubleshootResponse response =
        (TroubleshootResponse)
            service.assist("Debug query CountryQuery($code: ID!) { country(code) { code name1 } }");

    assertThat(response.issues()).hasSize(2);
    assertThat(response.correctedQuery())
        .contains("  country(code: $code) {", "    code", "    name");
    assertThat(response.variables()).containsEntry("code", "<runtime value>");
  }

  @Test
  void correctsMultipleInvalidFieldsAndNormalizesVariableSigils() {
    AssistantService service =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                toolCall(
                    "validate-original",
                    "validateOperation",
                    """
                    {"operation":"query CountryQuery($code: ID!) { country(code: $code) { code name1 native1234 } }"}
                    """),
                toolCall("inspect-country", "inspectSchema", "{\"typeNames\":[\"Country\"]}"),
                toolCall(
                    "validate-correction",
                    "validateOperation",
                    """
                    {"operation":"query CountryQuery($code: ID!) { country(code: $code) { code name native } }"}
                    """),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[
                        {
                          "issue":"Unknown field name1 on Country.",
                          "details":"The schema defines name instead.",
                          "suggestion":"Replace name1 with name."
                        },
                        {
                          "issue":"Unknown field native1234 on Country.",
                          "details":"The schema defines native instead.",
                          "suggestion":"Replace native1234 with native."
                        }
                      ],
                      "operation":"query CountryQuery($code: ID!) { country(code: $code) { code name native } }",
                      "variables":{"$code":"<runtime value>"}
                    }
                    """)));

    TroubleshootResponse response =
        (TroubleshootResponse)
            service.assist(
                """
                debug the below query:
                query CountryQuery($code: ID!) {
                  country(code: $code) {
                    code
                    name1
                    native1234
                  }
                }
                """);

    assertThat(response.issues()).hasSize(2);
    assertThat(response.correctedQuery()).contains("    name", "    native");
    assertThat(response.variables()).containsExactlyEntriesOf(Map.of("code", "<runtime value>"));
  }

  @Test
  void returnsNoIssuesForAValidMultilineOperation() {
    String operation =
        """
        query CountryQuery($code: ID!) {
          country(code: $code) {
            code
            name
            native
            emoji
            capital
            currency
            continent {
              code
              name
            }
            languages {
              code
              name
            }
          }
        }
        """;
    AssistantService service =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                toolCall(
                    "validate-original",
                    "validateOperation",
                    "{\"operation\":" + jsonString(operation) + "}"),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[],
                      "operation":%s,
                      "variables":{"code":"<runtime value>"}
                    }
                    """
                        .formatted(jsonString(operation)))));

    TroubleshootResponse response =
        (TroubleshootResponse) service.assist("debug the below query:\n" + operation);

    assertThat(response.issues()).isEmpty();
    assertThat(response.correctedQuery()).containsExactly(operation.strip().split("\\R"));
    assertThat(response.variables()).containsEntry("code", "<runtime value>");
  }

  @Test
  void rejectsMissingCorrections() {
    AssistantService missingCorrection =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[{
                        "issue":"Unknown field title.",
                        "details":"Country defines name.",
                        "suggestion":"Use name."
                      }],
                      "variables":{}
                    }
                    """)));

    assertThatThrownBy(() -> missingCorrection.assist("Troubleshoot this query"))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void rejectsIncompleteIssuesAndInvalidCorrectedOperations() {
    AssistantService incompleteIssue =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[{
                        "issue":"Unknown field title.",
                        "details":"Country defines name."
                      }],
                      "operation":"query ListCountries { countries { code name } }",
                      "variables":{}
                    }
                    """)));
    AssistantService invalidCorrection =
        service(
            sequentialResponses(
                troubleshootingRoute(),
                response(
                    """
                    {
                      "intent":"TROUBLESHOOT",
                      "issues":[{
                        "issue":"Unknown field title.",
                        "details":"Country defines name.",
                        "suggestion":"Use name."
                      }],
                      "operation":"query ListCountries { countries { missing } }",
                      "variables":{}
                    }
                    """)));

    assertThatThrownBy(() -> incompleteIssue.assist("Troubleshoot this query"))
        .isInstanceOf(InvalidAgentResponseException.class);
    assertThatThrownBy(() -> invalidCorrection.assist("Troubleshoot this query"))
        .isInstanceOf(InvalidAgentResponseException.class);
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

  private static ChatResponse troubleshootingRoute() {
    return response(
        """
        {"intent":"TROUBLESHOOT","reason":"Existing operation supplied","confidence":0.95}
        """);
  }

  private static ChatResponse response(String text) {
    return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
  }

  private static ChatResponse toolCall(String id, String name, String arguments) {
    return ChatResponse.builder()
        .aiMessage(
            AiMessage.from(
                ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build()))
        .build();
  }

  private static String toolResultText(ChatRequest request) {
    return request.messages().stream()
        .filter(ToolExecutionResultMessage.class::isInstance)
        .map(ToolExecutionResultMessage.class::cast)
        .map(ToolExecutionResultMessage::text)
        .reduce((previous, latest) -> latest)
        .orElseThrow();
  }

  private static String jsonString(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        + "\"";
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
