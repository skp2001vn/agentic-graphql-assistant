package com.example.graphqlassistant.evals;

import com.example.graphqlassistant.api.AssistantResponse;
import java.util.List;

record EvalReportCase(
    String id,
    String category,
    boolean passed,
    long latencyMillis,
    List<String> hardFailures,
    AssistantResponse output,
    List<EvalToolTrace> toolTrace,
    List<String> rawResponses,
    Double judgeScore) {

  EvalReportCase {
    hardFailures = List.copyOf(hardFailures);
    toolTrace = List.copyOf(toolTrace);
    rawResponses = List.copyOf(rawResponses);
  }
}
