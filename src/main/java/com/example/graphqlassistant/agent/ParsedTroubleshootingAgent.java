package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

/** Adapts the raw model/tool-loop response to the typed troubleshooting contract. */
public final class ParsedTroubleshootingAgent implements TroubleshootingAgent {

  private final TroubleshootingModelAgent delegate;

  /**
   * Creates a typed boundary around a raw JSON-producing troubleshooting model.
   *
   * @param delegate LangChain4j model/tool-loop contract
   */
  public ParsedTroubleshootingAgent(TroubleshootingModelAgent delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Agent(
      name = "graphqlTroubleshootingResult",
      description = "Parses the troubleshooting specialist result",
      outputKey = "troubleshootingResult")
  /**
   * Converts raw LLM JSON into the validated specialist domain model.
   *
   * @param prompt natural-language request containing an operation to diagnose
   * @return parsed structured output for orchestration
   */
  public SpecialistResult troubleshoot(@V("prompt") String prompt) {
    return LangChain4jAgentFactory.parseSpecialistResult(delegate.troubleshoot(prompt));
  }
}
