package com.example.graphqlassistant.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.provider.LangChain4jAssistantProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "assistant.ai.provider=openai",
      "assistant.ai.openai.api-key= sk-test-must-not-appear ",
      "assistant.logging.full-content-enabled=true"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(AssistantLoggingTest.ProviderConfiguration.class)
class AssistantLoggingTest {

  private static final String REQUEST_ID = "task-9-request";

  private static final String PROMPT =
      "Generate a query that lists country codes and names. sk-test-must-not-appear";

  @Autowired private MockMvc mockMvc;

  @Autowired private TestChatModel chatModel;

  @BeforeEach
  void resetChatModel() {
    chatModel.reset();
  }

  @Test
  void logsFullAgentAndToolContentWithoutCredentials(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            post("/assistant")
                .header("X-Request-ID", REQUEST_ID)
                .header("Authorization", "Bearer authorization-must-not-appear")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(PROMPT))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-ID", REQUEST_ID));

    assertThat(output)
        .contains(
            "\"event\":\"assistant_request_started\"",
            "\"requestId\":\"" + REQUEST_ID + "\"",
            "\"provider\":\"openai\"",
            "\"model\":\"gpt-5.4-mini\"",
            "\"selectedAgent\":\"GENERATION\"",
            "\"schema\":",
            "type Query",
            "\"prompt\":\"Generate a query that lists country codes and names. [REDACTED]\"",
            "\"event\":\"assistant_ai_response\"",
            "\"rawAiResponse\":",
            "response-id",
            "private reasoning [REDACTED]",
            "query ListCountries",
            "\"event\":\"assistant_tool_completed\"",
            "\"toolName\":\"inspectSchema\"",
            "\"toolInput\":",
            "Country",
            "\"toolOutput\":",
            "\"event\":\"assistant_response_normalized\"",
            "\"normalizedResponse\":",
            "\"event\":\"assistant_request_completed\"",
            "\"status\":200",
            "\"errorCategory\":\"NONE\"",
            "\"latencyMs\":")
        .doesNotContain("sk-test-must-not-appear", "authorization-must-not-appear");
  }

  @Test
  void doesNotLabelHealthTrafficAsAssistantRequests(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());

    assertThat(output).doesNotContain("\"event\":\"assistant_request_completed\"");
  }

  @Test
  void returnsAndLogsTheRequestIdForFailuresWithoutCredentialDetails(CapturedOutput output)
      throws Exception {
    chatModel.failWithCredentialDetail();

    mockMvc
        .perform(
            post("/assistant")
                .header("X-Request-ID", REQUEST_ID)
                .header("Authorization", "Bearer authorization-must-not-appear")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content(PROMPT))
        .andExpect(status().isBadGateway())
        .andExpect(header().string("X-Request-ID", REQUEST_ID))
        .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
        .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));

    assertThat(output)
        .contains(
            "\"event\":\"assistant_request_started\"",
            "\"event\":\"assistant_request_completed\"",
            "\"requestId\":\"" + REQUEST_ID + "\"",
            "\"status\":502",
            "\"errorCategory\":\"AI_PROVIDER_ERROR\"")
        .doesNotContain(
            "sk-test-must-not-appear",
            "authorization-must-not-appear",
            "provider credential detail");
  }

  private static ChatResponse response(String text) {
    return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProviderConfiguration {

    @Bean
    @Primary
    AssistantAiProvider testAssistantAiProvider(
        AssistantRequestLogger requestLogger, TestChatModel delegate) {
      return new LangChain4jAssistantProvider("openai", "gpt-5.4-mini", delegate, requestLogger);
    }

    @Bean
    TestChatModel testChatModel() {
      return new TestChatModel();
    }
  }

  static final class TestChatModel implements ChatModel {

    private final AtomicInteger calls = new AtomicInteger();

    private volatile boolean fail;

    void reset() {
      calls.set(0);
      fail = false;
    }

    void failWithCredentialDetail() {
      fail = true;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
      if (fail) {
        throw new IllegalStateException("provider credential detail sk-test-must-not-appear");
      }
      return switch (calls.getAndIncrement()) {
        case 0 ->
            ChatResponse.builder()
                .id("response-id")
                .modelName("gpt-5.4-mini")
                .aiMessage(
                    AiMessage.builder()
                        .text(
                            """
                            {"intent":"GENERATE","reason":"New operation requested","confidence":0.99}
                            """)
                        .thinking("private reasoning sk-test-must-not-appear")
                        .build())
                .build();
        case 1 ->
            ChatResponse.builder()
                .aiMessage(
                    AiMessage.from(
                        ToolExecutionRequest.builder()
                            .id("inspect-country")
                            .name("inspectSchema")
                            .arguments("{\"typeNames\":[\"Country\"]}")
                            .build()))
                .build();
        case 2 ->
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
    }
  }
}
