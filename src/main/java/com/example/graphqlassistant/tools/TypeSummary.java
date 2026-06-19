package com.example.graphqlassistant.tools;

import java.util.List;

public record TypeSummary(String name, String kind, List<FieldSummary> fields) {

  public TypeSummary {
    fields = List.copyOf(fields);
  }
}
