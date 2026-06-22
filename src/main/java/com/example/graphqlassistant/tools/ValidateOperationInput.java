package com.example.graphqlassistant.tools;

/**
 * Validated input for deterministic GraphQL operation analysis.
 *
 * @param operation nonblank model-generated operation within the assistant payload limit
 */
public record ValidateOperationInput(String operation) {

  private static final int MAX_OPERATION_LENGTH = 100 * 1024;

  /** Applies shared size and nonblank validation before syntax and schema analysis. */
  public ValidateOperationInput {
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("operation must not be blank");
    }
    if (operation.length() > MAX_OPERATION_LENGTH) {
      throw new IllegalArgumentException("operation must not exceed 100 KB");
    }
  }
}
