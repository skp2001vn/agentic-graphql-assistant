package com.example.graphqlassistant.schema;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.idl.SchemaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphqlOperationValidatorTest {

  private GraphqlOperationValidator validator;

  @BeforeEach
  void setUp() {
    String schema =
        """
        type Query {
          country(code: ID!): Country
          countries: [Country!]!
        }

        type Country {
          code: ID!
          name: String!
        }
        """;
    validator =
        new GraphqlOperationValidator(
            new GraphqlSchemaContext(schema, new SchemaParser().parse(schema)));
  }

  @Test
  void returnsTheParsedDocumentWhenTheOperationIsValid() {
    var result = validator.validate("query Country($code: ID!) { country(code: $code) { name } }");

    assertThat(result.valid()).isTrue();
    assertThat(result.document()).isNotNull();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void permitsLiteralDirectiveArgumentsWhileRequiringVariablesForFieldArguments() {
    var result =
        validator.validate(
            "query Country($code: ID!) { country(code: $code) @skip(if: true) { name } }");

    assertThat(result.valid()).isTrue();
  }

  @Test
  void reportsSyntaxSchemaAndAssistantContractDiagnostics() {
    var syntax = validator.validate("query Country($code: ID!) { country(code) { code name } }");
    var contract = validator.validate("{ countries { code name } }");
    var schema = validator.validate("query Country { countries { missing } }");

    assertThat(syntax.diagnostics())
        .extracting(GraphqlOperationValidator.Diagnostic::code)
        .containsExactly("InvalidArgumentSyntax");
    assertThat(contract.diagnostics())
        .extracting(GraphqlOperationValidator.Diagnostic::code)
        .containsExactly("OperationNameRequired");
    assertThat(schema.diagnostics())
        .extracting(GraphqlOperationValidator.Diagnostic::code)
        .contains("FieldUndefined");
  }
}
