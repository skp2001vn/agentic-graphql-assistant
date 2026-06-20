package com.example.graphqlassistant.agent;

/** Application-facing typed contract for the generation specialist. */
@FunctionalInterface
public interface GenerationAgent {

  SpecialistResult generate(String prompt);
}
