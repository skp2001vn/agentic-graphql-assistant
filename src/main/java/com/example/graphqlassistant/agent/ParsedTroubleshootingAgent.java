package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

/** Adapts the raw model/tool-loop response to the typed troubleshooting contract. */
public final class ParsedTroubleshootingAgent implements TroubleshootingAgent {

  private final TroubleshootingModelAgent delegate;

  public ParsedTroubleshootingAgent(TroubleshootingModelAgent delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Agent(
      name = "graphqlTroubleshootingResult",
      description = "Parses the troubleshooting specialist result",
      outputKey = "troubleshootingResult")
  public SpecialistResult troubleshoot(@V("prompt") String prompt) {
    return LangChain4jAgentFactory.parseSpecialistResult(delegate.troubleshoot(prompt));
  }
}
