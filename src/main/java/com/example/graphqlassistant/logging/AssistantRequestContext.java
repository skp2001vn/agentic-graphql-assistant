package com.example.graphqlassistant.logging;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Propagates request and selected-agent metadata through synchronous and virtual-thread execution.
 *
 * <p>An inheritable thread-local provides lightweight correlation across the blocking LLM workflow,
 * while the atomic agent label supports safe updates from inherited execution context.
 */
public final class AssistantRequestContext {

  private static final InheritableThreadLocal<State> CURRENT = new InheritableThreadLocal<>();

  private AssistantRequestContext() {
    throw new AssertionError("No instances");
  }

  /**
   * Starts request-scoped AI observability context.
   *
   * @param requestId validated correlation identifier
   */
  public static void start(String requestId) {
    CURRENT.set(new State(requestId));
  }

  /**
   * Returns the active request correlation identifier.
   *
   * @return request identifier or {@code untracked} outside a request
   */
  public static String requestId() {
    State state = CURRENT.get();
    return state == null ? "untracked" : state.requestId;
  }

  /**
   * Records the specialist selected by model routing.
   *
   * @param selectedAgent normalized agent label
   */
  public static void selectAgent(String selectedAgent) {
    State state = CURRENT.get();
    if (state != null) {
      state.selectedAgent.set(selectedAgent);
    }
  }

  /**
   * Returns the selected specialist for completion telemetry.
   *
   * @return selected agent label or {@code UNKNOWN} outside a request
   */
  public static String selectedAgent() {
    State state = CURRENT.get();
    return state == null ? "UNKNOWN" : state.selectedAgent.get();
  }

  /** Clears inherited request state to prevent cross-request context leakage. */
  public static void clear() {
    CURRENT.remove();
  }

  private static final class State {

    private final String requestId;

    private final AtomicReference<String> selectedAgent = new AtomicReference<>("ROUTER");

    private State(String requestId) {
      this.requestId = requestId;
    }
  }
}
