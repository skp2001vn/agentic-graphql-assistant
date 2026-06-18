package com.example.graphqlassistant.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SchemaStartupTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(GraphqlSchemaLoader.class);

  @Test
  void startsWithTheDefaultClasspathSchema() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(GraphqlSchemaContext.class);
          assertThat(context.getBean(GraphqlSchemaContext.class).schemaText())
              .contains("type Query");
        });
  }

  @Test
  void startsWithAConfiguredClasspathSchema() {
    contextRunner
        .withPropertyValues("assistant.schema.location=classpath:schema-valid.graphql")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(GraphqlSchemaContext.class).schemaText())
                  .contains("greeting");
            });
  }

  @Test
  void failsStartupForMissingSchema() {
    contextRunner
        .withPropertyValues("assistant.schema.location=classpath:missing.graphql")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("does not exist")
                    .hasMessageContaining("classpath:missing.graphql"));
  }

  @Test
  void failsStartupForEmptySchema() {
    contextRunner
        .withPropertyValues("assistant.schema.location=classpath:schema-empty.graphql")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("empty"));
  }

  @Test
  void failsStartupForInvalidSchema() {
    contextRunner
        .withPropertyValues("assistant.schema.location=classpath:schema-invalid.graphql")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasStackTraceContaining("invalid SDL"));
  }
}
