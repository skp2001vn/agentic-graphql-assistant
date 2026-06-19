package com.example.graphqlassistant.evals;

import com.example.graphqlassistant.provider.AssistantAiProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class TranscriptEvalProvider implements AssistantAiProvider {

  private final List<EvalTranscriptStep> transcript;

  private final AtomicInteger index = new AtomicInteger();

  TranscriptEvalProvider(List<EvalTranscriptStep> transcript) {
    this.transcript = List.copyOf(transcript);
  }

  @Override
  public String providerName() {
    return "deterministic";
  }

  @Override
  public String modelName() {
    return "fixture-transcript";
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    int next = index.getAndIncrement();
    if (next >= transcript.size()) {
      throw new AssertionError("Evaluation transcript was exhausted");
    }
    EvalTranscriptStep step = transcript.get(next);
    AiMessage message =
        switch (step.kind()) {
          case "text" -> AiMessage.from(step.text());
          case "tool" ->
              AiMessage.from(
                  ToolExecutionRequest.builder()
                      .id(step.name() + "-" + next)
                      .name(step.name())
                      .arguments(step.arguments())
                      .build());
          default -> throw new AssertionError("Unknown transcript step: " + step.kind());
        };
    return ChatResponse.builder().aiMessage(message).build();
  }
}
