package com.example.graphqlassistant.agent;

public record SpecialistResult(String operation) {

  public SpecialistResult {
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("Specialist operation must not be blank");
    }
  }
}
