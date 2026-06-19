package com.example.graphqlassistant.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SpecialistResult(
    RoutingIntent intent,
    List<SpecialistIssue> issues,
    String operation,
    Map<String, Object> variables) {

  public SpecialistResult(String operation) {
    this(RoutingIntent.GENERATE, List.of(), operation, Map.of());
  }

  public SpecialistResult(RoutingIntent intent, String operation, Map<String, Object> variables) {
    this(intent, List.of(), operation, variables);
  }

  public SpecialistResult {
    Objects.requireNonNull(intent, "Specialist intent must not be null");
    if (intent == RoutingIntent.CLARIFICATION_REQUIRED) {
      throw new IllegalArgumentException("Specialist intent must select a specialist");
    }
    issues = issues == null ? List.of() : List.copyOf(issues);
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("Specialist operation must not be blank");
    }
    variables =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(variables, "variables")));
  }
}
