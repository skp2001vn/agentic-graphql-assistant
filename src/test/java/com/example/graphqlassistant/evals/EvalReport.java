package com.example.graphqlassistant.evals;

import java.util.List;

record EvalReport(
    String mode,
    String provider,
    String model,
    int totalCases,
    int passedCases,
    double passRate,
    long warmupLatencyMillis,
    boolean latencyTargetMet,
    List<EvalReportCase> cases) {

  EvalReport {
    cases = List.copyOf(cases);
  }
}
