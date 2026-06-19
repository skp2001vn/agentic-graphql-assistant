package com.example.graphqlassistant.tools;

import java.util.List;

public record SchemaInspectionResult(List<TypeSummary> types) {

  public SchemaInspectionResult {
    types = List.copyOf(types);
  }
}
