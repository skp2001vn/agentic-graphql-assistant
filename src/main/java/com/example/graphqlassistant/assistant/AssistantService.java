package com.example.graphqlassistant.assistant;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.OrchestrationOutcome;
import com.example.graphqlassistant.agent.RoutingIntent;
import com.example.graphqlassistant.agent.SpecialistOutcome;
import com.example.graphqlassistant.agent.SpecialistResult;
import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.provider.AiProviderException;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import dev.langchain4j.service.output.OutputParsingException;
import java.util.Objects;

public final class AssistantService {

  private final AssistantOrchestrator orchestrator;

  private final GraphqlOperationProcessor operationProcessor;

  public AssistantService(
      AssistantOrchestrator orchestrator, GraphqlOperationProcessor operationProcessor) {
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    this.operationProcessor = Objects.requireNonNull(operationProcessor, "operationProcessor");
  }

  public GenerateResponse generate(String prompt) {
    OrchestrationOutcome outcome;
    try {
      outcome = orchestrator.handle(prompt);
    } catch (RuntimeException exception) {
      AiProviderException providerFailure = findCause(exception, AiProviderException.class);
      if (providerFailure != null) {
        throw providerFailure;
      }
      if (findCause(exception, OutputParsingException.class) != null) {
        throw invalidResponse();
      }
      throw exception;
    }

    if (!(outcome instanceof SpecialistOutcome specialist)
        || specialist.routing().intent() != RoutingIntent.GENERATE) {
      throw invalidResponse();
    }

    SpecialistResult result = specialist.result();
    if (result.intent() != RoutingIntent.GENERATE) {
      throw invalidResponse();
    }

    String operation = operationProcessor.process(result.operation(), result.variables());
    return new GenerateResponse(operation, result.variables());
  }

  private InvalidAgentResponseException invalidResponse() {
    return new InvalidAgentResponseException("The AI returned an invalid generation response");
  }

  private <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (causeType.isInstance(cause)) {
        return causeType.cast(cause);
      }
    }
    return null;
  }
}
