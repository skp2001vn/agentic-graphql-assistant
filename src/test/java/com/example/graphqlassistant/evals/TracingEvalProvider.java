package com.example.graphqlassistant.evals;

import com.example.graphqlassistant.provider.AssistantAiProvider;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class TracingEvalProvider implements AssistantAiProvider {

  private final AssistantAiProvider delegate;

  private final EvalTrace trace;

  TracingEvalProvider(AssistantAiProvider delegate, EvalTrace trace) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.trace = Objects.requireNonNull(trace, "trace");
  }

  @Override
  public String providerName() {
    return delegate.providerName();
  }

  @Override
  public String modelName() {
    return delegate.modelName();
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    trace.captureRequest(request);
    ChatResponse response = delegate.doChat(request);
    trace.captureResponse(response);
    return response;
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
