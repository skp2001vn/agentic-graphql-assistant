package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@FunctionalInterface
public interface TroubleshootingAgent {

  @Agent(
      name = "graphqlTroubleshooting",
      description = "Diagnoses and corrects schema-grounded GraphQL operations")
  @SystemMessage(
      """
      Diagnose the supplied GraphQL operation and return exactly one corrected operation.
      Use only the approved GraphQL tools to inspect the schema, validate the operation,
      and format it. Never execute GraphQL or access any external resource.
      Return only structured output.
      """)
  @UserMessage("{{prompt}}")
  SpecialistResult troubleshoot(@V("prompt") String prompt);
}
