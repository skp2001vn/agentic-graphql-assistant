package com.example.graphqlassistant.api;

public sealed interface AssistantResponse permits GenerateResponse, TroubleshootResponse {

  String intent();
}
