package com.example.graphqlassistant.tools;

/**
 * Validated input for deterministic GraphQL operation analysis.
 *
 * @param operation nonblank model-generated operation within the assistant payload limit
 */
public record ValidateOperationInput(String operation) {

  /** Applies shared size and nonblank validation before syntax and schema analysis. */
  public ValidateOperationInput {
    operation = ToolInputValidation.requireOperation(operation);
  }
}
