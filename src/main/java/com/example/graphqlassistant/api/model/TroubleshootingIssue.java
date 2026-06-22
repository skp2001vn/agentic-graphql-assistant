package com.example.graphqlassistant.api.model;

/**
 * Client-facing GraphQL diagnosis with explanation and actionable repair guidance.
 *
 * @param issue concise issue category
 * @param details schema- or validation-grounded explanation
 * @param suggestion actionable correction
 */
public record TroubleshootingIssue(String issue, String details, String suggestion) {

  /** Prevents incomplete model-generated diagnostics from entering the public API contract. */
  public TroubleshootingIssue {
    requireNonblank(issue, "issue");
    requireNonblank(details, "details");
    requireNonblank(suggestion, "suggestion");
  }

  private static void requireNonblank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Troubleshooting issue " + field + " must not be blank");
    }
  }
}
