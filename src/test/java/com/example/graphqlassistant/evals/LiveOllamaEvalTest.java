package com.example.graphqlassistant.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.graphqlassistant.api.model.AssistantResponse;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.provider.LangChain4jAssistantProvider;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import dev.langchain4j.model.ollama.OllamaChatModel;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

@Tag("live-eval")
class LiveOllamaEvalTest {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  private static final Duration WARM_LATENCY_TARGET = warmLatencyTarget();

  @Test
  void localQwenMeetsQualityAndWarmLatencyTargets() {
    GraphqlSchemaContext schemaContext = schemaContext();
    EvalScorer scorer = new EvalScorer(new GraphqlOperationProcessor(schemaContext));
    AssistantAiProvider liveProvider = liveProvider();
    long warmupStartedAt = System.nanoTime();
    liveProvider.chat("Reply with READY.");
    long warmupLatencyMillis = elapsedMillis(warmupStartedAt);

    List<EvalReportCase> results = new ArrayList<>();
    for (EvalCase evalCase : selectedCases()) {
      results.add(evaluate(evalCase, schemaContext, scorer, liveProvider));
    }
    long passed = results.stream().filter(EvalReportCase::passed).count();
    boolean latencyTargetMet =
        results.stream()
            .allMatch(result -> result.latencyMillis() < WARM_LATENCY_TARGET.toMillis());
    EvalReport report =
        new EvalReport(
            "live",
            liveProvider.providerName(),
            liveProvider.modelName(),
            results.size(),
            Math.toIntExact(passed),
            (double) passed / results.size(),
            warmupLatencyMillis,
            latencyTargetMet,
            results);
    EvalReportWriter.write("live-ollama-report.json", report);

    assertThat(report.passRate())
        .as("target/evals/live-ollama-report.json contains per-case failures")
        .isGreaterThanOrEqualTo(0.9);
    assertThat(report.latencyTargetMet())
        .as("every request after warmup should complete in under %s", WARM_LATENCY_TARGET)
        .isTrue();
  }

  private EvalReportCase evaluate(
      EvalCase evalCase,
      GraphqlSchemaContext schemaContext,
      EvalScorer scorer,
      AssistantAiProvider liveProvider) {
    EvalTrace trace = new EvalTrace();
    AssistantService service =
        EvalHarness.createService(
            new TracingEvalProvider(liveProvider, trace), schemaContext, REQUEST_TIMEOUT);
    long startedAt = System.nanoTime();
    AssistantResponse response = null;
    List<String> failures = new ArrayList<>();
    try {
      response = service.assist(evalCase.prompt());
      EvalScore score = scorer.score(evalCase.expectation(), response, trace.toolNames(), null);
      failures.addAll(score.hardFailures());
    } catch (RuntimeException exception) {
      failures.add("request failed: " + exception.getClass().getSimpleName());
    }
    return new EvalReportCase(
        evalCase.id(),
        evalCase.category(),
        failures.isEmpty(),
        elapsedMillis(startedAt),
        failures,
        response,
        trace.tools(),
        trace.rawResponses(),
        null);
  }

  private AssistantAiProvider liveProvider() {
    var model =
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("qwen3:8b")
            .timeout(REQUEST_TIMEOUT)
            .temperature(0.0)
            .think(false)
            .maxRetries(0)
            .logRequests(false)
            .logResponses(false)
            .build();
    return new LangChain4jAssistantProvider("ollama", "qwen3:8b", model);
  }

  private List<EvalCase> selectedCases() {
    String selectedId = System.getProperty("eval.case");
    if (selectedId == null || selectedId.isBlank()) {
      return EvalDataset.loadLive();
    }
    return EvalDataset.loadLive().stream()
        .filter(evalCase -> selectedId.equals(evalCase.id()))
        .toList();
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

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private static Duration warmLatencyTarget() {
    String configured =
        System.getProperty(
            "assistant.ai.warm-response-target",
            System.getenv().getOrDefault("ASSISTANT_AI_WARM_RESPONSE_TARGET", "30s"));
    return DurationStyle.detectAndParse(configured);
  }
}
