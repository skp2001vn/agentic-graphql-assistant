package com.example.graphqlassistant.schema;

import com.example.graphqlassistant.config.AssistantProperties;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
public class GraphqlSchemaLoader {

  private final AssistantProperties properties;

  public GraphqlSchemaLoader(AssistantProperties properties) {
    this.properties = properties;
  }

  @Bean
  GraphqlSchemaContext graphqlSchemaContext(ResourceLoader resourceLoader) {
    String location = properties.getSchema().getLocation();
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("assistant.schema.location must not be blank");
    }

    Resource resource = resourceLoader.getResource(location);
    if (!resource.exists()) {
      throw new IllegalStateException("GraphQL schema resource does not exist: " + location);
    }
    if (!resource.isReadable()) {
      throw new IllegalStateException("GraphQL schema resource is not readable: " + location);
    }

    String schemaText;
    try {
      schemaText = resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read GraphQL schema resource: " + location, exception);
    }

    if (schemaText.isBlank()) {
      throw new IllegalStateException("GraphQL schema resource is empty: " + location);
    }

    try {
      TypeDefinitionRegistry registry = new SchemaParser().parse(schemaText);
      return new GraphqlSchemaContext(schemaText, registry);
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "GraphQL schema resource contains invalid SDL: " + location, exception);
    }
  }
}
