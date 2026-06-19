package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@FunctionalInterface
public interface GenerationAgent {

  @Agent(name = "graphqlGeneration", description = "Generates schema-grounded GraphQL operations")
  @SystemMessage(
      """
      Generate exactly one GraphQL operation for the request. Use only the approved
      GraphQL tools to inspect the schema, validate the operation, and format it.
      Never execute GraphQL or access any external resource. Return only structured output.
      """)
  @UserMessage("{{prompt}}")
  SpecialistResult generate(@V("prompt") String prompt);
}
