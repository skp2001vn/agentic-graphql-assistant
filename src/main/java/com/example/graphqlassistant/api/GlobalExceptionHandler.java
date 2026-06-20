package com.example.graphqlassistant.api;

import com.example.graphqlassistant.agent.AgentExecutionException;
import com.example.graphqlassistant.agent.AgentTimeoutException;
import com.example.graphqlassistant.agent.ClarificationRequiredException;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.provider.AiProviderException;
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

/**
 * Translates API, provider, and agent failures into stable error contracts.
 *
 * <p>The mapping deliberately hides raw LLM, provider, and tool exceptions while retaining a
 * request identifier and coarse failure category for safe observability.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  static final String ERROR_CATEGORY_ATTRIBUTE =
      GlobalExceptionHandler.class.getName() + ".errorCategory";

  @ExceptionHandler(ClarificationRequiredException.class)
  ResponseEntity<ApiError> handleClarificationRequired(
      ClarificationRequiredException exception, HttpServletRequest request) {
    return response(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "CLARIFICATION_REQUIRED",
        exception.getMessage(),
        List.of(),
        request);
  }

  @ExceptionHandler(AiProviderException.class)
  ResponseEntity<ApiError> handleAiProviderException(
      AiProviderException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        "AI_PROVIDER_ERROR",
        "The configured AI provider could not complete the request.",
        List.of(),
        request);
  }

  @ExceptionHandler(AgentTimeoutException.class)
  ResponseEntity<ApiError> handleAgentTimeout(
      AgentTimeoutException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        "AI_PROVIDER_ERROR",
        "The configured AI provider could not complete the request.",
        List.of(),
        request);
  }

  @ExceptionHandler(AgentExecutionException.class)
  ResponseEntity<ApiError> handleAgentExecution(
      AgentExecutionException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        "AGENT_EXECUTION_ERROR",
        "The assistant agent could not complete the request safely.",
        List.of(),
        request);
  }

  @ExceptionHandler(InvalidAgentResponseException.class)
  ResponseEntity<ApiError> handleInvalidAgentResponse(
      InvalidAgentResponseException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        "INVALID_AI_RESPONSE",
        "The AI response did not satisfy the required contract.",
        List.of(),
        request);
  }

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
    request.setAttribute(ERROR_CATEGORY_ATTRIBUTE, code);
    ApiError error =
        new ApiError(Instant.now(), requestId(request), status.value(), code, message, details);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(error);
  }

  private String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    return requestId instanceof String value ? value : UUID.randomUUID().toString();
  }
}
