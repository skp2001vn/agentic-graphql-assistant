package com.example.graphqlassistant.evals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class EvalDataset {

  private static final List<String> RESOURCES =
      List.of(
          "evals/generation.jsonl",
          "evals/troubleshooting.jsonl",
          "evals/clarification.jsonl",
          "evals/adversarial.jsonl");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private EvalDataset() {
    throw new AssertionError("No instances");
  }

  static List<EvalCase> loadAll() {
    List<EvalCase> cases = new ArrayList<>();
    RESOURCES.forEach(resource -> cases.addAll(load(resource)));
    return List.copyOf(cases);
  }

  static List<EvalCase> loadLive() {
    return load("evals/live.jsonl");
  }

  private static List<EvalCase> load(String resource) {
    InputStream input = EvalDataset.class.getClassLoader().getResourceAsStream(resource);
    if (input == null) {
      throw new IllegalStateException("Missing evaluation dataset: " + resource);
    }

    try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      List<EvalCase> cases = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          cases.add(OBJECT_MAPPER.readValue(line, EvalCase.class));
        }
      }
      return cases;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read evaluation dataset: " + resource, exception);
    }
  }
}
