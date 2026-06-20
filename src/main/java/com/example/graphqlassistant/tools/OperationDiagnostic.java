package com.example.graphqlassistant.tools;

import java.util.List;

/**
 * Structured syntax, schema, or assistant-contract diagnostic returned to an AI tool loop.
 *
 * @param code machine-readable validation category
 * @param message repair-oriented explanation
 * @param line one-based source line when available
 * @param column one-based source column when available
 * @param path GraphQL query path associated with the issue
 */
public record OperationDiagnostic(
    String code, String message, int line, int column, List<String> path) {

  /** Copies the GraphQL path so diagnostics remain immutable between model turns. */
  public OperationDiagnostic {
    path = List.copyOf(path);
  }
}
