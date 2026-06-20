package com.example.graphqlassistant.agent;

/** Application-facing typed contract for the troubleshooting specialist. */
@FunctionalInterface
public interface TroubleshootingAgent {

  /**
   * Diagnoses and corrects a GraphQL operation using schema-grounded model reasoning.
   *
   * @param prompt request containing the operation and troubleshooting context
   * @return structured correction and issue explanations for deterministic validation
   */
  SpecialistResult troubleshoot(String prompt);
}
