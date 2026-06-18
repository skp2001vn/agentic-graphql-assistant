package com.example.graphqlassistant.api;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;

  private final String code;

  private final List<String> details;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

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
