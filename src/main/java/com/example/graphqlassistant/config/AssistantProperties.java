package com.example.graphqlassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("assistant")
public class AssistantProperties {

  private final Schema schema = new Schema();

  public Schema getSchema() {
    return schema;
  }

  public static class Schema {

    private String location = "classpath:schema.graphql";

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }
}
