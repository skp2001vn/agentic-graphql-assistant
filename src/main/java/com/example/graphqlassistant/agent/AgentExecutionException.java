package com.example.graphqlassistant.agent;

/** Signals that a routed AI agent or its bounded tool-calling workflow failed to execute safely. */
public class AgentExecutionException extends RuntimeException {

  /**
   * Creates an agent execution failure with a stable business-facing diagnostic.
   *
   * @param message description of the failed agent workflow
   */
  public AgentExecutionException(String message) {
    super(message);
  }

  /**
   * Creates an agent execution failure while retaining the provider or orchestration root cause.
   *
   * @param message description of the failed agent workflow
   * @param cause underlying model, tool, or orchestration failure
   */
  public AgentExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
