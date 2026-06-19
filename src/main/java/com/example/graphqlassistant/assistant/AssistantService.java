package com.example.graphqlassistant.assistant;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.ClarificationOutcome;
import com.example.graphqlassistant.agent.ClarificationRequiredException;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.agent.OrchestrationOutcome;
import com.example.graphqlassistant.agent.SpecialistIssue;
import com.example.graphqlassistant.agent.SpecialistOutcome;
import com.example.graphqlassistant.agent.SpecialistResult;
import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.api.TroubleshootResponse;
import com.example.graphqlassistant.api.TroubleshootingIssue;
import com.example.graphqlassistant.logging.AssistantRequestLogger;
import com.example.graphqlassistant.provider.AiProviderException;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import dev.langchain4j.service.output.OutputParsingException;
import java.util.List;
import java.util.Objects;

public final class AssistantService {

  private static final String CLARIFICATION_GUIDANCE =
      "Specify what operation you want to generate or include the operation to troubleshoot.";

  private final AssistantOrchestrator orchestrator;

  private final GraphqlOperationProcessor operationProcessor;

  private final AssistantRequestLogger requestLogger;

  private final AssistantAiProvider provider;

  private final GraphqlSchemaContext schemaContext;

  public AssistantService(
      AssistantOrchestrator orchestrator, GraphqlOperationProcessor operationProcessor) {
    this(orchestrator, operationProcessor, AssistantRequestLogger.disabled(), null, null);
  }

  public AssistantService(
      AssistantOrchestrator orchestrator,
      GraphqlOperationProcessor operationProcessor,
      AssistantRequestLogger requestLogger,
      AssistantAiProvider provider,
      GraphqlSchemaContext schemaContext) {
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    this.operationProcessor = Objects.requireNonNull(operationProcessor, "operationProcessor");
    this.requestLogger = Objects.requireNonNull(requestLogger, "requestLogger");
    this.provider = provider;
    this.schemaContext = schemaContext;
  }

  public AssistantResponse assist(String prompt) {
    if (provider != null && schemaContext != null) {
      requestLogger.requestStarted(
          provider.providerName(), provider.modelName(), schemaContext.schemaText(), prompt);
    }
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
      InvalidAgentResponseException invalidResponse =
          findCause(exception, InvalidAgentResponseException.class);
      if (invalidResponse != null) {
        throw invalidResponse;
      }
      throw exception;
    }

    if (outcome instanceof ClarificationOutcome) {
      throw new ClarificationRequiredException(CLARIFICATION_GUIDANCE);
    }
    if (!(outcome instanceof SpecialistOutcome specialist)) {
      throw invalidResponse();
    }

    SpecialistResult result = specialist.result();
    if (result.intent() != specialist.routing().intent()) {
      throw invalidResponse();
    }

    String operation = operationProcessor.process(result.operation(), result.variables());
    AssistantResponse response =
        switch (result.intent()) {
          case GENERATE -> generateResponse(result, operation);
          case TROUBLESHOOT -> troubleshootResponse(result, operation);
          case CLARIFICATION_REQUIRED -> throw invalidResponse();
        };
    requestLogger.normalizedResponse(response);
    return response;
  }

  private GenerateResponse generateResponse(SpecialistResult result, String operation) {
    if (!result.issues().isEmpty()) {
      throw invalidResponse();
    }
    return new GenerateResponse(operation, result.variables());
  }

  private TroubleshootResponse troubleshootResponse(SpecialistResult result, String operation) {
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
