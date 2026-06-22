package com.example.graphqlassistant.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Stable, request-correlated error envelope returned by the assistant HTTP API.
 *
 * @param timestamp time at which the error response was created
 * @param requestId correlation identifier shared with structured logs
 * @param status HTTP status code
 * @param code machine-readable assistant failure category
 * @param message safe client-facing explanation
 * @param details optional validation or recovery details
 */
public record ApiError(
    Instant timestamp,
    String requestId,
    int status,
    String code,
    String message,
    List<String> details) {

  /** Creates an immutable error payload while requiring all contract fields. */
  public ApiError {
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    details = List.copyOf(Objects.requireNonNull(details, "details"));
  }
}
