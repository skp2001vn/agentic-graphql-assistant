package com.example.graphqlassistant.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Schema(requiredProperties = {"intent", "issues", "correctedQuery", "variables"})
public record TroubleshootResponse(
    @Schema(allowableValues = "TROUBLESHOOT") String intent,
    List<TroubleshootingIssue> issues,
    @Schema(description = "Pretty-printed corrected GraphQL operation, one line per array element")
        List<String> correctedQuery,
    Map<String, Object> variables)
    implements AssistantResponse {

  public TroubleshootResponse(
      List<TroubleshootingIssue> issues, String correctedQuery, Map<String, Object> variables) {
    this(
        "TROUBLESHOOT",
        issues,
        correctedQuery == null ? null : correctedQuery.lines().toList(),
        variables);
  }

  public TroubleshootResponse {
    if (!"TROUBLESHOOT".equals(intent)) {
      throw new IllegalArgumentException("Troubleshoot response intent must be TROUBLESHOOT");
    }
    issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    if (issues.isEmpty()) {
      throw new IllegalArgumentException("Troubleshoot response issues must not be empty");
    }
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
