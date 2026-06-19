package com.example.graphqlassistant.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LangChain4jAssistantProviderTest {

  @Test
  void delegatesChatRequestsAndReturnsResponses() {
    ChatResponse expected =
        ChatResponse.builder().aiMessage(AiMessage.from("provider response")).build();
    ChatModel delegate =
        new ChatModel() {
          @Override
          public ChatResponse doChat(ChatRequest request) {
            return expected;
          }
        };
    AssistantAiProvider provider = new LangChain4jAssistantProvider("ollama", "qwen3:8b", delegate);

    ChatResponse actual = provider.chat(request());

    assertThat(actual).isSameAs(expected);
    assertThat(provider.providerName()).isEqualTo("ollama");
    assertThat(provider.modelName()).isEqualTo("qwen3:8b");
  }

  @Test
  void rejectsPerRequestModelOverrides() {
    AssistantAiProvider provider =
        new LangChain4jAssistantProvider("ollama", "qwen3:8b", unusedDelegate());
    ChatRequest request =
        ChatRequest.builder().messages(UserMessage.from("hello")).modelName("other-model").build();

    assertThatThrownBy(() -> provider.chat(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI request model override is not allowed");
  }

  @Test
  void translatesProviderFailuresWithoutRetainingSensitiveDetails(CapturedOutput output) {
    String apiKey = "sk-test-sensitive";
    ChatModel delegate =
        new ChatModel() {
          @Override
          public ChatResponse doChat(ChatRequest request) {
            throw new IllegalStateException("Provider rejected API key " + apiKey);
          }
        };
    AssistantAiProvider provider =
        new LangChain4jAssistantProvider("openai", "gpt-5.4-mini", delegate);

    assertThatThrownBy(() -> provider.chat(request()))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> {
              assertThat(exception)
                  .hasMessage("AI provider request failed for openai model gpt-5.4-mini")
                  .hasNoCause();
              assertThat(stackTrace(exception)).doesNotContain(apiKey);
            });
    assertThat(output).doesNotContain(apiKey);
  }

  private static ChatRequest request() {
    return ChatRequest.builder().messages(UserMessage.from("hello")).build();
  }

  private static ChatModel unusedDelegate() {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest request) {
        throw new AssertionError("Delegate should not be called");
      }
    };
  }

  private static String stackTrace(Throwable throwable) {
    StringWriter output = new StringWriter();
    throwable.printStackTrace(new PrintWriter(output));
    return output.toString();
  }
}
