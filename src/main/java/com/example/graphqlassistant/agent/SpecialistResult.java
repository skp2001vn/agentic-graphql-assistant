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
    variables = normalizeVariableNames(Objects.requireNonNull(variables, "variables"));
  }

  private static Map<String, Object> normalizeVariableNames(Map<String, Object> variables) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    variables.forEach(
        (name, value) -> {
          if (name == null) {
            throw new IllegalArgumentException("Variable name must not be null");
          }
          String normalizedName = name.startsWith("$") ? name.substring(1) : name;
          if (normalizedName.isBlank() || normalized.containsKey(normalizedName)) {
            throw new IllegalArgumentException("Variable names must be unique and nonblank");
          }
          normalized.put(normalizedName, value);
        });
    return Collections.unmodifiableMap(normalized);
  }
}
