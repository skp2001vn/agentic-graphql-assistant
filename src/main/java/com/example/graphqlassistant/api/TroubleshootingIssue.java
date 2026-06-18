package com.example.graphqlassistant.api;

public record TroubleshootingIssue(String issue, String details, String suggestion) {

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
