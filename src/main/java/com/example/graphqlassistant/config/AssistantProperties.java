package com.example.graphqlassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("assistant")
public class AssistantProperties {

  private final Schema schema = new Schema();

  private final Ai ai = new Ai();

  public Schema getSchema() {
    return schema;
  }

  public Ai getAi() {
    return ai;
  }

  public static class Schema {

    private String location = "classpath:schema.graphql";

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }

  public static class Ai {

    private String provider = "ollama";

    private Duration requestTimeout = Duration.ofSeconds(60);

    private Duration warmResponseTarget = Duration.ofSeconds(30);

    private final Ollama ollama = new Ollama();

    private final OpenAi openai = new OpenAi();

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public Duration getRequestTimeout() {
      return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
    }

    public Duration getWarmResponseTarget() {
      return warmResponseTarget;
    }

    public void setWarmResponseTarget(Duration warmResponseTarget) {
      this.warmResponseTarget = warmResponseTarget;
    }

    public Ollama getOllama() {
      return ollama;
    }

    public OpenAi getOpenai() {
      return openai;
    }
  }

  public static class Ollama {

    private String baseUrl = "http://localhost:11434";

    private String model = "qwen3:8b";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }

  public static class OpenAi {

    private String apiKey = "";

    private String model = "gpt-5-mini";

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }
}
