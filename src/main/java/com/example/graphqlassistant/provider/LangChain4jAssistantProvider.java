package com.example.graphqlassistant.provider;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LangChain4jAssistantProvider implements AssistantAiProvider {

  private final String providerName;

  private final String modelName;

  private final ChatModel delegate;

  public LangChain4jAssistantProvider(String providerName, String modelName, ChatModel delegate) {
    this.providerName = Objects.requireNonNull(providerName);
    this.modelName = Objects.requireNonNull(modelName);
    this.delegate = Objects.requireNonNull(delegate);
  }

  @Override
  public String providerName() {
    return providerName;
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    if (request.modelName() != null && !modelName.equals(request.modelName())) {
      throw new IllegalArgumentException("AI request model override is not allowed");
    }

    try {
      return delegate.doChat(request);
    } catch (RuntimeException exception) {
      throw new AiProviderException(providerName, modelName);
    }
  }

  @Override
  public ChatRequestParameters defaultRequestParameters() {
    return delegate.defaultRequestParameters();
  }

  @Override
  public List<ChatModelListener> listeners() {
    return delegate.listeners();
  }

  @Override
  public ModelProvider provider() {
    return delegate.provider();
  }

  @Override
  public Set<Capability> supportedCapabilities() {
    return delegate.supportedCapabilities();
  }
}
