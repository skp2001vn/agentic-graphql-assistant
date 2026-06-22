package com.example.graphqlassistant.schema;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.VariableReference;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the shared deterministic validation rules for GraphQL operations.
 *
 * <p>The model-facing {@code validateOperation} tool and the final application-boundary processor
 * both use this validator so syntax, schema, and assistant-contract diagnostics cannot drift. It
 * parses but never executes operations.
 */
public final class GraphqlOperationValidator {

  private static final int MAX_OPERATION_LENGTH = 100 * 1024;

  private static final Pattern MISSING_ARGUMENT_NAME =
      Pattern.compile("\\b([_A-Za-z][_0-9A-Za-z]*)\\s*\\(\\s*([_A-Za-z][_0-9A-Za-z]*)\\s*\\)");

  private final GraphQLSchema schema;

  /**
   * Builds the validation schema from the configured SDL registry.
   *
   * @param schemaContext loaded schema text and parsed type registry
   */
  public GraphqlOperationValidator(GraphqlSchemaContext schemaContext) {
    Objects.requireNonNull(schemaContext, "schemaContext");
    schema =
        UnExecutableSchemaGenerator.makeUnExecutableSchema(schemaContext.typeDefinitionRegistry());
  }

  /**
   * Parses an operation and reports all deterministic validation diagnostics available.
   *
   * @param operation GraphQL operation text
   * @return parsed document when syntax is valid, plus immutable diagnostics
   */
  public ValidationResult validate(String operation) {
    if (operation.length() > MAX_OPERATION_LENGTH) {
      return new ValidationResult(
          null,
          List.of(
              new Diagnostic(
                  "OperationTooLarge", "Operation must not exceed 100 KB", 0, 0, List.of())));
    }
    Document document;
    try {
      document = Parser.parse(operation);
    } catch (InvalidSyntaxException exception) {
      SourceLocation location = exception.getLocation();
      Diagnostic argumentSyntax = missingArgumentNameDiagnostic(operation, location);
      return new ValidationResult(
          null,
          List.of(
              argumentSyntax != null
                  ? argumentSyntax
                  : diagnostic("InvalidSyntax", exception.getMessage(), location)));
    }

    List<Diagnostic> diagnostics = new ArrayList<>();
    diagnostics.addAll(operationContractDiagnostics(document));
    diagnostics.addAll(
        new Validator()
            .validateDocument(schema, document, Locale.ROOT).stream()
                .map(this::toDiagnostic)
                .toList());
    return new ValidationResult(document, diagnostics);
  }

  GraphQLSchema schema() {
    return schema;
  }

  private Diagnostic missingArgumentNameDiagnostic(String operation, SourceLocation location) {
    Matcher matcher = MISSING_ARGUMENT_NAME.matcher(operation);
    if (!matcher.find()) {
      return null;
    }

    String fieldName = matcher.group(1);
    String argumentName = matcher.group(2);
    if (!Pattern.compile("\\$" + Pattern.quote(argumentName) + "\\s*:").matcher(operation).find()) {
      return null;
    }
    SourceLocation safeLocation = safeLocation(location);
    return new Diagnostic(
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

  private List<Diagnostic> operationContractDiagnostics(Document document) {
    List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
    if (operations.size() != 1) {
      return List.of(
          diagnostic(
              "OperationCount",
              "The assistant requires exactly one GraphQL operation",
              document.getSourceLocation()));
    }

    OperationDefinition operation = operations.getFirst();
    List<Diagnostic> diagnostics = new ArrayList<>();
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

  private void collectLiteralArguments(Node<?> node, List<Diagnostic> diagnostics) {
    if (node instanceof Field field) {
      field.getArguments().stream()
          .filter(argument -> !(argument.getValue() instanceof VariableReference))
          .map(
              argument ->
                  diagnostic(
                      "LiteralArgument",
                      "Argument '" + argument.getName() + "' must use a declared variable",
                      argument.getSourceLocation()))
          .forEach(diagnostics::add);
    }
    node.getChildren().forEach(child -> collectLiteralArguments(child, diagnostics));
  }

  private Diagnostic diagnostic(String code, String message, SourceLocation location) {
    SourceLocation safeLocation = safeLocation(location);
    return new Diagnostic(
        code, message, safeLocation.getLine(), safeLocation.getColumn(), List.of());
  }

  private Diagnostic toDiagnostic(ValidationError error) {
    SourceLocation location =
        error.getLocations().isEmpty() ? new SourceLocation(0, 0) : error.getLocations().getFirst();
    return new Diagnostic(
        error.getValidationErrorType().toString(),
        error.getMessage(),
        location.getLine(),
        location.getColumn(),
        error.getQueryPath() == null ? List.of() : error.getQueryPath());
  }

  private SourceLocation safeLocation(SourceLocation location) {
    return location == null ? new SourceLocation(0, 0) : location;
  }

  /**
   * Parsed operation and diagnostics returned by the shared validator.
   *
   * @param document parsed operation, or {@code null} when parsing failed
   * @param diagnostics immutable validation diagnostics
   */
  public record ValidationResult(Document document, List<Diagnostic> diagnostics) {

    /** Copies diagnostics so validation results remain immutable. */
    public ValidationResult {
      diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Reports whether validation produced no diagnostics.
     *
     * @return {@code true} when the operation satisfies all shared validation rules
     */
    public boolean valid() {
      return diagnostics.isEmpty();
    }
  }

  /**
   * Structured validation diagnostic suitable for application and model-tool consumers.
   *
   * @param code stable diagnostic category
   * @param message human-readable validation detail
   * @param line one-based source line, or zero when unavailable
   * @param column one-based source column, or zero when unavailable
   * @param path schema-validation query path, or an empty list
   */
  public record Diagnostic(String code, String message, int line, int column, List<String> path) {

    /** Copies the query path so diagnostics remain immutable. */
    public Diagnostic {
      path = List.copyOf(path);
    }
  }
}
