package com.example.graphqlassistant.provider;

public class AiProviderException extends RuntimeException {

  public AiProviderException(String providerName, String modelName) {
    super("AI provider request failed for " + providerName + " model " + modelName);
  }
}
