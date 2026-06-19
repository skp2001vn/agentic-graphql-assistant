package com.example.graphqlassistant.tools;

import graphql.language.Argument;
import graphql.language.Document;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.VariableReference;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OperationValidationTool {

  private static final Pattern MISSING_ARGUMENT_NAME =
      Pattern.compile("\\b([_A-Za-z][_0-9A-Za-z]*)\\s*\\(\\s*([_A-Za-z][_0-9A-Za-z]*)\\s*\\)");

  private final GraphQLSchema schema;

  OperationValidationTool(TypeDefinitionRegistry registry) {
    schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
  }

  OperationValidationResult validate(ValidateOperationInput input) {
    Document document;
    try {
      document = Parser.parse(input.operation());
    } catch (InvalidSyntaxException exception) {
      SourceLocation location = exception.getLocation();
      OperationDiagnostic argumentSyntax =
          missingArgumentNameDiagnostic(input.operation(), location);
      return new OperationValidationResult(
          false,
          List.of(
              argumentSyntax != null
                  ? argumentSyntax
                  : new OperationDiagnostic(
                      "InvalidSyntax",
                      exception.getMessage(),
                      location.getLine(),
                      location.getColumn(),
                      List.of())));
    }

    List<OperationDiagnostic> diagnostics = new ArrayList<>();
    diagnostics.addAll(operationContractDiagnostics(document));
    diagnostics.addAll(
        new Validator()
            .validateDocument(schema, document, Locale.ROOT).stream()
                .map(this::toDiagnostic)
                .toList());
    return new OperationValidationResult(diagnostics.isEmpty(), diagnostics);
  }

  private OperationDiagnostic missingArgumentNameDiagnostic(
      String operation, SourceLocation location) {
    Matcher matcher = MISSING_ARGUMENT_NAME.matcher(operation);
    if (!matcher.find()) {
      return null;
    }

    String fieldName = matcher.group(1);
    String argumentName = matcher.group(2);
    if (!Pattern.compile("\\$" + Pattern.quote(argumentName) + "\\s*:").matcher(operation).find()) {
      return null;
    }
    SourceLocation safeLocation = location == null ? new SourceLocation(0, 0) : location;
    return new OperationDiagnostic(
        "InvalidArgumentSyntax",
        "Field '"
            + fieldName
            + "' must pass arguments as name: value; use "
            + fieldName
            + "("
            + argumentName
            + ": $"
            + argumentName
            + ") for the declared variable",
        safeLocation.getLine(),
        safeLocation.getColumn(),
        List.of(fieldName));
  }

  private List<OperationDiagnostic> operationContractDiagnostics(Document document) {
    List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
    if (operations.size() != 1) {
      return List.of(
          diagnostic(
              "OperationCount",
              "The assistant requires exactly one GraphQL operation",
              document.getSourceLocation()));
    }

    OperationDefinition operation = operations.getFirst();
    List<OperationDiagnostic> diagnostics = new ArrayList<>();
    if (operation.getName() == null || operation.getName().isBlank()) {
      diagnostics.add(
          diagnostic(
              "OperationNameRequired",
              "The assistant requires a named GraphQL operation",
              operation.getSourceLocation()));
    } else if (!Character.isUpperCase(operation.getName().codePointAt(0))) {
      diagnostics.add(
          diagnostic(
              "OperationNamePascalCase",
              "The operation name must start with an uppercase letter",
              operation.getSourceLocation()));
    }
    if (operation.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
      diagnostics.add(
          diagnostic(
              "SubscriptionUnsupported",
              "Subscriptions are not supported",
              operation.getSourceLocation()));
    }
    collectLiteralArguments(operation, diagnostics);
    return diagnostics;
  }

  private void collectLiteralArguments(Node<?> node, List<OperationDiagnostic> diagnostics) {
    if (node instanceof Argument argument && !(argument.getValue() instanceof VariableReference)) {
      diagnostics.add(
          diagnostic(
              "LiteralArgument",
              "Argument '" + argument.getName() + "' must use a declared variable",
              argument.getSourceLocation()));
    }
    node.getChildren().forEach(child -> collectLiteralArguments(child, diagnostics));
  }

  private OperationDiagnostic diagnostic(String code, String message, SourceLocation location) {
    SourceLocation safeLocation = location == null ? new SourceLocation(0, 0) : location;
    return new OperationDiagnostic(
        code, message, safeLocation.getLine(), safeLocation.getColumn(), List.of());
  }

  private OperationDiagnostic toDiagnostic(ValidationError error) {
    SourceLocation location =
        error.getLocations().isEmpty() ? new SourceLocation(0, 0) : error.getLocations().get(0);
    return new OperationDiagnostic(
        error.getValidationErrorType().toString(),
        error.getMessage(),
        location.getLine(),
        location.getColumn(),
        error.getQueryPath() == null ? List.of() : error.getQueryPath());
  }
}
