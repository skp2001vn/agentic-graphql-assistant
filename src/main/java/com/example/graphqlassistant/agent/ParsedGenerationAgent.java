package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

public final class ParsedGenerationAgent implements GenerationAgent {

  private final GenerationToolAgent delegate;

  public ParsedGenerationAgent(GenerationToolAgent delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Agent(
      name = "graphqlGenerationResult",
      description = "Parses the generation specialist result",
      outputKey = "generationResult")
  public SpecialistResult generate(@V("prompt") String prompt) {
    return LangChain4jAgentFactory.parseSpecialistResult(delegate.generate(prompt));
  }
}
