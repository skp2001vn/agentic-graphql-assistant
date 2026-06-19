package com.example.graphqlassistant.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.Objects;

public final class ParsedTroubleshootingAgent implements TroubleshootingAgent {

  private final TroubleshootingToolAgent delegate;

  public ParsedTroubleshootingAgent(TroubleshootingToolAgent delegate) {
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
