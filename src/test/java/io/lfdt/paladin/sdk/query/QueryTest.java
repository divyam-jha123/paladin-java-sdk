package io.lfdt.paladin.sdk.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesOnlyNonEmptyClauses() throws Exception {
    Query q = Query.builder().equal("from", "alice").limit(10).build();
    JsonNode node = mapper.readTree(mapper.writeValueAsString(q));
    assertThat(node.has("equal")).isTrue();
    assertThat(node.has("neq")).isFalse();
    assertThat(node.has("in")).isFalse();
    assertThat(node.get("limit").asInt()).isEqualTo(10);
    assertThat(node.has("sort")).isFalse();
  }

  @Test
  void inOpEmitsValuesArray() throws Exception {
    Query q = Query.builder().in("status", List.of("pending", "completed")).build();
    JsonNode node = mapper.readTree(mapper.writeValueAsString(q));
    assertThat(node.get("in").get(0).get("field").asText()).isEqualTo("status");
    assertThat(node.get("in").get(0).get("values").get(1).asText()).isEqualTo("completed");
  }
}
