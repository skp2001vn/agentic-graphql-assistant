package com.example.graphqlassistant.evals;

import java.util.List;

record EvalScore(boolean passed, List<String> hardFailures, Double judgeScore) {

  EvalScore {
    hardFailures = List.copyOf(hardFailures);
    passed = hardFailures.isEmpty();
  }
}
