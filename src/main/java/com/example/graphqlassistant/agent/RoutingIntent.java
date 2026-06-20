package com.example.graphqlassistant.agent;

/** Supported intents in the assistant's model-routing taxonomy. */
public enum RoutingIntent {
  /** Generate a new schema-grounded GraphQL operation. */
  GENERATE,

  /** Diagnose and correct an existing GraphQL operation. */
  TROUBLESHOOT,

  /** Ask for more context instead of choosing an unsafe or low-confidence route. */
  CLARIFICATION_REQUIRED
}
