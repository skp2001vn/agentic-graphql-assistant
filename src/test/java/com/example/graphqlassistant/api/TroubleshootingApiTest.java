package com.example.graphqlassistant.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.assistant.AssistantService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssistantController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class TroubleshootingApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssistantService assistantService;

  @Test
  void returnsTroubleshootingIssuesAndTheCorrectedOperation() throws Exception {
    String prompt = "Why does query ListCountries { countries { title } } fail?";
    when(assistantService.assist(prompt))
        .thenReturn(
            new TroubleshootResponse(
                List.of(
                    new TroubleshootingIssue(
                        "Unknown field title.",
                        "Country defines name rather than title.",
                        "Replace title with name.")),
                "query ListCountries {\n  countries {\n    name\n  }\n}",
                Map.of()));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(prompt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("TROUBLESHOOT"))
        .andExpect(jsonPath("$.issues[0].issue").value("Unknown field title."))
        .andExpect(jsonPath("$.issues[0].details").value("Country defines name rather than title."))
        .andExpect(jsonPath("$.issues[0].suggestion").value("Replace title with name."))
        .andExpect(jsonPath("$.correctedQuery").isArray())
        .andExpect(jsonPath("$.correctedQuery[0]").value("query ListCountries {"))
        .andExpect(jsonPath("$.correctedQuery[2]").value("    name"))
        .andExpect(jsonPath("$.variables").isMap());
  }

  @Test
  void returnsAnEmptyIssueListForAValidMultilineOperation() throws Exception {
    String prompt =
        """
        debug the below query:
        query CountryQuery($code: ID!) {
          country(code: $code) {
            code
            name
          }
        }
        """;
    when(assistantService.assist(prompt))
        .thenReturn(
            new TroubleshootResponse(
                List.of(),
                """
                query CountryQuery($code: ID!) {
                  country(code: $code) {
                    code
                    name
                  }
                }
                """,
                Map.of("code", "<runtime value>")));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(prompt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("TROUBLESHOOT"))
        .andExpect(jsonPath("$.issues").isEmpty())
        .andExpect(jsonPath("$.correctedQuery").isArray())
        .andExpect(jsonPath("$.correctedQuery[0]").value("query CountryQuery($code: ID!) {"))
        .andExpect(jsonPath("$.variables.code").value("<runtime value>"));
  }

  @Test
  void mapsInvalidTroubleshootingResultsToBadGateway() throws Exception {
    String prompt = "Troubleshoot this query";
    when(assistantService.assist(prompt)).thenThrow(new InvalidAgentResponseException("invalid"));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(prompt))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"));
  }
}
