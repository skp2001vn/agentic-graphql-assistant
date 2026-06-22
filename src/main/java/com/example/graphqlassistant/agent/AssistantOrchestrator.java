package com.example.graphqlassistant.agent;

import com.example.graphqlassistant.logging.AssistantRequestLogger;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coordinates intent classification, specialist selection, and bounded tool use for one assistant
 * request.
 *
 * <p>The orchestrator implements a confidence-gated agentic workflow: a router first performs
 * intent classification, low-confidence requests become clarification outcomes, and accepted
 * requests run through a specialist with a hard timeout and tool-call guardrails. Final operation
 * validation remains at the application service boundary.
 */
public final class AssistantOrchestrator {

  private final AssistantRouter router;

  private final SpecialistWorkflow specialistWorkflow;

  private final GraphqlAssistantTools tools;

  private final Duration timeout;

  private final double minimumRoutingConfidence;

  private final AssistantRequestLogger requestLogger;

  /**
   * Creates an orchestrator with logging disabled, primarily for focused application composition.
   *
   * @param router model-backed intent classifier
   * @param specialistWorkflow conditional generation/troubleshooting workflow
   * @param tools schema-grounding and GraphQL validation tools
   * @param timeout end-to-end agent latency limit
   * @param minimumRoutingConfidence confidence threshold below which clarification is required
   */
  public AssistantOrchestrator(
      AssistantRouter router,
      SpecialistWorkflow specialistWorkflow,
      GraphqlAssistantTools tools,
      Duration timeout,
      double minimumRoutingConfidence) {
    this(
        router,
        specialistWorkflow,
        tools,
        timeout,
        minimumRoutingConfidence,
        AssistantRequestLogger.disabled());
  }

  /**
   * Creates an orchestrator with request-scoped AI observability.
   *
   * @param router model-backed intent classifier
   * @param specialistWorkflow conditional generation/troubleshooting workflow
   * @param tools schema-grounding and GraphQL validation tools
   * @param timeout end-to-end agent latency limit
   * @param minimumRoutingConfidence confidence threshold below which clarification is required
   * @param requestLogger structured logger for routing and agent telemetry
   */
  public AssistantOrchestrator(
      AssistantRouter router,
      SpecialistWorkflow specialistWorkflow,
      GraphqlAssistantTools tools,
      Duration timeout,
      double minimumRoutingConfidence,
      AssistantRequestLogger requestLogger) {
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
    this.requestLogger = Objects.requireNonNull(requestLogger, "requestLogger");
  }

  /**
   * Runs the bounded router-specialist pipeline for a natural-language prompt.
   *
   * <p>A virtual thread isolates the blocking model call while the timeout caps total inference and
   * tool-loop latency.
   *
   * @param prompt untrusted natural-language generation or troubleshooting request
   * @return either a validated specialist result or a clarification outcome
   * @throws AgentTimeoutException when the workflow exceeds its latency budget
   * @throws AgentExecutionException when a router or specialist fails
   * @throws InvalidAgentResponseException when model output violates the required contract
   */
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
    RoutingDecision decision;
    try {
      decision = router.route(prompt);
    } catch (RuntimeException exception) {
      throw new AgentExecutionException("Assistant agent execution failed", exception);
    }
    if (decision == null) {
      throw new InvalidAgentResponseException("Router returned an invalid decision");
    }
    requestLogger.agentSelected(decision.intent());
    if (decision.intent() == RoutingIntent.CLARIFICATION_REQUIRED
        || decision.confidence() < minimumRoutingConfidence) {
      return new ClarificationOutcome(decision);
    }

    tools.beginToolTracking();
    try {
      SpecialistResult result;
      try {
        result = specialistWorkflow.handle(prompt, decision.intent());
      } catch (RuntimeException exception) {
        throw new AgentExecutionException("Assistant agent execution failed", exception);
      }
      tools.finishModelToolCalls();
      if (decision.intent() == RoutingIntent.GENERATE && !tools.wasSchemaInspected()) {
        throw new InvalidAgentResponseException(
            "Generation specialist did not inspect the configured schema");
      }
      if (result == null) {
        throw new InvalidAgentResponseException("Specialist returned an invalid result");
      }
      return new SpecialistOutcome(decision, result);
    } finally {
      tools.clearToolTracking();
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
