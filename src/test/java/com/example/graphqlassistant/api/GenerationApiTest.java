package com.example.graphqlassistant.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.provider.AiProviderException;
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
class GenerationApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssistantService assistantService;

  @Test
  void returnsGeneratedOperations() throws Exception {
    when(assistantService.assist("Generate country CA"))
        .thenReturn(
            new GenerateResponse(
                "query GetCountry($code: ID!) {\n"
                    + "  country(code: $code) {\n"
                    + "    name\n"
                    + "  }\n"
                    + "}",
                Map.of("code", "CA")));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("Generate country CA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intent").value("GENERATE"))
        .andExpect(jsonPath("$.query").isArray())
        .andExpect(jsonPath("$.query[0]").value("query GetCountry($code: ID!) {"))
        .andExpect(jsonPath("$.query[1]").value("  country(code: $code) {"))
        .andExpect(jsonPath("$.variables.code").value("CA"));
  }

  @Test
  void mapsInvalidModelResultsToBadGateway() throws Exception {
    when(assistantService.assist("Generate a query"))
        .thenThrow(new InvalidAgentResponseException("invalid"));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("Generate a query"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"));
  }

  @Test
  void mapsProviderFailuresToBadGateway() throws Exception {
    when(assistantService.assist("Generate a query"))
        .thenThrow(new AiProviderException("ollama", "qwen3:8b"));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("Generate a query"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
  }
}
