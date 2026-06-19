package com.example.graphqlassistant.schema;

import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import graphql.GraphQLContext;
import graphql.GraphQLException;
import graphql.execution.RawVariables;
import graphql.execution.TypeFromAST;
import graphql.execution.ValuesResolver;
import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class GraphqlOperationProcessor {

  private static final String INVALID_OPERATION = "The AI returned an invalid GraphQL operation";

  private final GraphQLSchema schema;

  public GraphqlOperationProcessor(GraphqlSchemaContext schemaContext) {
    Objects.requireNonNull(schemaContext, "schemaContext");
    schema =
        UnExecutableSchemaGenerator.makeUnExecutableSchema(schemaContext.typeDefinitionRegistry());
  }

  public String process(String operation, Map<String, Object> variables) {
    if (operation == null || operation.isBlank() || variables == null) {
      throw invalidOperation();
    }
    try {
      Document document = Parser.parse(operation);
      OperationDefinition definition = requireOneNamedOperation(document);
      requireValidDocument(document);
      requireVariableArguments(document);
      requireCompatibleVariables(definition, variables);
      return AstPrinter.printAst(document).strip();
    } catch (InvalidAgentResponseException exception) {
      throw exception;
    } catch (GraphQLException exception) {
      throw invalidOperation();
    }
  }

  private OperationDefinition requireOneNamedOperation(Document document) {
    var operations = document.getDefinitionsOfType(OperationDefinition.class);
    if (operations.size() != 1) {
      throw invalidOperation();
    }

    OperationDefinition operation = operations.getFirst();
    if (operation.getName() == null
        || operation.getName().isBlank()
        || !Character.isUpperCase(operation.getName().codePointAt(0))
        || operation.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
      throw invalidOperation();
    }
    return operation;
  }

  private void requireValidDocument(Document document) {
    if (!new Validator().validateDocument(schema, document, Locale.ROOT).isEmpty()) {
      throw invalidOperation();
    }
  }

  private void requireVariableArguments(Node<?> node) {
    if (node instanceof Field field
        && field.getArguments().stream()
            .anyMatch(argument -> !(argument.getValue() instanceof VariableReference))) {
      throw invalidOperation();
    }
    node.getChildren().forEach(this::requireVariableArguments);
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
}
