package com.example.graphqlassistant.tools;

import com.example.graphqlassistant.logging.AssistantRequestLogger;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Objects;

public final class GraphqlAssistantTools {

  private final SchemaInspectionTool schemaInspection;

  private final OperationValidationTool operationValidation;

  private final OperationFormattingTool operationFormatting;

  private final AssistantRequestLogger requestLogger;

  private final ThreadLocal<Boolean> schemaInspected = ThreadLocal.withInitial(() -> false);

  private GraphqlAssistantTools(
      SchemaInspectionTool schemaInspection,
      OperationValidationTool operationValidation,
      OperationFormattingTool operationFormatting,
      AssistantRequestLogger requestLogger) {
    this.schemaInspection = schemaInspection;
    this.operationValidation = operationValidation;
    this.operationFormatting = operationFormatting;
    this.requestLogger = requestLogger;
  }

  public static GraphqlAssistantTools from(GraphqlSchemaContext schemaContext) {
    return from(schemaContext, AssistantRequestLogger.disabled());
  }

  public static GraphqlAssistantTools from(
      GraphqlSchemaContext schemaContext, AssistantRequestLogger requestLogger) {
    Objects.requireNonNull(schemaContext, "schemaContext");
    return new GraphqlAssistantTools(
        new SchemaInspectionTool(schemaContext.typeDefinitionRegistry()),
        new OperationValidationTool(schemaContext.typeDefinitionRegistry()),
        new OperationFormattingTool(),
        Objects.requireNonNull(requestLogger, "requestLogger"));
  }

  @Tool("Inspect configured GraphQL root operations and requested type definitions")
  public SchemaInspectionResult inspectSchema(
      @P("Type names to inspect; roots are always included") List<String> typeNames) {
    InspectSchemaInput input = new InspectSchemaInput(typeNames);
    SchemaInspectionResult result =
        requestLogger.toolCall("inspectSchema", input, () -> schemaInspection.inspect(input));
    schemaInspected.set(true);
    return result;
  }

  @Tool("Parse and validate a GraphQL operation against the configured schema")
  public OperationValidationResult validateOperation(
      @P("GraphQL operation to validate") String operation) {
    ValidateOperationInput input = new ValidateOperationInput(operation);
    return requestLogger.toolCall(
        "validateOperation", input, () -> operationValidation.validate(input));
  }

  @Tool("Parse and return a canonically formatted GraphQL operation")
  public OperationFormattingResult formatOperation(
      @P("GraphQL operation to format") String operation) {
    FormatOperationInput input = new FormatOperationInput(operation);
    return requestLogger.toolCall(
        "formatOperation", input, () -> operationFormatting.format(input));
  }

  public void beginToolTracking() {
    schemaInspected.set(false);
  }

  public boolean wasSchemaInspected() {
    return schemaInspected.get();
  }

  public void clearToolTracking() {
    schemaInspected.remove();
  }
}
