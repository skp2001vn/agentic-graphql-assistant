package com.example.graphqlassistant.tools;

import java.util.List;

public record FieldSummary(String name, String type, List<ArgumentSummary> arguments) {

  public FieldSummary {
    arguments = List.copyOf(arguments);
  }
}
