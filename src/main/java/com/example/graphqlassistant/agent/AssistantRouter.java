package com.example.graphqlassistant.agent;

import dev.langchain4j.service.SystemMessage;

@FunctionalInterface
public interface AssistantRouter {

  @SystemMessage(
      """
      Classify the request only. Return GENERATE for a new GraphQL operation,
      TROUBLESHOOT for analysis or correction of an existing operation, or
      CLARIFICATION_REQUIRED when the request is insufficient or ambiguous.
      Supply a concise reason and confidence from 0 to 1. Do not answer the request.
      """)
  RoutingDecision route(String prompt);
}
