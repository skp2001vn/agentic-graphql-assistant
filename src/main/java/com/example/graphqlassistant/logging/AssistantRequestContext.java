package com.example.graphqlassistant.logging;

import java.util.concurrent.atomic.AtomicReference;

public final class AssistantRequestContext {

  private static final InheritableThreadLocal<State> CURRENT = new InheritableThreadLocal<>();

  private AssistantRequestContext() {
    throw new AssertionError("No instances");
  }

  public static void start(String requestId) {
    CURRENT.set(new State(requestId));
  }

  public static String requestId() {
    State state = CURRENT.get();
    return state == null ? "untracked" : state.requestId;
  }

  public static void selectAgent(String selectedAgent) {
    State state = CURRENT.get();
    if (state != null) {
      state.selectedAgent.set(selectedAgent);
    }
  }

  public static String selectedAgent() {
    State state = CURRENT.get();
    return state == null ? "UNKNOWN" : state.selectedAgent.get();
  }

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
