package com.example.graphqlassistant.tools;

import java.util.List;

/**
 * Retrieved GraphQL schema context supplied to an AI specialist.
 *
 * @param types compact root and requested type summaries
 */
public record SchemaInspectionResult(List<TypeSummary> types) {

  /** Copies type summaries to keep retrieved grounding context immutable. */
  public SchemaInspectionResult {
    types = List.copyOf(types);
  }
}
