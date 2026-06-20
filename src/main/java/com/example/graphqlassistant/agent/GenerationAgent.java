package com.example.graphqlassistant.agent;

/** Application-facing typed contract for the generation specialist. */
@FunctionalInterface
public interface GenerationAgent {

  /**
   * Generates a schema-grounded GraphQL operation from a natural-language request.
   *
   * @param prompt business request that the specialist must translate into GraphQL
   * @return structured generation result for deterministic downstream validation
   */
  SpecialistResult generate(String prompt);
}
