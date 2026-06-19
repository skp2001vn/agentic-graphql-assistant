package com.example.graphqlassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void publishesTheAssistantContractWithExamplesAndStandardErrors() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode document = objectMapper.readTree(body);
    JsonNode assistant = document.path("paths").path("/assistant").path("post");
    JsonNode responses = assistant.path("responses");

    assertThat(document.path("info").path("title").asString()).isEqualTo("GraphQL Assistant API");
    assertThat(assistant.path("requestBody").path("content").has("text/plain")).isTrue();
    assertThat(
            assistant
                .path("requestBody")
                .path("content")
                .path("text/plain")
                .path("examples")
                .path("generate")
                .path("value")
                .asString())
        .contains("Generate");
    assertThat(
            responses
                .path("200")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("oneOf")
                .toString())
        .contains("GenerateResponse", "TroubleshootResponse");
    assertThat(
            responses.path("200").path("content").path("application/json").path("examples").size())
        .isEqualTo(2);
    assertVariantSchema(
        document.path("components").path("schemas").path("GenerateResponse"),
        "GENERATE",
        Set.of("intent", "query", "variables"));
    assertVariantSchema(
        document.path("components").path("schemas").path("TroubleshootResponse"),
        "TROUBLESHOOT",
        Set.of("intent", "issues", "correctedQuery", "variables"));

    assertThat(responseCodes(responses))
        .containsExactlyInAnyOrder("200", "400", "413", "415", "422", "500", "502");
    for (String status : Set.of("400", "413", "415", "422", "500", "502")) {
      assertThat(
              responses
                  .path(status)
                  .path("content")
                  .path("application/json")
                  .path("schema")
                  .path("$ref")
                  .asString())
          .endsWith("/ApiError");
      assertThat(
              responses
                  .path(status)
                  .path("content")
                  .path("application/json")
                  .path("examples")
                  .isObject())
          .isTrue();
    }
    assertThat(
            responses.path("502").path("content").path("application/json").path("examples").size())
        .isEqualTo(3);
  }

  @Test
  void publishesSwaggerUi() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  private Set<String> responseCodes(JsonNode responses) {
    Set<String> codes = new java.util.HashSet<>();
    codes.addAll(responses.propertyNames());
    return codes;
  }

  private void assertVariantSchema(JsonNode schema, String intent, Set<String> requiredFields) {
    for (String field : requiredFields) {
      assertThat(schema.path("required").toString()).contains("\"" + field + "\"");
    }
    assertThat(schema.path("properties").path("intent").path("enum").toString()).contains(intent);
  }
}
