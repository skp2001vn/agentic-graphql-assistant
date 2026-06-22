package com.example.graphqlassistant.tools.model;

import java.util.List;

/**
 * Token-efficient GraphQL field metadata exposed through schema inspection.
 *
 * @param name schema field name
 * @param type exact GraphQL output or input type expression
 * @param arguments immutable argument metadata for callable fields
 */
public record FieldSummary(String name, String type, List<ArgumentSummary> arguments) {

  /** Copies argument metadata so tool results remain immutable across agent turns. */
  public FieldSummary {
    arguments = List.copyOf(arguments);
  }
}
