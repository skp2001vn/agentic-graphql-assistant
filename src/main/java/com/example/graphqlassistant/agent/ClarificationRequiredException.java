package com.example.graphqlassistant.agent;

public class ClarificationRequiredException extends RuntimeException {

  public ClarificationRequiredException(String message) {
    super(message);
  }
}
