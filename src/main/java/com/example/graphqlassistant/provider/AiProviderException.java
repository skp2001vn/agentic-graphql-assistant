package com.example.graphqlassistant.provider;

/** Wraps provider-specific inference failures behind a stable assistant-domain exception. */
public class AiProviderException extends RuntimeException {

  /**
   * Creates a sanitized provider failure without leaking raw SDK or remote-service details.
   *
   * @param providerName inference provider identifier
   * @param modelName model identifier used for the failed request
   */
  public AiProviderException(String providerName, String modelName) {
    super("AI provider request failed for " + providerName + " model " + modelName);
  }
}
