package com.example.graphqlassistant.tools;

/**
 * Validated input for canonical GraphQL AST formatting.
 *
 * @param operation nonblank operation within the assistant payload limit
 */
public record FormatOperationInput(String operation) {

  /** Applies shared size and nonblank validation before parsing model-generated GraphQL. */
  public FormatOperationInput {
    operation = ToolInputValidation.requireOperation(operation);
  }
}
