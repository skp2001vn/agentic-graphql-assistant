package com.example.graphqlassistant.api;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Carries an intentional HTTP status and machine-readable business error across API layers. */
public class ApiException extends RuntimeException {

  /** HTTP status returned by the global exception translator. */
  private final HttpStatus status;

  /** Stable machine-readable error category returned to clients. */
  private final String code;

  /** Optional safe diagnostics returned with the API error. */
  private final List<String> details;

  /**
   * Creates an API exception without field-level details.
   *
   * @param status HTTP status to return
   * @param code stable machine-readable error code
   * @param message safe client-facing explanation
   */
  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

  /**
   * Creates an API exception with additional safe diagnostics.
   *
   * @param status HTTP status to return
   * @param code stable machine-readable error code
   * @param message safe client-facing explanation
   * @param details structured validation or recovery details
   */
  public ApiException(HttpStatus status, String code, String message, List<String> details) {
    super(message);
    this.status = Objects.requireNonNull(status, "status");
    this.code = Objects.requireNonNull(code, "code");
    this.details = List.copyOf(Objects.requireNonNull(details, "details"));
  }

  HttpStatus status() {
    return status;
  }

  String code() {
    return code;
  }

  List<String> details() {
    return details;
  }
}
