package com.example.graphqlassistant.config;

import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.provider.LangChain4jAssistantProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
public class AiProviderConfiguration {

  @Bean
  AssistantAiProvider assistantAiProvider(AssistantProperties properties) {
    AssistantProperties.Ai ai = properties.getAi();
    Duration timeout = requirePositive(ai.getRequestTimeout(), "assistant.ai.request-timeout");
    String provider =
        requireNonBlank(ai.getProvider(), "assistant.ai.provider").toLowerCase(Locale.ROOT);

    return switch (provider) {
      case "ollama" -> createOllamaProvider(ai.getOllama(), timeout);
      case "openai" -> createOpenAiProvider(ai.getOpenai(), timeout);
      default -> throw new IllegalStateException("assistant.ai.provider must be ollama or openai");
    };
  }

  private AssistantAiProvider createOllamaProvider(
      AssistantProperties.Ollama properties, Duration timeout) {
    String baseUrl = requireNonBlank(properties.getBaseUrl(), "assistant.ai.ollama.base-url");
    String model = requireNonBlank(properties.getModel(), "assistant.ai.ollama.model");
    ChatModel chatModel =
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(model)
            .timeout(timeout)
            .maxRetries(0)
            .logRequests(false)
            .logResponses(false)
            .build();
    return new LangChain4jAssistantProvider("ollama", model, chatModel);
  }

  private AssistantAiProvider createOpenAiProvider(
      AssistantProperties.OpenAi properties, Duration timeout) {
    String apiKey = requireNonBlank(properties.getApiKey(), "assistant.ai.openai.api-key");
    String model = requireNonBlank(properties.getModel(), "assistant.ai.openai.model");
    ChatModel chatModel =
        OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)
            .timeout(timeout)
            .maxRetries(0)
            .logRequests(false)
            .logResponses(false)
            .build();
    return new LangChain4jAssistantProvider("openai", model, chatModel);
  }

  private static String requireNonBlank(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(propertyName + " must not be blank");
    }
    return value.trim();
  }

  private static Duration requirePositive(Duration value, String propertyName) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException(propertyName + " must be positive");
    }
    return value;
  }
}
