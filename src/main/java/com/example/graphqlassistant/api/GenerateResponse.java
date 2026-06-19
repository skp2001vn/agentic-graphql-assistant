package com.example.graphqlassistant.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Schema(requiredProperties = {"intent", "query", "variables"})
public record GenerateResponse(
    @Schema(allowableValues = "GENERATE") String intent,
    String query,
    Map<String, Object> variables)
    implements AssistantResponse {

  public GenerateResponse(String query, Map<String, Object> variables) {
    this("GENERATE", query, variables);
  }

  public GenerateResponse {
    if (!"GENERATE".equals(intent)) {
      throw new IllegalArgumentException("Generate response intent must be GENERATE");
    }
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Generate response query must not be blank");
    }
    variables =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(variables, "variables")));
  }
}
