package com.example.graphqlassistant.tools;

import java.util.List;

public record OperationValidationResult(boolean valid, List<OperationDiagnostic> diagnostics) {

  public OperationValidationResult {
    diagnostics = List.copyOf(diagnostics);
    if (valid == !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("valid must match whether diagnostics are empty");
    }
  }
}
