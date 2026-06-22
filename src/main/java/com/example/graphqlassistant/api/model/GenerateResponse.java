package com.example.graphqlassistant.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * API payload for a schema-grounded GraphQL operation generated from natural language.
 *
 * <p>The operation is represented as lines for readable JSON while variables remain a typed JSON
 * object that clients can pass to a GraphQL runtime.
 *
 * @param intent fixed {@code GENERATE} response discriminator
 * @param query validated GraphQL operation split into display-friendly lines
 * @param variables immutable runtime variable values
 */
@Schema(requiredProperties = {"intent", "query", "variables"})
public record GenerateResponse(
    @Schema(allowableValues = "GENERATE") String intent,
    @Schema(description = "Pretty-printed GraphQL operation, one line per array element")
        List<String> query,
    Map<String, Object> variables)
    implements AssistantResponse {

  /**
   * Creates a generation payload from canonical GraphQL text.
   *
   * @param query validated, pretty-printed GraphQL operation
   * @param variables runtime variable values produced or normalized by the assistant
   */
  public GenerateResponse(String query, Map<String, Object> variables) {
    this("GENERATE", query == null ? null : query.lines().toList(), variables);
  }

  /** Enforces the generation response discriminator and immutable operation data. */
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
