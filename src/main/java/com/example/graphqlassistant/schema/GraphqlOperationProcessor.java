package com.example.graphqlassistant.schema;

import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import graphql.GraphQLContext;
import graphql.GraphQLException;
import graphql.execution.RawVariables;
import graphql.execution.TypeFromAST;
import graphql.execution.ValuesResolver;
import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministically validates and canonicalizes GraphQL operations returned by AI specialists.
 *
 * <p>This processor is the final application-boundary guardrail. It delegates syntax, schema, and
 * assistant-contract checks to the shared {@link GraphqlOperationValidator}, then synthesizes safe
 * example values when requested, applies GraphQL variable coercion, and prints the validated
 * abstract syntax tree (AST). No generated operation is executed.
 */
public final class GraphqlOperationProcessor {

  private static final String INVALID_OPERATION = "The AI returned an invalid GraphQL operation";

  private static final String UNRESOLVED_VALUE = "<runtime value>";

  private final GraphqlOperationValidator operationValidator;

  private final GraphQLSchema schema;

  /**
   * Creates a processor with validation rules built from the configured SDL registry.
   *
   * @param schemaContext loaded schema text and parsed type registry
   */
  public GraphqlOperationProcessor(GraphqlSchemaContext schemaContext) {
    this(new GraphqlOperationValidator(schemaContext));
  }

  /**
   * Creates a processor that reuses the application's shared operation validator.
   *
   * @param operationValidator shared syntax, schema, and assistant-contract validator
   */
  public GraphqlOperationProcessor(GraphqlOperationValidator operationValidator) {
    this.operationValidator = Objects.requireNonNull(operationValidator, "operationValidator");
    schema = operationValidator.schema();
  }

  /**
   * Validates and canonically formats a model-produced GraphQL operation.
   *
   * @param operation generated or corrected GraphQL document
   * @param variables model-produced runtime variable values
   * @return canonical GraphQL text
   * @throws InvalidAgentResponseException when the operation or variables violate the contract
   */
  public String process(String operation, Map<String, Object> variables) {
    return processWithVariables(operation, variables).operation();
  }

  /**
   * Validates, formats, and resolves example variables for a model-produced operation.
   *
   * @param operation generated or corrected GraphQL document
   * @param variables model-produced runtime variable values
   * @return immutable canonical operation and coerced variable values
   * @throws InvalidAgentResponseException when parsing, schema validation, or coercion fails
   */
  public ProcessedOperation processWithVariables(String operation, Map<String, Object> variables) {
    if (operation == null || operation.isBlank() || variables == null) {
      throw invalidOperation();
    }
    var validation = operationValidator.validate(operation);
    if (!validation.valid()) {
      throw invalidOperation();
    }
    try {
      Document document = validation.document();
      OperationDefinition definition =
          document.getDefinitionsOfType(OperationDefinition.class).getFirst();
      Map<String, Object> resolvedVariables = resolveExampleVariables(definition, variables);
      requireCompatibleVariables(definition, resolvedVariables);
      return new ProcessedOperation(AstPrinter.printAst(document).strip(), resolvedVariables);
    } catch (GraphQLException exception) {
      throw invalidOperation();
    }
  }

  private Map<String, Object> resolveExampleVariables(
      OperationDefinition operation, Map<String, Object> variables) {
    Map<String, Object> resolved = new LinkedHashMap<>(variables);
    operation
        .getVariableDefinitions()
        .forEach(
            definition -> {
              String name = definition.getName();
              if (UNRESOLVED_VALUE.equals(resolved.get(name))) {
                GraphQLInputType type =
                    (GraphQLInputType) TypeFromAST.getTypeFromAST(schema, definition.getType());
                resolved.put(name, exampleValue(name, type));
              }
            });
    return Collections.unmodifiableMap(resolved);
  }

