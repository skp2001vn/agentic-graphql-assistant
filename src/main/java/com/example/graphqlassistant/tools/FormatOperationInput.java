package com.example.graphqlassistant.tools;

public record FormatOperationInput(String operation) {

  public FormatOperationInput {
    operation = ToolInputValidation.requireOperation(operation);
  }
}
