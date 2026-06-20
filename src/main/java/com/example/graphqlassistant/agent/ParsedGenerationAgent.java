package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

/** Adapts the raw model/tool-loop response to the typed generation contract. */
public final class ParsedGenerationAgent implements GenerationAgent {

  private final GenerationModelAgent delegate;

  public ParsedGenerationAgent(GenerationModelAgent delegate) {
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
