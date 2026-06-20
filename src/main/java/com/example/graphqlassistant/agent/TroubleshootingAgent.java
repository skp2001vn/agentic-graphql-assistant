package com.example.graphqlassistant.agent;

/** Application-facing typed contract for the troubleshooting specialist. */
@FunctionalInterface
public interface TroubleshootingAgent {

  SpecialistResult troubleshoot(String prompt);
}
