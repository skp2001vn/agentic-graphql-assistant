package com.example.graphqlassistant.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.graphqlassistant.api.GenerateResponse;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import graphql.schema.idl.SchemaParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalScorerTest {

  private static final String SCHEMA =
      """
      type Query {
        country(code: ID!): Country
      }

      type Country {
        code: ID!
        name: String!
      }
      """;

  private final EvalScorer scorer =
      new EvalScorer(
          new GraphqlOperationProcessor(
              new GraphqlSchemaContext(SCHEMA, new SchemaParser().parse(SCHEMA))));

  @Test
  void passesWhenAllHardChecksMatch() {
    EvalExpectation expectation =
        new EvalExpectation(
            "GENERATE",
            Map.of("code", "CA"),
            List.of(),
            List.of("inspectSchema"),
            List.of("country", "name"));
    GenerateResponse response =
        new GenerateResponse(
            "query GetCountry($code: ID!) { country(code: $code) { code name } }",
            Map.of("code", "CA"));

    EvalScore score = scorer.score(expectation, response, List.of("inspectSchema"), null);

    assertThat(score.passed()).isTrue();
    assertThat(score.hardFailures()).isEmpty();
  }

  @Test
  void hardFailuresCannotBeOverriddenByAJudgeScore() {
    EvalExpectation expectation =
        new EvalExpectation(
            "GENERATE",
            Map.of("code", "CA"),
            List.of(),
            List.of("inspectSchema"),
            List.of("country", "name"));
    GenerateResponse response =
        new GenerateResponse(
            "query GetCountry($code: ID!) { country(code: $code) { code } }", Map.of("code", "US"));

    EvalScore score = scorer.score(expectation, response, List.of(), 1.0);

    assertThat(score.passed()).isFalse();
    assertThat(score.hardFailures())
        .contains("variables did not match", "missing required tool: inspectSchema");
    assertThat(score.judgeScore()).isEqualTo(1.0);
  }
}
