package com.example.graphqlassistant.tools;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record InspectSchemaInput(List<String> typeNames) {

  private static final Pattern GRAPHQL_NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

  public InspectSchemaInput {
    typeNames = List.copyOf(Objects.requireNonNull(typeNames, "typeNames"));
    if (typeNames.size() > 20
        || typeNames.stream()
            .anyMatch(name -> name == null || !GRAPHQL_NAME.matcher(name).matches())) {
      throw new IllegalArgumentException(
          "typeNames must contain at most 20 valid GraphQL type names");
    }
  }
}
