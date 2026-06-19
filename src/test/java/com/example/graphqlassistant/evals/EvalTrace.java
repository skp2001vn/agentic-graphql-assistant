package com.example.graphqlassistant.evals;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EvalTrace {

  private final List<EvalToolTrace> tools = new ArrayList<>();

  private final Map<String, Integer> toolIndexesById = new LinkedHashMap<>();

  private final List<String> rawResponses = new ArrayList<>();

  void captureRequest(ChatRequest request) {
    request.messages().stream()
        .filter(ToolExecutionResultMessage.class::isInstance)
        .map(ToolExecutionResultMessage.class::cast)
        .forEach(this::captureToolOutput);
  }

  void captureResponse(ChatResponse response) {
    rawResponses.add(String.valueOf(response.aiMessage()));
    for (ToolExecutionRequest request : response.aiMessage().toolExecutionRequests()) {
      if (!toolIndexesById.containsKey(request.id())) {
        toolIndexesById.put(request.id(), tools.size());
        tools.add(new EvalToolTrace(request.name(), request.arguments(), null));
      }
    }
  }

  List<EvalToolTrace> tools() {
    return List.copyOf(tools);
  }

  List<String> toolNames() {
    return tools.stream().map(EvalToolTrace::name).toList();
  }

  List<String> rawResponses() {
    return List.copyOf(rawResponses);
  }

  private void captureToolOutput(ToolExecutionResultMessage result) {
    Integer index = toolIndexesById.get(result.id());
    if (index == null) {
      return;
    }
    EvalToolTrace existing = tools.get(index);
    tools.set(index, new EvalToolTrace(existing.name(), existing.arguments(), result.text()));
  }
}
