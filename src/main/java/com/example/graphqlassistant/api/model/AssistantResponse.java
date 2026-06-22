package com.example.graphqlassistant.api.model;

/** Closed API response hierarchy for generation and troubleshooting assistant intents. */
public sealed interface AssistantResponse permits GenerateResponse, TroubleshootResponse {

  /**
   * Returns the normalized intent that selected this response shape.
   *
   * @return {@code GENERATE} or {@code TROUBLESHOOT}
   */
  String intent();
}
