package com.example.graphqlassistant.evals;

import java.util.List;
import java.util.Map;

record EvalExpectation(
    String intent,
    Map<String, Object> variables,
    List<String> expectedIssueTerms,
    List<String> requiredTools,
    List<String> expectedOperationTerms) {

  EvalExpectation {
    variables = Map.copyOf(variables);
    expectedIssueTerms = List.copyOf(expectedIssueTerms);
    requiredTools = List.copyOf(requiredTools);
    expectedOperationTerms = List.copyOf(expectedOperationTerms);
  }
}
