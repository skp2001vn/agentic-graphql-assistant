package com.example.graphqlassistant.agent.langchain4j;

import com.example.graphqlassistant.agent.GenerationAgent;
import com.example.graphqlassistant.agent.SpecialistResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

/** Adapts the raw model/tool-loop response to the typed generation contract. */
public final class ParsedGenerationAgent implements GenerationAgent {

  private final GenerationModelAgent delegate;

  /**
   * Creates a typed boundary around a raw JSON-producing generation model.
   *
   * @param delegate LangChain4j model/tool-loop contract
   */
  public ParsedGenerationAgent(GenerationModelAgent delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Agent(
      name = "graphqlGenerationResult",
      description = "Parses the generation specialist result",
      outputKey = "generationResult")
  /**
   * Converts raw LLM JSON into the validated specialist domain model.
   *
   * @param prompt natural-language generation request
   * @return parsed structured output for orchestration
   */
  public SpecialistResult generate(@V("prompt") String prompt) {
    return LangChain4jAgentFactory.parseSpecialistResult(delegate.generate(prompt));
  }
}
