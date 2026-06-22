package com.example.graphqlassistant.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.graphqlassistant.agent.ClarificationRequiredException;
import com.example.graphqlassistant.agent.InvalidAgentResponseException;
import com.example.graphqlassistant.api.AssistantResponse;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("deterministic-eval")
class DeterministicEvalTest {

  @Test
  void allCuratedCasesMeetTheHardThresholds() {
    GraphqlSchemaContext schemaContext = schemaContext();
    EvalScorer scorer = new EvalScorer(new GraphqlOperationProcessor(schemaContext));
    List<EvalReportCase> results =
        EvalDataset.loadAll().stream()
            .map(evalCase -> evaluate(evalCase, schemaContext, scorer))
            .toList();
    long passed = results.stream().filter(EvalReportCase::passed).count();
    EvalReport report =
        new EvalReport(
            "deterministic",
            "deterministic",
            "fixture-transcript",
            results.size(),
            Math.toIntExact(passed),
            (double) passed / results.size(),
            0,
            true,
            results);
    EvalReportWriter.write("deterministic-report.json", report);

    assertThat(results)
        .as("target/evals/deterministic-report.json contains per-case failures")
        .allMatch(EvalReportCase::passed);
    assertThat(report.passRate()).isEqualTo(1.0);
  }

  private EvalReportCase evaluate(
      EvalCase evalCase, GraphqlSchemaContext schemaContext, EvalScorer scorer) {
    EvalTrace trace = new EvalTrace();
    var provider =
        new TracingEvalProvider(new TranscriptEvalProvider(evalCase.transcript()), trace);
    AssistantService service =
        EvalHarness.createService(provider, schemaContext, Duration.ofSeconds(5));
    long startedAt = System.nanoTime();
    AssistantResponse response = null;
    List<String> failures = new ArrayList<>();
    try {
      response = service.assist(evalCase.prompt());
      if (evalCase.expectedError() != null) {
        failures.add("expected error was not returned: " + evalCase.expectedError());
      } else {
        EvalScore score = scorer.score(evalCase.expectation(), response, trace.toolNames(), null);
        failures.addAll(score.hardFailures());
      }
    } catch (ClarificationRequiredException exception) {
      if (!"CLARIFICATION".equals(evalCase.expectedError())) {
        failures.add("unexpected clarification");
      }
    } catch (InvalidAgentResponseException exception) {
      if (!"INVALID_RESPONSE".equals(evalCase.expectedError())) {
        failures.add("unexpected invalid response");
      }
    } catch (RuntimeException exception) {
      failures.add("unexpected error: " + exception.getClass().getSimpleName());
    }
    evalCase.requiredTools().stream()
        .filter(tool -> !trace.toolNames().contains(tool))
        .forEach(tool -> failures.add("missing required tool: " + tool));
    long latencyMillis = (System.nanoTime() - startedAt) / 1_000_000;
    return new EvalReportCase(
        evalCase.id(),
        evalCase.category(),
        failures.isEmpty(),
        latencyMillis,
        failures,
        response,
        trace.tools(),
        trace.rawResponses(),
        null);
  }

  private GraphqlSchemaContext schemaContext() {
    try (var input = getClass().getClassLoader().getResourceAsStream("schema.graphql")) {
      if (input == null) {
        throw new IllegalStateException("Missing schema.graphql");
      }
      String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return new GraphqlSchemaContext(schema, new SchemaParser().parse(schema));
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load schema.graphql", exception);
    }
  }
}
