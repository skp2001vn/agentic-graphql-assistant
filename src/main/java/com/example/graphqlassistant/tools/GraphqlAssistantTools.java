package com.example.graphqlassistant.tools;

import com.example.graphqlassistant.logging.AssistantRequestLogger;
import com.example.graphqlassistant.schema.GraphqlOperationValidator;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.model.InspectSchemaInput;
import com.example.graphqlassistant.tools.model.OperationDiagnostic;
import com.example.graphqlassistant.tools.model.OperationValidationResult;
import com.example.graphqlassistant.tools.model.SchemaInspectionResult;
import com.example.graphqlassistant.tools.model.ValidateOperationInput;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Objects;

/**
 * Curated tool surface that grounds AI agents in the configured GraphQL schema.
 *
 * <p>The tools implement retrieval-augmented generation (RAG) over schema metadata without a vector
 * database: specialists retrieve only relevant root and type definitions, validate candidate
 * operations deterministically, and use structured diagnostics for repair. Per-thread tracking
 * enforces a bounded tool budget and verifies that generation consulted the schema before
 * returning.
 */
public final class GraphqlAssistantTools {

  /** Hard limit on deterministic tool calls available to one specialist invocation. */
  public static final int MAX_TOOL_CALLS = 4;

  private final SchemaInspectionTool schemaInspection;

  private final GraphqlOperationValidator operationValidator;

  private final AssistantRequestLogger requestLogger;

  private final ThreadLocal<Boolean> schemaInspected = ThreadLocal.withInitial(() -> false);

  private final ThreadLocal<Integer> toolCalls = new ThreadLocal<>();

  private GraphqlAssistantTools(
      SchemaInspectionTool schemaInspection,
      GraphqlOperationValidator operationValidator,
      AssistantRequestLogger requestLogger) {
    this.schemaInspection = schemaInspection;
    this.operationValidator = operationValidator;
    this.requestLogger = requestLogger;
  }

  /**
   * Creates the schema-inspection and validation tool suite without AI telemetry.
   *
   * @param schemaContext configured schema grounding context
   * @return bounded GraphQL tool facade
   */
  public static GraphqlAssistantTools from(GraphqlSchemaContext schemaContext) {
    return from(
        schemaContext,
        new GraphqlOperationValidator(schemaContext),
        AssistantRequestLogger.disabled());
  }

  public static GraphqlAssistantTools from(
      GraphqlSchemaContext schemaContext,
      GraphqlOperationValidator operationValidator,
      AssistantRequestLogger requestLogger) {
    Objects.requireNonNull(schemaContext, "schemaContext");
    return new GraphqlAssistantTools(
        new SchemaInspectionTool(schemaContext.typeDefinitionRegistry()),
        Objects.requireNonNull(operationValidator, "operationValidator"),
        Objects.requireNonNull(requestLogger, "requestLogger"));
  }

  /**
   * Retrieves compact schema context for root operations and requested types.
   *
   * <p>This targeted retrieval reduces prompt tokens and hallucination risk compared with injecting
   * the complete schema into every model turn.
   *
   * @param typeNames GraphQL type names relevant to the current reasoning step
   * @return token-efficient schema summaries
   */
  @Tool("Inspect configured GraphQL root operations and requested type definitions")
  public SchemaInspectionResult inspectSchema(
      @P("Type names to inspect; roots are always included") List<String> typeNames) {
    recordToolCall();
    InspectSchemaInput input = new InspectSchemaInput(typeNames);
    SchemaInspectionResult result =
        requestLogger.toolCall("inspectSchema", input, () -> schemaInspection.inspect(input));
    schemaInspected.set(true);
    return result;
  }

  /**
   * Parses and validates a candidate operation against syntax, schema, and assistant conventions.
   *
   * <p>The final application boundary invokes the same shared validator after the specialist
   * returns; this tool exposes its diagnostics so the model can self-correct first.
   *
   * @param operation model-generated GraphQL operation
   * @return structured diagnostics suitable for self-correction in the tool loop
   */
  @Tool("Parse and validate a GraphQL operation against the configured schema")
  public OperationValidationResult validateOperation(
      @P("GraphQL operation to validate") String operation) {
    recordToolCall();
    ValidateOperationInput input = new ValidateOperationInput(operation);
    return requestLogger.toolCall("validateOperation", input, () -> validationResult(input));
  }

  /** Starts request-local tool-budget accounting before a specialist agent executes. */
  public void beginToolTracking() {
    schemaInspected.set(false);
    toolCalls.set(0);
  }

  /**
   * Reports whether the active generation workflow retrieved schema context.
   *
   * @return {@code true} after at least one schema-inspection tool call
   */
  public boolean wasSchemaInspected() {
    return schemaInspected.get();
  }

  /** Stops enforcing the model tool-call budget while preserving inspection evidence. */
  public void finishModelToolCalls() {
    toolCalls.remove();
  }

  /** Clears all request-local tool state to prevent leakage across assistant requests. */
  public void clearToolTracking() {
    schemaInspected.remove();
    toolCalls.remove();
  }

  private void recordToolCall() {
    Integer calls = toolCalls.get();
    if (calls == null) {
      return;
    }
    if (calls >= MAX_TOOL_CALLS) {
      throw new IllegalStateException("Assistant exceeded its tool call limit");
    }
    toolCalls.set(calls + 1);
  }

  private OperationValidationResult validationResult(ValidateOperationInput input) {
    var validation = operationValidator.validate(input.operation());
    List<OperationDiagnostic> diagnostics =
        validation.diagnostics().stream()
            .map(
                diagnostic ->
                    new OperationDiagnostic(
                        diagnostic.code(),
                        diagnostic.message(),
                        diagnostic.line(),
                        diagnostic.column(),
                        diagnostic.path()))
            .toList();
    return new OperationValidationResult(validation.valid(), diagnostics);
  }
}
