package com.example.graphqlassistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import graphql.schema.idl.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphqlAssistantToolsTest {

  private GraphqlAssistantTools tools;

  @BeforeEach
  void setUp() {
    String schema =
        """
        type Query {
          country(code: ID!): Country
          countries(filter: CountryFilter): [Country!]!
        }

        type Country {
          code: ID!
          name: String!
        }

        input CountryFilter {
          code: ID
          name: String
        }
        """;
    GraphqlSchemaContext context =
        new GraphqlSchemaContext(schema, new SchemaParser().parse(schema));
    tools = GraphqlAssistantTools.from(context);
  }

  @Test
  void inspectsRootOperationsAndRequestedTypes() {
    SchemaInspectionResult result = tools.inspectSchema(List.of("Country", "CountryFilter"));

    assertThat(result.types())
        .extracting(TypeSummary::name)
        .containsExactly("Query", "Country", "CountryFilter");
    assertThat(result.types().get(0).fields())
        .extracting(FieldSummary::name)
        .containsExactly("country", "countries");
    assertThat(result.types().get(1).fields())
        .extracting(FieldSummary::name)
        .containsExactly("code", "name");
    assertThat(result.types().get(2).kind()).isEqualTo("INPUTOBJECT");
    assertThat(result.types().get(2).fields())
        .extracting(FieldSummary::name)
        .containsExactly("code", "name");
  }

  @Test
  void validatesOperationsAgainstTheConfiguredSchema() {
    OperationValidationResult valid =
        tools.validateOperation("query Country($code: ID!) { country(code: $code) { name } }");
    OperationValidationResult invalid =
        tools.validateOperation("query Country { country(code: \"CA\") { missing } }");

    assertThat(valid.valid()).isTrue();
    assertThat(valid.diagnostics()).isEmpty();
    assertThat(invalid.valid()).isFalse();
    assertThat(invalid.diagnostics())
        .extracting(OperationDiagnostic::code)
        .contains("FieldUndefined", "LiteralArgument");
  }

  @Test
  void reportsAssistantContractViolations() {
    OperationValidationResult result = tools.validateOperation("{ countries { code name } }");

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics())
        .extracting(OperationDiagnostic::code)
        .containsExactly("OperationNameRequired");
  }

  @Test
  void reportsSyntaxErrorsAsStructuredDiagnostics() {
    OperationValidationResult result = tools.validateOperation("query Broken { country(");

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo("InvalidSyntax");
              assertThat(diagnostic.message()).isNotBlank();
              assertThat(diagnostic.line()).isPositive();
              assertThat(diagnostic.column()).isPositive();
            });
  }

  @Test
  void reportsMissingArgumentNameSyntaxActionably() {
    OperationValidationResult result =
        tools.validateOperation("query Country($code: ID!) { country(code) { code name } }");

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics())
        .singleElement()
        .satisfies(
            diagnostic -> {
              assertThat(diagnostic.code()).isEqualTo("InvalidArgumentSyntax");
              assertThat(diagnostic.message()).contains("country(code: $code)");
            });
  }

  @Test
  void formatsOperationsCanonically() {
    OperationFormattingResult result =
        tools.formatOperation("query Country($code:ID!){country(code:$code){code name}}");

    assertThat(result.formattedOperation())
        .isEqualTo(
            """
            query Country($code: ID!) {
              country(code: $code) {
                code
                name
              }
            }
            """
                .strip());
  }

  @Test
  void rejectsInvalidToolArgumentsBeforeUse() {
    assertThatThrownBy(() -> new InspectSchemaInput(List.of("not a GraphQL name")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("typeNames");
    assertThatThrownBy(() -> new ValidateOperationInput(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operation");
    assertThatThrownBy(() -> new FormatOperationInput(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operation");
  }

  @Test
  void rejectsAFifthTrackedModelToolCall() {
    tools.beginToolTracking();

    for (int call = 0; call < GraphqlAssistantTools.MAX_TOOL_CALLS; call++) {
      assertThat(tools.inspectSchema(List.of()).types()).isNotEmpty();
    }

    assertThatThrownBy(() -> tools.inspectSchema(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Assistant exceeded its tool call limit");
  }
}
