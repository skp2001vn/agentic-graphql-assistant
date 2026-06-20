package com.example.graphqlassistant.tools;

import java.util.List;

/**
 * Deterministic validation feedback used for AI self-correction.
 *
 * @param valid whether the operation satisfies syntax, schema, and assistant conventions
 * @param diagnostics immutable repair signals; empty exactly when {@code valid} is true
 */
public record OperationValidationResult(boolean valid, List<OperationDiagnostic> diagnostics) {

  /** Keeps the validity flag and diagnostic collection logically consistent. */
  public OperationValidationResult {
    diagnostics = List.copyOf(diagnostics);
    if (valid == !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("valid must match whether diagnostics are empty");
    }
  }
}
