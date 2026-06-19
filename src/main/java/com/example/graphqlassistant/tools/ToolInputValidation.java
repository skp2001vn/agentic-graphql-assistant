package com.example.graphqlassistant.tools;

final class ToolInputValidation {

  private static final int MAX_OPERATION_LENGTH = 100 * 1024;

  private ToolInputValidation() {
    throw new AssertionError("No instances");
  }

  static String requireOperation(String operation) {
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("operation must not be blank");
    }
    if (operation.length() > MAX_OPERATION_LENGTH) {
      throw new IllegalArgumentException("operation must not exceed 100 KB");
    }
    return operation;
  }
}
