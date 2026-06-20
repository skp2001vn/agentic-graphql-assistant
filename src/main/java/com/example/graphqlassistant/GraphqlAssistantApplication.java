package com.example.graphqlassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Bootstraps the schema-grounded GraphQL AI assistant as a Spring Boot application. */
@SpringBootApplication
public class GraphqlAssistantApplication {

  /**
   * Starts the HTTP API, validates configuration, and loads the GraphQL schema at startup.
   *
   * @param args command-line arguments forwarded to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(GraphqlAssistantApplication.class, args);
  }
}
