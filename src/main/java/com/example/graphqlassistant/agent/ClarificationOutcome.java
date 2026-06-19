package com.example.graphqlassistant.agent;

import java.util.Objects;

public record ClarificationOutcome(RoutingDecision routing) implements OrchestrationOutcome {

  public ClarificationOutcome {
    Objects.requireNonNull(routing, "routing");
  }
}
