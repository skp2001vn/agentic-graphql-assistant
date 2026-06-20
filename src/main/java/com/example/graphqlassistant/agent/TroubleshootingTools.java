package com.example.graphqlassistant.agent;

import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import com.example.graphqlassistant.tools.OperationValidationResult;
import com.example.graphqlassistant.tools.SchemaInspectionResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Objects;

final class TroubleshootingTools {

  private final GraphqlAssistantTools delegate;

  TroubleshootingTools(GraphqlAssistantTools delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Tool("Inspect configured GraphQL root operations and requested type definitions")
  SchemaInspectionResult inspectSchema(
      @P("Type names to inspect; roots are always included") List<String> typeNames) {
    return delegate.inspectSchema(typeNames);
  }

  @Tool("Parse and validate a GraphQL operation against the configured schema")
  OperationValidationResult validateOperation(
      @P("GraphQL operation to validate") String operation) {
    return delegate.validateOperation(operation);
  }
}
