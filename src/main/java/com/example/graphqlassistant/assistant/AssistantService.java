package com.example.graphqlassistant.assistant;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.OrchestrationOutcome;
import com.example.graphqlassistant.agent.SpecialistIssue;
import com.example.graphqlassistant.agent.SpecialistOutcome;
import com.example.graphqlassistant.agent.SpecialistResult;
import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.api.TroubleshootResponse;
import com.example.graphqlassistant.api.TroubleshootingIssue;
import com.example.graphqlassistant.provider.AiProviderException;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import dev.langchain4j.service.output.OutputParsingException;
import java.util.List;
import java.util.Objects;

public final class AssistantService {

  private final AssistantOrchestrator orchestrator;

  private final GraphqlOperationProcessor operationProcessor;

  public AssistantService(
      AssistantOrchestrator orchestrator, GraphqlOperationProcessor operationProcessor) {
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    this.operationProcessor = Objects.requireNonNull(operationProcessor, "operationProcessor");
  }

  public AssistantResponse assist(String prompt) {
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

    if (!(outcome instanceof SpecialistOutcome specialist)) {
      throw invalidResponse();
    }

    SpecialistResult result = specialist.result();
    if (result.intent() != specialist.routing().intent()) {
      throw invalidResponse();
    }

    String operation = operationProcessor.process(result.operation(), result.variables());
    return switch (result.intent()) {
      case GENERATE -> generateResponse(result, operation);
      case TROUBLESHOOT -> troubleshootResponse(result, operation);
      case CLARIFICATION_REQUIRED -> throw invalidResponse();
    };
  }

  private GenerateResponse generateResponse(SpecialistResult result, String operation) {
    if (!result.issues().isEmpty()) {
      throw invalidResponse();
    }
    return new GenerateResponse(operation, result.variables());
  }

  private TroubleshootResponse troubleshootResponse(SpecialistResult result, String operation) {
    if (result.issues().isEmpty()) {
      throw invalidResponse();
    }
    List<TroubleshootingIssue> issues = result.issues().stream().map(this::toApiIssue).toList();
    return new TroubleshootResponse(issues, operation, result.variables());
  }

  private TroubleshootingIssue toApiIssue(SpecialistIssue issue) {
    if (issue == null
        || issue.issue() == null
        || issue.issue().isBlank()
        || issue.details() == null
        || issue.details().isBlank()
        || issue.suggestion() == null
        || issue.suggestion().isBlank()) {
      throw invalidResponse();
    }
    return new TroubleshootingIssue(issue.issue(), issue.details(), issue.suggestion());
  }

  private InvalidAgentResponseException invalidResponse() {
    return new InvalidAgentResponseException("The AI returned an invalid assistant response");
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
