package com.example.graphqlassistant.agent;

/** Signals that an AI orchestration workflow exceeded its configured latency budget. */
public class AgentTimeoutException extends RuntimeException {

  /**
   * Creates a timeout failure for a bounded agent execution.
   *
   * @param message timeout diagnostic suitable for translation at the API boundary
   */
  public AgentTimeoutException(String message) {
    super(message);
  }
}
