package com.example.graphqlassistant.tools;

import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.util.List;
import java.util.Locale;

final class OperationValidationTool {

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
      return new OperationValidationResult(
          false,
          List.of(
              new OperationDiagnostic(
                  "InvalidSyntax",
                  exception.getMessage(),
                  location.getLine(),
                  location.getColumn(),
                  List.of())));
    }

    List<OperationDiagnostic> diagnostics =
        new Validator()
            .validateDocument(schema, document, Locale.ROOT).stream()
                .map(this::toDiagnostic)
                .toList();
    return new OperationValidationResult(diagnostics.isEmpty(), diagnostics);
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
