package com.example.graphqlassistant.evals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class EvalReportWriter {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private EvalReportWriter() {
    throw new AssertionError("No instances");
  }

  static void write(String fileName, EvalReport report) {
    Path output = Path.of("target", "evals", fileName);
    try {
      Files.createDirectories(output.getParent());
      OBJECT_MAPPER.writeValue(output.toFile(), report);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write evaluation report: " + output, exception);
    }
  }
}
