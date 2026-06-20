package com.example.graphqlassistant.agent;

import java.util.Objects;

/**
 * Represents a confidence-gated decision to request more user context instead of hallucinating.
 *
 * @param routing router decision that triggered clarification
 */
public record ClarificationOutcome(RoutingDecision routing) implements OrchestrationOutcome {

  /** Ensures every clarification preserves the router decision that triggered it. */
  public ClarificationOutcome {
    Objects.requireNonNull(routing, "routing");
  }
}
