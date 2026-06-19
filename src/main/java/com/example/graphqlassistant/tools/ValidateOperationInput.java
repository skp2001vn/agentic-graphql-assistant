package com.example.graphqlassistant.tools;

public record ValidateOperationInput(String operation) {

  public ValidateOperationInput {
    operation = ToolInputValidation.requireOperation(operation);
  }
}
