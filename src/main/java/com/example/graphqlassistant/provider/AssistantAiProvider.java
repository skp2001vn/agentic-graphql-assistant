package com.example.graphqlassistant.provider;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Provider-neutral chat-model contract enriched with stable provider and model identity.
 *
 * <p>The abstraction lets the agent graph use local or hosted LLM inference without coupling
 * orchestration business logic to a specific SDK.
 */
public interface AssistantAiProvider extends ChatModel {

  /**
   * Returns the configured inference provider.
   *
   * @return stable provider identifier
   */
  String providerName();

  /**
   * Returns the configured model.
   *
   * @return stable model identifier
   */
  String modelName();
}
