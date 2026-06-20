package com.example.graphqlassistant.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * Model-backed intent classifier that routes prompts without attempting to answer them.
 *
 * <p>This narrow classification step separates routing from generation, reducing prompt scope and
 * enabling confidence-gated clarification before invoking a more expensive specialist agent.
 */
@FunctionalInterface
public interface AssistantRouter {

  /**
   * Classifies a prompt as generation, troubleshooting, or clarification-required.
   *
   * @param prompt untrusted natural-language assistant request
   * @return structured intent, rationale, and reported confidence from the routing model
   */
  @SystemMessage(
      """
      Classify the request only. Return GENERATE for a new GraphQL operation,
      TROUBLESHOOT for analysis or correction of an existing operation, or
      CLARIFICATION_REQUIRED when the request is insufficient or ambiguous.
      Supply a concise reason and confidence from 0 to 1. Do not answer the request.
      """)
  RoutingDecision route(String prompt);
}
