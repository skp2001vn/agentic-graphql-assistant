package com.example.graphqlassistant.tools;

import java.util.List;

/**
 * Token-efficient GraphQL type representation used for schema-grounded generation.
 *
 * @param name GraphQL type name
 * @param kind normalized schema type kind
 * @param fields immutable field summaries relevant to operation construction
 */
public record TypeSummary(String name, String kind, List<FieldSummary> fields) {

  /** Copies fields so one retrieval result cannot be mutated across agent turns. */
  public TypeSummary {
    fields = List.copyOf(fields);
  }
}
