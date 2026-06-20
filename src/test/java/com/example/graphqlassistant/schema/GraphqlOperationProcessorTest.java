package com.example.graphqlassistant.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import graphql.schema.idl.SchemaParser;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphqlOperationProcessorTest {

  private GraphqlOperationProcessor processor;

  @BeforeEach
  void setUp() {
    String schema =
        """
        type Query {
          countries: [Country!]!
          country(code: ID!): Country
          example(
            code: ID!
            term: String!
            limit: Int!
            ratio: Float!
            active: Boolean!
          ): Country
        }

        type Mutation {
          renameCountry(code: ID!, name: String!): Country
        }

        type Country {
          code: ID!
          name: String!
        }
        """;
    processor =
        new GraphqlOperationProcessor(
            new GraphqlSchemaContext(schema, new SchemaParser().parse(schema)));
  }

  @Test
  void validatesAndPrettyPrintsNamedQueries() {
    String operation = processor.process("query ListCountries{countries{code name}}", Map.of());

    assertThat(operation)
        .isEqualTo(
            """
            query ListCountries {
              countries {
                code
                name
              }
            }
            """
                .strip());
  }

  @Test
  void acceptsVariablesCompatibleWithNamedQueriesAndMutations() {
    String query =
        processor.process(
            "query GetCountry($code:ID!){country(code:$code){name}}", Map.of("code", "CA"));
    String mutation =
        processor.process(
            "mutation RenameCountry($code:ID!,$name:String!){"
                + "renameCountry(code:$code,name:$name){code name}}",
            Map.of("code", "CA", "name", "Canada"));

    assertThat(query).startsWith("query GetCountry($code: ID!)");
    assertThat(mutation).startsWith("mutation RenameCountry($code: ID!, $name: String!)");
  }

  @Test
  void replacesUnresolvedValuesWithTypeCompatibleExamples() {
    var result =
        processor.processWithVariables(
            """
            query Example(
              $code: ID!
              $term: String!
              $limit: Int!
              $ratio: Float!
              $active: Boolean!
            ) {
              example(
                code: $code
                term: $term
                limit: $limit
                ratio: $ratio
                active: $active
              ) {
                name
              }
            }
            """,
            Map.of(
                "code", "<runtime value>",
                "term", "<runtime value>",
                "limit", "<runtime value>",
                "ratio", "<runtime value>",
                "active", "<runtime value>"));

    assertThat(result.variables())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("code", "CA", "term", "example", "limit", 1, "ratio", 1.0, "active", true));
  }

  @Test
  void rejectsUnnamedMultipleSubscriptionAndSchemaInvalidOperations() {
    assertInvalid("{ countries { code } }");
    assertInvalid("query listCountries { countries { code } }");
    assertInvalid("query One { countries { code } } query Two { countries { name } }");
    assertInvalid("subscription WatchCountries { countries { code } }");
    assertInvalid("query MissingField { countries { missing } }");
  }

  @Test
  void rejectsMissingExtraAndIncompatibleVariables() {
    assertThatThrownBy(
            () ->
                processor.process(
                    "query GetCountry($code:ID!){country(code:$code){name}}", Map.of()))
        .isInstanceOf(InvalidAgentResponseException.class);
    assertThatThrownBy(
            () ->
                processor.process(
                    "query ListCountries { countries { name } }", Map.of("code", "CA")))
        .isInstanceOf(InvalidAgentResponseException.class);
    assertThatThrownBy(
            () ->
                processor.process(
                    "query GetCountry($code:ID!){country(code:$code){name}}",
                    Map.of("code", Map.of("unexpected", true))))
        .isInstanceOf(InvalidAgentResponseException.class);
  }

  @Test
  void rejectsEmbeddedFieldArgumentValues() {
    assertInvalid("query GetCountry { country(code: \"CA\") { name } }");
  }

  private void assertInvalid(String operation) {
    assertThatThrownBy(() -> processor.process(operation, Map.of()))
        .isInstanceOf(InvalidAgentResponseException.class)
        .hasMessage("The AI returned an invalid GraphQL operation");
  }
}
