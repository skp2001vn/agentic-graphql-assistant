package com.example.graphqlassistant.evals;

import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.api.TroubleshootResponse;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class EvalScorer {

  private final GraphqlOperationProcessor operationProcessor;

  EvalScorer(GraphqlOperationProcessor operationProcessor) {
    this.operationProcessor = Objects.requireNonNull(operationProcessor, "operationProcessor");
  }

  EvalScore score(
      EvalExpectation expectation,
      AssistantResponse response,
      List<String> toolNames,
      Double judgeScore) {
    List<String> failures = new ArrayList<>();
    if (!expectation.intent().equals(response.intent())) {
      failures.add("intent did not match");
    }

    String operation;
    if (response instanceof GenerateResponse generated) {
      operation = String.join("\n", generated.query());
      if (!expectation.variables().equals(generated.variables())) {
        failures.add("variables did not match");
      }
      if (!expectation.expectedIssueTerms().isEmpty()) {
        failures.add("response contract did not include troubleshooting issues");
      }
    } else if (response instanceof TroubleshootResponse troubleshot) {
      operation = String.join("\n", troubleshot.correctedQuery());
      if (!expectation.variables().equals(troubleshot.variables())) {
        failures.add("variables did not match");
      }
      String issues = troubleshot.issues().toString().toLowerCase(Locale.ROOT);
      expectation.expectedIssueTerms().stream()
          .filter(term -> !issues.contains(term.toLowerCase(Locale.ROOT)))
          .forEach(term -> failures.add("missing expected issue term: " + term));
    } else {
      failures.add("unknown response contract");
      return new EvalScore(false, failures, judgeScore);
    }

    try {
      String normalized = operationProcessor.process(operation, expectation.variables());
      String comparableOperation = normalized.toLowerCase(Locale.ROOT);
      expectation.expectedOperationTerms().stream()
          .filter(term -> !comparableOperation.contains(term.toLowerCase(Locale.ROOT)))
          .forEach(term -> failures.add("missing expected operation term: " + term));
    } catch (RuntimeException exception) {
      failures.add("operation was not valid GraphQL");
    }

    expectation.requiredTools().stream()
        .filter(tool -> !toolNames.contains(tool))
        .forEach(tool -> failures.add("missing required tool: " + tool));
    return new EvalScore(failures.isEmpty(), failures, judgeScore);
  }
}
