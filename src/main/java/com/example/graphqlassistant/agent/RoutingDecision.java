package com.example.graphqlassistant.agent;

import java.util.Objects;

public record RoutingDecision(RoutingIntent intent, String reason, double confidence) {

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
