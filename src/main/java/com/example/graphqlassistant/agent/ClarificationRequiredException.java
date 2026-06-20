package com.example.graphqlassistant.agent;

/**
 * Signals that the assistant needs more context to route the request with sufficient confidence.
 */
public class ClarificationRequiredException extends RuntimeException {

  /**
   * Creates a clarification-required failure for translation to an actionable API response.
   *
   * @param message guidance describing the missing user context
   */
  public ClarificationRequiredException(String message) {
    super(message);
  }
}
