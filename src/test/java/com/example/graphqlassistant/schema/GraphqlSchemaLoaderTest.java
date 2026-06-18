package com.example.graphqlassistant.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.graphqlassistant.config.AssistantProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class GraphqlSchemaLoaderTest {

  private static final String VALID_SCHEMA = "type Query { greeting: String! }";

  @Test
  void loadsUtf8SchemaAndRetainsItsParsedRepresentation() {
    GraphqlSchemaContext context =
        load(new ByteArrayResource(VALID_SCHEMA.getBytes(StandardCharsets.UTF_8)));

    assertThat(context.schemaText()).isEqualTo(VALID_SCHEMA);
    assertThat(context.typeDefinitionRegistry().types()).containsKey("Query");
  }

  @Test
  void readsTheConfiguredResourceExactlyOnce() {
    AtomicInteger reads = new AtomicInteger();
    Resource resource =
        new AbstractResource() {
          @Override
          public String getDescription() {
            return "counted schema";
          }

          @Override
          public boolean exists() {
            return true;
          }

          @Override
          public InputStream getInputStream() {
            reads.incrementAndGet();
            return new ByteArrayInputStream(VALID_SCHEMA.getBytes(StandardCharsets.UTF_8));
          }
        };

    load(resource);

    assertThat(reads).hasValue(1);
  }

  @Test
  void rejectsMissingSchema() {
    Resource missing =
        new AbstractResource() {
          @Override
          public String getDescription() {
            return "missing schema";
          }

          @Override
          public boolean exists() {
            return false;
          }

          @Override
          public InputStream getInputStream() throws IOException {
            throw new IOException("missing");
          }
        };

    assertThatThrownBy(() -> load(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not exist")
        .hasMessageContaining("test:schema");
  }

  @Test
  void rejectsUnreadableSchema() {
    Resource unreadable =
        new AbstractResource() {
          @Override
          public String getDescription() {
            return "unreadable schema";
          }

          @Override
          public boolean exists() {
            return true;
          }

          @Override
          public boolean isReadable() {
            return false;
          }

          @Override
          public InputStream getInputStream() throws IOException {
            throw new IOException("unreadable");
          }
        };

    assertThatThrownBy(() -> load(unreadable))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not readable")
        .hasMessageContaining("test:schema");
  }

  @Test
  void rejectsEmptySchema() {
    assertThatThrownBy(() -> load(new ByteArrayResource(new byte[0])))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty")
        .hasMessageContaining("test:schema");
  }

  @Test
  void rejectsInvalidSchema() {
    Resource invalid =
        new ByteArrayResource("type Query { broken: }".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> load(invalid))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid SDL")
        .hasMessageContaining("test:schema");
  }

  private GraphqlSchemaContext load(Resource resource) {
    AssistantProperties properties = new AssistantProperties();
    properties.getSchema().setLocation("test:schema");
    ResourceLoader resourceLoader =
        new ResourceLoader() {
          @Override
          public Resource getResource(String location) {
            return resource;
          }

          @Override
          public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
          }
        };
    return new GraphqlSchemaLoader(properties).graphqlSchemaContext(resourceLoader);
  }
}
