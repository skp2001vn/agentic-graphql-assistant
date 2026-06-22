package com.example.graphqlassistant.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * API payload for AI-assisted GraphQL diagnosis and a deterministically validated correction.
 *
 * <p>Issue explanations preserve the model's diagnostic summary while the corrected operation and
 * variables have already passed schema validation and canonical formatting.
 *
 * @param intent fixed {@code TROUBLESHOOT} response discriminator
 * @param issues immutable GraphQL diagnoses and repair guidance
 * @param correctedQuery validated correction split into display-friendly lines
 * @param variables immutable runtime variable values
 */
@Schema(requiredProperties = {"intent", "issues", "correctedQuery", "variables"})
public record TroubleshootResponse(
    @Schema(allowableValues = "TROUBLESHOOT") String intent,
    List<TroubleshootingIssue> issues,
    @Schema(description = "Pretty-printed corrected GraphQL operation, one line per array element")
        List<String> correctedQuery,
    Map<String, Object> variables)
    implements AssistantResponse {

  /**
   * Creates a troubleshooting payload from canonical GraphQL text.
   *
   * @param issues user-facing diagnoses and repair guidance
   * @param correctedQuery validated, pretty-printed corrected operation
   * @param variables runtime variable values for the correction
   */
  public TroubleshootResponse(
      List<TroubleshootingIssue> issues, String correctedQuery, Map<String, Object> variables) {
    this(
        "TROUBLESHOOT",
        issues,
        correctedQuery == null ? null : correctedQuery.lines().toList(),
        variables);
  }

  /** Enforces the troubleshooting discriminator and immutable response collections. */
  public TroubleshootResponse {
    if (!"TROUBLESHOOT".equals(intent)) {
      throw new IllegalArgumentException("Troubleshoot response intent must be TROUBLESHOOT");
    }
    issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    if (correctedQuery == null
        || correctedQuery.isEmpty()
        || correctedQuery.stream().anyMatch(Objects::isNull)
        || correctedQuery.stream().allMatch(String::isBlank)) {
      throw new IllegalArgumentException("Troubleshoot response corrected query must not be blank");
    }
    correctedQuery = List.copyOf(correctedQuery);
    variables =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(variables, "variables")));
  }
}
