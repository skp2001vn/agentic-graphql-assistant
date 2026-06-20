package com.example.graphqlassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe external configuration for schema grounding, AI inference, and assistant observability.
 *
 * <p>Spring binds these properties from environment variables or configuration files so provider,
 * model, timeout, schema, and logging choices remain deployment concerns rather than hard-coded
 * business logic.
 */
@ConfigurationProperties("assistant")
public class AssistantProperties {

  private final Schema schema = new Schema();

  private final Ai ai = new Ai();

  private final Logging logging = new Logging();

  /**
   * Returns GraphQL schema-loading settings.
   *
   * @return mutable Spring-bound schema settings
   */
  public Schema getSchema() {
    return schema;
  }

  /**
   * Returns AI provider, model, and latency settings.
   *
   * @return mutable Spring-bound inference settings
   */
  public Ai getAi() {
    return ai;
  }

  /**
   * Returns AI observability and content-logging settings.
   *
   * @return mutable Spring-bound logging settings
   */
  public Logging getLogging() {
    return logging;
  }

  /** Configures the schema definition used to ground and validate generated GraphQL operations. */
  public static class Schema {

    private String location = "classpath:schema.graphql";

    /**
     * Returns the Spring resource location of the GraphQL SDL.
     *
     * @return schema resource location
     */
    public String getLocation() {
      return location;
    }

    /**
     * Sets the Spring resource location of the GraphQL SDL.
     *
     * @param location schema resource location
     */
    public void setLocation(String location) {
      this.location = location;
    }
  }

  /** Configures provider selection, inference latency budgets, and provider-specific models. */
  public static class Ai {

    private String provider = "ollama";

    private Duration requestTimeout = Duration.ofSeconds(60);

    private Duration warmResponseTarget = Duration.ofSeconds(30);

    private final Ollama ollama = new Ollama();

    private final OpenAi openai = new OpenAi();

    /**
     * Returns the selected inference provider identifier.
     *
     * @return {@code ollama} or {@code openai}
     */
    public String getProvider() {
      return provider;
    }

    /**
     * Selects the inference provider.
     *
     * @param provider {@code ollama} or {@code openai}
     */
    public void setProvider(String provider) {
      this.provider = provider;
    }

    /**
     * Returns the hard end-to-end model and agent timeout.
     *
     * @return request timeout
     */
    public Duration getRequestTimeout() {
      return requestTimeout;
    }

    /**
     * Sets the hard end-to-end model and agent timeout.
     *
     * @param requestTimeout positive request timeout
     */
    public void setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
    }

    /**
     * Returns the operational target for a warm model response.
     *
     * @return warm-response service-level target
     */
    public Duration getWarmResponseTarget() {
      return warmResponseTarget;
    }

    /**
     * Sets the operational target for a warm model response.
     *
     * @param warmResponseTarget positive warm-response target
     */
    public void setWarmResponseTarget(Duration warmResponseTarget) {
      this.warmResponseTarget = warmResponseTarget;
    }

    /**
     * Returns local Ollama inference settings.
     *
     * @return mutable Ollama settings
     */
    public Ollama getOllama() {
      return ollama;
    }

    /**
     * Returns hosted OpenAI inference settings.
     *
     * @return mutable OpenAI settings
     */
    public OpenAi getOpenai() {
      return openai;
    }
  }

  /** Configures a local Ollama endpoint and the model used for private on-device inference. */
  public static class Ollama {

    private String baseUrl = "http://localhost:11434";

    private String model = "qwen3:8b";

    /**
     * Returns the Ollama HTTP endpoint.
     *
     * @return Ollama base URL
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    /**
     * Sets the Ollama HTTP endpoint.
     *
     * @param baseUrl Ollama base URL
     */
    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    /**
     * Returns the Ollama model tag used for inference.
     *
     * @return local model tag
     */
    public String getModel() {
      return model;
    }

    /**
     * Sets the Ollama model tag used for inference.
     *
     * @param model local model tag
     */
    public void setModel(String model) {
      this.model = model;
    }
  }

  /** Configures OpenAI credentials and the hosted model used for inference. */
  public static class OpenAi {

    private String apiKey = "";

    private String model = "gpt-5.4-mini";

    /**
     * Returns the OpenAI API key supplied to the provider client.
     *
     * @return configured API key
     */
    public String getApiKey() {
      return apiKey;
    }

    /**
     * Sets the OpenAI API key supplied to the provider client.
     *
     * @param apiKey provider credential
     */
    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    /**
     * Returns the hosted OpenAI model identifier.
     *
     * @return model identifier
     */
    public String getModel() {
      return model;
    }

    /**
     * Sets the hosted OpenAI model identifier.
     *
     * @param model model identifier
     */
    public void setModel(String model) {
      this.model = model;
    }
  }

  /** Configures whether prompts, schemas, model output, and tool payloads are logged in full. */
  public static class Logging {

    private boolean fullContentEnabled = true;

    /**
     * Reports whether full AI request and response content is included in structured logs.
     *
     * @return {@code true} when full-content logging is enabled
     */
    public boolean isFullContentEnabled() {
      return fullContentEnabled;
    }

    /**
     * Enables or disables full AI request and response content logging.
     *
     * @param fullContentEnabled whether sensitive model context may be logged after redaction
     */
    public void setFullContentEnabled(boolean fullContentEnabled) {
      this.fullContentEnabled = fullContentEnabled;
    }
  }
}
