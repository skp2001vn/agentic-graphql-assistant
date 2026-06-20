package com.example.graphqlassistant.agent;

/** Signals that probabilistic model output failed a deterministic application contract. */
public class InvalidAgentResponseException extends RuntimeException {

  /**
   * Creates a validation failure for malformed, unsafe, or schema-invalid AI output.
   *
   * @param message stable diagnostic describing the rejected model response
   */
  public InvalidAgentResponseException(String message) {
    super(message);
  }
}
