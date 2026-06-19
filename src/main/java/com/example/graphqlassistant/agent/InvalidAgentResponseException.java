package com.example.graphqlassistant.agent;

public class InvalidAgentResponseException extends RuntimeException {

  public InvalidAgentResponseException(String message) {
    super(message);
  }
}
