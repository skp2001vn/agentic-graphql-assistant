package com.example.graphqlassistant.evals;

import java.util.List;
import java.util.Map;

record EvalCase(
    String id,
    String category,
    String prompt,
    String expectedIntent,
    Map<String, Object> expectedVariables,
    List<String> expectedIssueTerms,
    List<String> requiredTools,
    List<String> expectedOperationTerms,
    String expectedError,
    List<EvalTranscriptStep> transcript) {

  EvalCase {
    expectedVariables = expectedVariables == null ? Map.of() : Map.copyOf(expectedVariables);
    expectedIssueTerms = expectedIssueTerms == null ? List.of() : List.copyOf(expectedIssueTerms);
    requiredTools = requiredTools == null ? List.of() : List.copyOf(requiredTools);
    expectedOperationTerms =
        expectedOperationTerms == null ? List.of() : List.copyOf(expectedOperationTerms);
    transcript = transcript == null ? List.of() : List.copyOf(transcript);
  }

  EvalExpectation expectation() {
    return new EvalExpectation(
        expectedIntent,
        expectedVariables,
        expectedIssueTerms,
        requiredTools,
        expectedOperationTerms);
  }
}
