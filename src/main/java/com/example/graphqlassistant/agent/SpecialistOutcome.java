package com.example.graphqlassistant.agent;

import java.util.Objects;

/**
 * Successful specialist branch containing both the routing trace and structured AI result.
 *
 * @param routing router decision that selected the specialist
 * @param result structured generation or troubleshooting output
 */
public record SpecialistOutcome(RoutingDecision routing, SpecialistResult result)
    implements OrchestrationOutcome {

  /** Ensures a specialist outcome cannot represent the non-specialist clarification branch. */
  public SpecialistOutcome {
    Objects.requireNonNull(routing, "routing");
    Objects.requireNonNull(result, "result");
    if (routing.intent() == RoutingIntent.CLARIFICATION_REQUIRED) {
      throw new IllegalArgumentException("Specialist outcome requires a specialist intent");
    }
  }
}
