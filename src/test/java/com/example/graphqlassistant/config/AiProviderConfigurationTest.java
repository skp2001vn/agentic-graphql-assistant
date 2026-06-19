package com.example.graphqlassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.graphqlassistant.provider.AssistantAiProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AiProviderConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AiProviderConfiguration.class);

  @Test
  void selectsOllamaWithLocalDefaults() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(AssistantAiProvider.class);
          assertThat(context).hasSingleBean(ChatModel.class);

          AssistantAiProvider provider = context.getBean(AssistantAiProvider.class);
          AssistantProperties properties = context.getBean(AssistantProperties.class);
          assertThat(provider.providerName()).isEqualTo("ollama");
          assertThat(provider.modelName()).isEqualTo("qwen3:8b");
          assertThat(provider.defaultRequestParameters().modelName()).isEqualTo("qwen3:8b");
          assertThat(provider.defaultRequestParameters().temperature()).isEqualTo(0.0);
          assertThat(provider.supportedCapabilities()).isEmpty();
          assertThat(properties.getAi().getOllama().getBaseUrl())
              .isEqualTo("http://localhost:11434");
        });
  }

  @Test
  void selectsOpenAiWhenConfiguredWithAnApiKey() {
    contextRunner
        .withPropertyValues(
            "assistant.ai.provider=openai", "assistant.ai.openai.api-key=test-secret")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AssistantAiProvider provider = context.getBean(AssistantAiProvider.class);
              assertThat(provider.providerName()).isEqualTo("openai");
              assertThat(provider.modelName()).isEqualTo("gpt-5-mini");
              assertThat(provider.defaultRequestParameters().modelName()).isEqualTo("gpt-5-mini");
            });
  }

  @Test
  void rejectsUnsupportedProvider() {
    contextRunner
        .withPropertyValues("assistant.ai.provider=unknown")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("assistant.ai.provider")
                    .hasMessageContaining("ollama or openai"));
  }

  @Test
  void rejectsOpenAiWithoutAnApiKey() {
    contextRunner
        .withPropertyValues("assistant.ai.provider=openai", "assistant.ai.openai.api-key= ")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessage("assistant.ai.openai.api-key must not be blank"));
  }

  @Test
  void rejectsMissingSelectedOllamaSettings() {
    contextRunner
        .withPropertyValues("assistant.ai.ollama.base-url= ")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("assistant.ai.ollama.base-url must not be blank"));
  }

  @Test
  void rejectsMissingSelectedOllamaModel() {
    contextRunner
        .withPropertyValues("assistant.ai.ollama.model= ")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessage("assistant.ai.ollama.model must not be blank"));
  }

  @Test
  void rejectsNonpositiveProviderTimeout() {
    contextRunner
        .withPropertyValues("assistant.ai.request-timeout=0s")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessage("assistant.ai.request-timeout must be positive"));
  }

  @Test
  void rejectsNonpositiveWarmResponseTarget() {
    contextRunner
        .withPropertyValues("assistant.ai.warm-response-target=0s")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessage("assistant.ai.warm-response-target must be positive"));
  }

  @Test
  void rejectsMissingSelectedOpenAiModelWithoutExposingTheKey(CapturedOutput output) {
    String apiKey = "sk-test-sensitive";

    contextRunner
        .withPropertyValues(
            "assistant.ai.provider=openai",
            "assistant.ai.openai.api-key=" + apiKey,
            "assistant.ai.openai.model= ")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("assistant.ai.openai.model must not be blank");
              assertThat(context.getStartupFailure()).hasMessageNotContaining(apiKey);
            });

    assertThat(output).doesNotContain(apiKey);
  }
}