  private Object exampleValue(String variableName, GraphQLInputType type) {
    if (type instanceof GraphQLNonNull nonNull) {
      return exampleValue(variableName, (GraphQLInputType) nonNull.getWrappedType());
    }
    if (type instanceof GraphQLList list) {
      return List.of(exampleValue(variableName, (GraphQLInputType) list.getWrappedType()));
    }
    if (type instanceof GraphQLEnumType enumType) {
      return enumType.getValues().getFirst().getName();
    }
    if (type instanceof GraphQLInputObjectType inputObject) {
      Map<String, Object> fields = new LinkedHashMap<>();
      inputObject.getFields().stream()
          .filter(field -> field.getType() instanceof GraphQLNonNull)
          .forEach(
              field -> fields.put(field.getName(), exampleValue(field.getName(), field.getType())));
      return fields;
    }
    if (type instanceof GraphQLScalarType scalar) {
      return switch (scalar.getName()) {
        case "Boolean" -> true;
        case "Int" -> 1;
        case "Float" -> 1.0;
        case "ID" -> variableName.toLowerCase(Locale.ROOT).endsWith("code") ? "CA" : "example-id";
        case "String" -> exampleString(variableName);
        default -> "example";
      };
    }
    throw invalidOperation();
  }

  private String exampleString(String variableName) {
    String normalizedName = variableName.toLowerCase(Locale.ROOT);
    if (normalizedName.endsWith("code")) {
      return "CA";
    }
    if (normalizedName.endsWith("name")) {
      return "Example";
    }
    return "example";
  }

  private void requireCompatibleVariables(
      OperationDefinition operation, Map<String, Object> variables) {
    Set<String> declaredVariables =
        operation.getVariableDefinitions().stream()
            .map(definition -> definition.getName())
            .collect(Collectors.toUnmodifiableSet());
    if (!declaredVariables.containsAll(variables.keySet())) {
      throw invalidOperation();
    }
    for (var definition : operation.getVariableDefinitions()) {
      if (variables.containsKey(definition.getName())
          && !hasCompatibleJsonShape(
              variables.get(definition.getName()),
              (GraphQLInputType) TypeFromAST.getTypeFromAST(schema, definition.getType()))) {
        throw invalidOperation();
      }
    }

    ValuesResolver.coerceVariableValues(
        schema,
        operation.getVariableDefinitions(),
        RawVariables.of(variables),
        GraphQLContext.getDefault(),
        Locale.ROOT);
  }

  private boolean hasCompatibleJsonShape(Object value, GraphQLInputType type) {
    if (value == null) {
      return true;
    }
    if (type instanceof GraphQLNonNull nonNull) {
      return hasCompatibleJsonShape(value, (GraphQLInputType) nonNull.getWrappedType());
    }
    if (type instanceof GraphQLList list) {
      GraphQLInputType elementType = (GraphQLInputType) list.getWrappedType();
      return value instanceof List<?> values
          ? values.stream().allMatch(element -> hasCompatibleJsonShape(element, elementType))
          : hasCompatibleJsonShape(value, elementType);
    }
    if (type instanceof GraphQLInputObjectType inputObject) {
      if (!(value instanceof Map<?, ?> values)) {
        return false;
      }
      return values.entrySet().stream()
          .allMatch(
              entry ->
                  entry.getKey() instanceof String fieldName
                      && inputObject.getField(fieldName) != null
                      && hasCompatibleJsonShape(
                          entry.getValue(), inputObject.getField(fieldName).getType()));
    }
    if (type instanceof GraphQLEnumType) {
      return value instanceof String;
    }
    if (type instanceof GraphQLScalarType scalar) {
      return switch (scalar.getName()) {
        case "Boolean" -> value instanceof Boolean;
        case "Int" ->
            value instanceof Number number
                && number.longValue() >= Integer.MIN_VALUE
                && number.longValue() <= Integer.MAX_VALUE
                && number.doubleValue() == number.longValue();
        case "Float" -> value instanceof Number;
        case "ID" ->
            value instanceof String
                || value instanceof Number number && number.doubleValue() == number.longValue();
        case "String" -> value instanceof String;
        default -> true;
      };
    }
    return false;
  }

  private InvalidAgentResponseException invalidOperation() {
    return new InvalidAgentResponseException(INVALID_OPERATION);
  }

  /**
   * Immutable GraphQL operation after deterministic post-processing of AI output.
   *
   * @param operation canonical schema-valid GraphQL operation
   * @param variables immutable coerced runtime variable values
   */
  public record ProcessedOperation(String operation, Map<String, Object> variables) {

    /** Copies variable values into an insertion-ordered immutable map. */
    public ProcessedOperation {
      variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
  }
}
