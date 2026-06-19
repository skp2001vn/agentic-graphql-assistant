package com.example.graphqlassistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.agent.AgentExecutionException;
import com.example.graphqlassistant.agent.AgentTimeoutException;
import com.example.graphqlassistant.agent.ClarificationRequiredException;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.provider.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssistantController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class AssistantFailureApiTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssistantService assistantService;

  @Test
  void returnsActionableClarificationWithoutPartialOutput() throws Exception {
    when(assistantService.assist("Help"))
        .thenThrow(
            new ClarificationRequiredException(
                "Specify what operation you want to generate or include the operation to troubleshoot."));

    postPrompt("Help")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("CLARIFICATION_REQUIRED"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Specify what operation you want to generate or include the operation to troubleshoot."))
        .andExpect(jsonPath("$.details").isEmpty())
        .andExpect(jsonPath("$.intent").doesNotExist())
        .andExpect(jsonPath("$.query").doesNotExist())
        .andExpect(jsonPath("$.correctedQuery").doesNotExist());
  }

  @Test
  void mapsProviderConnectionAndRejectionFailuresToBadGateway() throws Exception {
    when(assistantService.assist("Generate a query"))
        .thenThrow(new AiProviderException("ollama", "qwen3:8b"));

    postPrompt("Generate a query")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
  }

  @Test
  void mapsTheEndToEndTimeoutToProviderError() throws Exception {
    when(assistantService.assist("Generate slowly"))
        .thenThrow(new AgentTimeoutException("sensitive timeout detail"));

    postPrompt("Generate slowly")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"))
        .andExpect(content().string(not(containsString("sensitive timeout detail"))));
  }

  @Test
  void mapsInvalidStructuredOutputToBadGateway() throws Exception {
    when(assistantService.assist("Generate malformed output"))
        .thenThrow(new InvalidAgentResponseException("sensitive parser detail"));

    postPrompt("Generate malformed output")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("INVALID_AI_RESPONSE"))
        .andExpect(content().string(not(containsString("sensitive parser detail"))));
  }

  @Test
  void mapsToolAndLoopFailuresToControlledAgentErrors() throws Exception {
    when(assistantService.assist("Keep using tools"))
        .thenThrow(new AgentExecutionException("sensitive tool-loop detail"));

    postPrompt("Keep using tools")
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AGENT_EXECUTION_ERROR"))
        .andExpect(content().string(not(containsString("sensitive tool-loop detail"))));
  }

  private org.springframework.test.web.servlet.ResultActions postPrompt(String prompt)
      throws Exception {
    return mockMvc.perform(
        post("/assistant")
            .contentType(MediaType.TEXT_PLAIN)
            .accept(MediaType.APPLICATION_JSON)
            .content(prompt));
  }
}
