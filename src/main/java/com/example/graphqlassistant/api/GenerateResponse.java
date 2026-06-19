package com.example.graphqlassistant.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Schema(requiredProperties = {"intent", "query", "variables"})
public record GenerateResponse(
    @Schema(allowableValues = "GENERATE") String intent,
    @Schema(description = "Pretty-printed GraphQL operation, one line per array element")
        List<String> query,
    Map<String, Object> variables)
    implements AssistantResponse {

  public GenerateResponse(String query, Map<String, Object> variables) {
    this("GENERATE", query == null ? null : query.lines().toList(), variables);
  }

  public GenerateResponse {
    if (!"GENERATE".equals(intent)) {
      throw new IllegalArgumentException("Generate response intent must be GENERATE");
    }
    if (query == null
        || query.isEmpty()
        || query.stream().anyMatch(Objects::isNull)
        || query.stream().allMatch(String::isBlank)) {
      throw new IllegalArgumentException("Generate response query must not be blank");
    }
    query = List.copyOf(query);
    variables =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(variables, "variables")));
  }
}
