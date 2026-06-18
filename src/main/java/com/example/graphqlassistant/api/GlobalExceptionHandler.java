package com.example.graphqlassistant.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
    return response(
        exception.status(), exception.code(), exception.getMessage(), exception.details(), request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Content-Type must be text/plain with UTF-8 encoding.",
        List.of(),
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableMessage(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Request body must contain a nonblank UTF-8 prompt.",
        List.of(),
        request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  void preserveNotFoundResponse(NoResourceFoundException exception)
      throws NoResourceFoundException {
    throw exception;
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred.",
        List.of(),
        request);
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status,
      String code,
      String message,
      List<String> details,
      HttpServletRequest request) {
    ApiError error =
        new ApiError(Instant.now(), requestId(request), status.value(), code, message, details);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(error);
  }

  private String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    return requestId instanceof String value ? value : UUID.randomUUID().toString();
  }
}
