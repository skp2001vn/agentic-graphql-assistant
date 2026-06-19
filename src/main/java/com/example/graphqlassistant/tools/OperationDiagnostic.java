package com.example.graphqlassistant.tools;

import java.util.List;

public record OperationDiagnostic(
    String code, String message, int line, int column, List<String> path) {

  public OperationDiagnostic {
    path = List.copyOf(path);
  }
}
