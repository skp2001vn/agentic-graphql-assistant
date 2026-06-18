package com.example.graphqlassistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiError(
    Instant timestamp,
    String requestId,
    int status,
    String code,
    String message,
    List<String> details) {

  public ApiError {
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    details = List.copyOf(Objects.requireNonNull(details, "details"));
  }
}
