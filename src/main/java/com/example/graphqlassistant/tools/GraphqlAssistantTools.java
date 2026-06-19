package com.example.graphqlassistant.tools;

import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.Objects;

public final class GraphqlAssistantTools {

  private final SchemaInspectionTool schemaInspection;

  private final OperationValidationTool operationValidation;

  private final OperationFormattingTool operationFormatting;

  private GraphqlAssistantTools(
      SchemaInspectionTool schemaInspection,
      OperationValidationTool operationValidation,
      OperationFormattingTool operationFormatting) {
    this.schemaInspection = schemaInspection;
    this.operationValidation = operationValidation;
    this.operationFormatting = operationFormatting;
  }

  public static GraphqlAssistantTools from(GraphqlSchemaContext schemaContext) {
    Objects.requireNonNull(schemaContext, "schemaContext");
    return new GraphqlAssistantTools(
        new SchemaInspectionTool(schemaContext.typeDefinitionRegistry()),
        new OperationValidationTool(schemaContext.typeDefinitionRegistry()),
        new OperationFormattingTool());
  }

  @Tool("Inspect configured GraphQL root operations and requested type definitions")
  public SchemaInspectionResult inspectSchema(
      @P("Validated type names to inspect; roots are always included") InspectSchemaInput input) {
    return schemaInspection.inspect(input);
  }

  @Tool("Parse and validate a GraphQL operation against the configured schema")
  public OperationValidationResult validateOperation(
      @P("GraphQL operation to validate") ValidateOperationInput input) {
    return operationValidation.validate(input);
  }

  @Tool("Parse and return a canonically formatted GraphQL operation")
  public OperationFormattingResult formatOperation(
      @P("GraphQL operation to format") FormatOperationInput input) {
    return operationFormatting.format(input);
  }
}
