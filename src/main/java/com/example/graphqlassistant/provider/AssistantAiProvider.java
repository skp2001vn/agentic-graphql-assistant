package com.example.graphqlassistant.provider;

import dev.langchain4j.model.chat.ChatModel;

public interface AssistantAiProvider extends ChatModel {

  String providerName();

  String modelName();
}
