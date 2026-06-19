package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

@FunctionalInterface
public interface SpecialistWorkflow {

  @Agent
  SpecialistResult handle(
      @V("prompt") String prompt, @V("routingIntent") RoutingIntent routingIntent);
}
