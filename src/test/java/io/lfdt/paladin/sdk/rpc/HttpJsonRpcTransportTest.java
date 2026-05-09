package io.lfdt.paladin.sdk.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lfdt.paladin.sdk.PaladinException;
import io.lfdt.paladin.sdk.PaladinRpcException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpJsonRpcTransportTest {

  private MockWebServer server;
  private HttpJsonRpcTransport transport;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    transport = HttpJsonRpcTransport.builder()
        .url(server.url("/").toString())
        .basicAuth("paladin", "paladin")
        .objectMapper(mapper)
        .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void sendsJsonRpc20RequestWithBasicAuth() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"alice\"}"));

    String node = transport.call("transport_nodeName", List.of(), String.class);
    assertThat(node).isEqualTo("alice");

    RecordedRequest req = server.takeRequest();
    assertThat(req.getMethod()).isEqualTo("POST");
    assertThat(req.getHeader("Authorization")).isEqualTo("Basic cGFsYWRpbjpwYWxhZGlu");

    JsonNode body = mapper.readTree(req.getBody().readUtf8());
    assertThat(body.get("jsonrpc").asText()).isEqualTo("2.0");
    assertThat(body.get("method").asText()).isEqualTo("transport_nodeName");
    assertThat(body.get("params").isArray()).isTrue();
    assertThat(body.has("id")).isTrue();
  }

  @Test
  void translatesJsonRpcErrorIntoPaladinRpcException() {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"PD012345 not found\"}}"));

    assertThatThrownBy(() -> transport.call("ptx_getTransaction", List.of("nope"), String.class))
        .isInstanceOf(PaladinRpcException.class)
        .satisfies(t -> {
          PaladinRpcException e = (PaladinRpcException) t;
          assertThat(e.code()).isEqualTo(-32000);
          assertThat(e.rpcMessage()).contains("PD012345");
        });
  }

  @Test
  void wrapsNonJsonResponseInPaladinException() {
    server.enqueue(new MockResponse().setResponseCode(502).setBody("Bad Gateway"));

    assertThatThrownBy(() -> transport.call("ptx_anything", List.of(), String.class))
        .isInstanceOf(PaladinException.class)
        .hasMessageContaining("HTTP 502");
  }

  @Test
  void asyncCallReturnsCompletableFuture() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":42}"));

    Long height = transport.<Long>callAsync(
            "bidx_getConfirmedBlockHeight",
            List.of(),
            mapper.getTypeFactory().constructType(Long.class))
        .toCompletableFuture()
        .get();

    assertThat(height).isEqualTo(42L);
  }
}
