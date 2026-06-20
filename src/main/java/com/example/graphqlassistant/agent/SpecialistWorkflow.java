package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Conditional agentic workflow that dispatches generation or troubleshooting specialists. */
@FunctionalInterface
public interface SpecialistWorkflow {

  /**
   * Executes the specialist selected by the router's intent.
   *
   * @param prompt original natural-language request
   * @param routingIntent generation or troubleshooting branch to execute
   * @return structured specialist result stored in the LangChain4j workflow state
   */
  @Agent
  SpecialistResult handle(
      @V("prompt") String prompt, @V("routingIntent") RoutingIntent routingIntent);
}
