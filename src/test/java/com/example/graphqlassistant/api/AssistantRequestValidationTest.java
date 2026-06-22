package com.example.graphqlassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.assistant.AssistantService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AssistantController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class AssistantRequestValidationTest {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AssistantService assistantService;

  @Test
  void rejectsBlankPrompts() throws Exception {
    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(" \n\t "))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)))
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.details").isEmpty());
  }

  @Test
  void rejectsBodiesLargerThanOneHundredKilobytes() throws Exception {
    byte[] oversizedPrompt = "a".repeat(100 * 1024 + 1).getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(oversizedPrompt))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.status").value(413))
        .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"))
        .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)));
  }

  @Test
  void rejectsUnsupportedContentTypes() throws Exception {
    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)));
  }

  @Test
  void rejectsMissingContentType() throws Exception {
    mockMvc
        .perform(post("/assistant").accept(MediaType.APPLICATION_JSON).content("generate a query"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void rejectsExplicitNonUtf8Text() throws Exception {
    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.parseMediaType("text/plain;charset=ISO-8859-1"))
                .accept(MediaType.APPLICATION_JSON)
                .content("generate a query"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void rejectsMalformedUtf8() throws Exception {
    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(new byte[] {(byte) 0xC3, (byte) 0x28}))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void serializesGenerateResponseContract() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("code", null);
    GenerateResponse response =
        new GenerateResponse(
            """
            query ListCountries {
              countries {
                code
              }
            }
            """
                .strip(),
            variables);

    JsonNode json = objectMapper.valueToTree(response);

    assertThat(json.path("intent").asString()).isEqualTo("GENERATE");
    assertThat(json.path("query").isArray()).isTrue();
    assertThat(json.path("query").path(0).asString()).isEqualTo("query ListCountries {");
    assertThat(json.path("query").path(1).asString()).isEqualTo("  countries {");
    assertThat(json.path("query").path(4).asString()).isEqualTo("}");
    assertThat(json.path("variables").path("code").isNull()).isTrue();
  }

  @Test
  void serializesTroubleshootResponseContract() {
    TroubleshootResponse response =
        new TroubleshootResponse(
            List.of(
                new TroubleshootingIssue(
                    "Unknown field", "The schema has no such field.", "Use name instead.")),
            """
            query ListCountries {
              countries {
                name
              }
            }
            """
                .strip(),
            Map.of());

    JsonNode json = objectMapper.valueToTree(response);

    assertThat(json.path("intent").asString()).isEqualTo("TROUBLESHOOT");
    assertThat(json.path("issues").path(0).path("issue").asString()).isEqualTo("Unknown field");
    assertThat(json.path("issues").path(0).path("details").asString())
        .isEqualTo("The schema has no such field.");
    assertThat(json.path("issues").path(0).path("suggestion").asString())
        .isEqualTo("Use name instead.");
    assertThat(json.path("correctedQuery").isArray()).isTrue();
    assertThat(json.path("correctedQuery").path(0).asString()).isEqualTo("query ListCountries {");
    assertThat(json.path("correctedQuery").path(1).asString()).isEqualTo("  countries {");
    assertThat(json.path("correctedQuery").path(4).asString()).isEqualTo("}");
    assertThat(json.path("variables").isObject()).isTrue();
  }
}
