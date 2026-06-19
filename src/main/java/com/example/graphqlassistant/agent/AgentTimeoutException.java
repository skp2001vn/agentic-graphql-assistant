package com.example.graphqlassistant.agent;

public class AgentTimeoutException extends RuntimeException {

  public AgentTimeoutException(String message) {
    super(message);
  }
}
