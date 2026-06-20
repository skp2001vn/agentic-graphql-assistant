package com.example.graphqlassistant.provider;

import com.example.graphqlassistant.logging.AssistantRequestLogger;
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

/**
 * LangChain4j chat-model adapter that enforces model pinning and assistant-domain failures.
 *
 * <p>The adapter prevents per-request model overrides, records inference telemetry, and translates
 * provider SDK exceptions into a stable boundary understood by the API layer.
 */
public final class LangChain4jAssistantProvider implements AssistantAiProvider {

  private final String providerName;

  private final String modelName;

  private final ChatModel delegate;

  private final AssistantRequestLogger requestLogger;

  /**
   * Creates a provider adapter with AI response logging disabled.
   *
   * @param providerName inference provider identifier
   * @param modelName pinned model identifier
   * @param delegate configured LangChain4j chat model
   */
  public LangChain4jAssistantProvider(String providerName, String modelName, ChatModel delegate) {
    this(providerName, modelName, delegate, AssistantRequestLogger.disabled());
  }

  /**
   * Creates a provider adapter with request-scoped inference observability.
   *
   * @param providerName inference provider identifier
   * @param modelName pinned model identifier
   * @param delegate configured LangChain4j chat model
   * @param requestLogger structured AI lifecycle logger
   */
  public LangChain4jAssistantProvider(
      String providerName,
      String modelName,
      ChatModel delegate,
      AssistantRequestLogger requestLogger) {
    this.providerName = Objects.requireNonNull(providerName);
    this.modelName = Objects.requireNonNull(modelName);
    this.delegate = Objects.requireNonNull(delegate);
    this.requestLogger = Objects.requireNonNull(requestLogger);
  }

  /** {@inheritDoc} */
  @Override
  public String providerName() {
    return providerName;
  }

  /** {@inheritDoc} */
  @Override
  public String modelName() {
    return modelName;
  }

  /**
   * Executes one pinned-model inference request and translates provider failures.
   *
   * @param request LangChain4j chat request from a router or specialist agent
   * @return provider response for the agent framework
   */
  @Override
  public ChatResponse doChat(ChatRequest request) {
    if (request.modelName() != null && !modelName.equals(request.modelName())) {
      throw new IllegalArgumentException("AI request model override is not allowed");
    }

    try {
      ChatResponse response = delegate.doChat(request);
      requestLogger.aiResponse(providerName, modelName, response);
      return response;
    } catch (RuntimeException exception) {
      throw new AiProviderException(providerName, modelName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ChatRequestParameters defaultRequestParameters() {
    return delegate.defaultRequestParameters();
  }

  /** {@inheritDoc} */
  @Override
  public List<ChatModelListener> listeners() {
    return delegate.listeners();
  }

  /** {@inheritDoc} */
  @Override
  public ModelProvider provider() {
    return delegate.provider();
  }

  /** {@inheritDoc} */
  @Override
  public Set<Capability> supportedCapabilities() {
    return delegate.supportedCapabilities();
  }
}
