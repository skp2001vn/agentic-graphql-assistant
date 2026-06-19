package com.example.graphqlassistant.agent;

import java.util.Objects;

public record SpecialistOutcome(RoutingDecision routing, SpecialistResult result)
    implements OrchestrationOutcome {

  public SpecialistOutcome {
    Objects.requireNonNull(routing, "routing");
    Objects.requireNonNull(result, "result");
    if (routing.intent() == RoutingIntent.CLARIFICATION_REQUIRED) {
      throw new IllegalArgumentException("Specialist outcome requires a specialist intent");
    }
  }
}
