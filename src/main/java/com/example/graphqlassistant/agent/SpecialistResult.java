package com.example.graphqlassistant.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral structured output shared by generation and troubleshooting specialists.
 *
 * <p>The record is the anti-corruption boundary between probabilistic LLM JSON and deterministic
 * application logic. It normalizes GraphQL variable names and prevents clarification intents or
 * blank operations from entering response processing.
 *
 * @param intent selected generation or troubleshooting intent
 * @param issues model-produced diagnoses; empty for generation
 * @param operation generated or corrected GraphQL operation
 * @param variables runtime values keyed without the GraphQL {@code $} prefix
 */
public record SpecialistResult(
    RoutingIntent intent,
    List<SpecialistIssue> issues,
    String operation,
    Map<String, Object> variables) {

  /**
   * Creates a generation result without diagnostics or variables.
   *
   * @param operation generated GraphQL operation
   */
  public SpecialistResult(String operation) {
    this(RoutingIntent.GENERATE, List.of(), operation, Map.of());
  }

  /**
   * Creates a specialist result without issue diagnostics.
   *
   * @param intent selected generation or troubleshooting intent
   * @param operation generated or corrected GraphQL operation
   * @param variables runtime variable examples keyed without the GraphQL {@code $} prefix
   */
  public SpecialistResult(RoutingIntent intent, String operation, Map<String, Object> variables) {
    this(intent, List.of(), operation, variables);
  }

  /** Validates structured model output and creates immutable issue and variable collections. */
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
