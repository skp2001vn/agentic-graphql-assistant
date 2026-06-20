package com.example.graphqlassistant.agent;

import java.util.Objects;

/**
 * Structured intent-classification output produced by the assistant router.
 *
 * @param intent selected route in the assistant intent taxonomy
 * @param reason concise model rationale for observability and clarification
 * @param confidence normalized confidence score used by the routing gate
 */
public record RoutingDecision(RoutingIntent intent, String reason, double confidence) {

  /** Enforces the routing taxonomy and normalized confidence range used for confidence gating. */
  public RoutingDecision {
    Objects.requireNonNull(intent, "intent");
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Routing reason must not be blank");
    }
    if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("Routing confidence must be between 0 and 1");
    }
  }
}
