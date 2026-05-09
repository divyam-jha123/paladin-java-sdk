package io.lfdt.paladin.sdk.pgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lfdt.paladin.sdk.PaladinClient;
import io.lfdt.paladin.sdk.types.PrivacyGroup;
import io.lfdt.paladin.sdk.types.PrivacyGroupInput;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PgroupModuleTest {

  private MockWebServer server;
  private PaladinClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = PaladinClient.builder().url(server.url("/").toString()).objectMapper(mapper).build();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void createGroupSendsExpectedShape() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
        + "\"id\":\"pg-1\",\"domain\":\"pente\",\"created\":\"2026-05-09T00:00:00Z\","
        + "\"name\":\"deal-1\",\"members\":[\"alice\",\"bob\"],\"properties\":{},\"configuration\":{}}}"));

    PrivacyGroup group = client.pgroup().createGroup(PrivacyGroupInput.builder()
        .domain("pente")
        .name("deal-1")
        .members(List.of("alice", "bob"))
        .build());

    assertThat(group.id()).isEqualTo("pg-1");
    assertThat(group.members()).containsExactly("alice", "bob");

    RecordedRequest req = server.takeRequest();
    JsonNode body = mapper.readTree(req.getBody().readUtf8());
    assertThat(body.get("method").asText()).isEqualTo("pgroup_createGroup");
    assertThat(body.get("params").get(0).get("domain").asText()).isEqualTo("pente");
    assertThat(body.get("params").get(0).get("members").get(1).asText()).isEqualTo("bob");
  }
}
