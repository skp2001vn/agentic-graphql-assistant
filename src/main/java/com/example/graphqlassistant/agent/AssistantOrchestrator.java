package com.example.graphqlassistant.agent;

import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import com.example.graphqlassistant.tools.OperationValidationResult;
import com.example.graphqlassistant.tools.ValidateOperationInput;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AssistantOrchestrator {

  private final AssistantRouter router;

  private final SpecialistWorkflow specialistWorkflow;

  private final GraphqlAssistantTools tools;

  private final Duration timeout;

  private final double minimumRoutingConfidence;

  public AssistantOrchestrator(
      AssistantRouter router,
      SpecialistWorkflow specialistWorkflow,
      GraphqlAssistantTools tools,
      Duration timeout,
      double minimumRoutingConfidence) {
    this.router = Objects.requireNonNull(router, "router");
    this.specialistWorkflow = Objects.requireNonNull(specialistWorkflow, "specialistWorkflow");
    this.tools = Objects.requireNonNull(tools, "tools");
    this.timeout = requirePositive(timeout);
    if (!Double.isFinite(minimumRoutingConfidence)
        || minimumRoutingConfidence < 0.0
        || minimumRoutingConfidence > 1.0) {
      throw new IllegalArgumentException("minimumRoutingConfidence must be between 0 and 1");
    }
    this.minimumRoutingConfidence = minimumRoutingConfidence;
  }

  public OrchestrationOutcome handle(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }

    FutureTask<OrchestrationOutcome> workflow = new FutureTask<>(() -> handleWithinTimeout(prompt));
    Thread.ofVirtual().name("assistant-workflow").start(workflow);
    try {
      return workflow.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      workflow.cancel(true);
      throw new AgentTimeoutException(
          "Assistant workflow exceeded its " + timeout.toMillis() + " ms timeout");
    } catch (InterruptedException exception) {
      workflow.cancel(true);
      Thread.currentThread().interrupt();
      throw new AgentTimeoutException("Assistant workflow was interrupted");
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Assistant workflow failed", exception.getCause());
    }
  }

  private OrchestrationOutcome handleWithinTimeout(String prompt) {
    RoutingDecision decision = router.route(prompt);
    if (decision == null) {
      throw new InvalidAgentResponseException("Router returned an invalid decision");
    }
    if (decision.intent() == RoutingIntent.CLARIFICATION_REQUIRED
        || decision.confidence() < minimumRoutingConfidence) {
      return new ClarificationOutcome(decision);
    }

    SpecialistResult result = specialistWorkflow.handle(prompt, decision.intent());
    validateSpecialistResult(result);
    return new SpecialistOutcome(decision, result);
  }

  private void validateSpecialistResult(SpecialistResult result) {
    if (result == null) {
      throw new InvalidAgentResponseException("Specialist returned an invalid result");
    }
    OperationValidationResult validation =
        tools.validateOperation(new ValidateOperationInput(result.operation()));
    if (!validation.valid()) {
      throw new InvalidAgentResponseException(
          "The specialist returned an invalid GraphQL operation");
    }
  }

  private static Duration requirePositive(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    return timeout;
  }
}
