package io.lfdt.paladin.sdk.ptx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lfdt.paladin.sdk.PaladinClient;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.types.Transaction;
import io.lfdt.paladin.sdk.types.TransactionInput;
import io.lfdt.paladin.sdk.types.TransactionReceipt;
import io.lfdt.paladin.sdk.types.TransactionType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PtxModuleTest {

  private MockWebServer server;
  private PaladinClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = PaladinClient.builder()
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
  void sendTransactionUsesCorrectMethodAndPayload() throws Exception {
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"tx-123\"}"));

    TransactionInput input = TransactionInput.builder()
        .type(TransactionType.PRIVATE)
        .domain("noto")
        .from("alice")
        .to("0xabc")
        .function("transfer(address,uint256)")
        .data(Map.of("to", "bob", "amount", "100"))
        .build();

    String txId = client.ptx().sendTransaction(input);
    assertThat(txId).isEqualTo("tx-123");

    RecordedRequest req = server.takeRequest();
    JsonNode body = mapper.readTree(req.getBody().readUtf8());
    assertThat(body.get("method").asText()).isEqualTo("ptx_sendTransaction");
    assertThat(body.get("params").get(0).get("type").asText()).isEqualTo("private");
    assertThat(body.get("params").get(0).get("domain").asText()).isEqualTo("noto");
    assertThat(body.get("params").get(0).get("function").asText()).isEqualTo("transfer(address,uint256)");
  }

  @Test
  void getTransactionReturnsOptional() throws Exception {
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"
        + "\"id\":\"tx-1\",\"created\":\"2026-05-09T00:00:00Z\","
        + "\"type\":\"private\",\"from\":\"alice\",\"data\":{}}}"));

    Optional<Transaction> tx = client.ptx().getTransaction("tx-1");
    assertThat(tx).isPresent();
    assertThat(tx.get().id()).isEqualTo("tx-1");
    assertThat(tx.get().type()).isEqualTo(TransactionType.PRIVATE);
  }

  @Test
  void getTransactionReturnsEmptyOnNullResult() {
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));
    assertThat(client.ptx().getTransaction("missing")).isEmpty();
  }

  @Test
  void queryTransactionsSerializesQueryDsl() throws Exception {
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[]}"));

    Query q = Query.builder()
        .equal("from", "alice")
        .greaterThan("created", "2026-01-01")
        .limit(50)
        .sort("-created")
        .build();
    List<Transaction> result = client.ptx().queryTransactions(q);
    assertThat(result).isEmpty();

    RecordedRequest req = server.takeRequest();
    JsonNode body = mapper.readTree(req.getBody().readUtf8());
    JsonNode params = body.get("params").get(0);
    assertThat(body.get("method").asText()).isEqualTo("ptx_queryTransactions");
    assertThat(params.get("limit").asInt()).isEqualTo(50);
    assertThat(params.get("sort").get(0).asText()).isEqualTo("-created");
    assertThat(params.get("equal").get(0).get("field").asText()).isEqualTo("from");
    assertThat(params.get("greaterThan").get(0).get("field").asText()).isEqualTo("created");
  }

  @Test
  void pollForReceiptReturnsOnceMaterialised() {
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}"));
    server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{"
        + "\"id\":\"tx-1\",\"sequence\":1,\"blockNumber\":7,\"success\":true,"
        + "\"transactionHash\":\"0xdead\",\"source\":\"alice\"}}"));

    Optional<TransactionReceipt> r =
        client.ptx().pollForReceipt("tx-1", Duration.ofSeconds(2), Duration.ofMillis(20));
    assertThat(r).isPresent();
    assertThat(r.get().success()).isTrue();
    assertThat(r.get().blockNumber()).isEqualTo(7);
    assertThat(r.get().transactionHash()).isEqualTo("0xdead");
  }

  @Test
  void pollForReceiptTimesOut() {
    for (int i = 0; i < 10; i++) {
      server.enqueue(json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));
    }

    Optional<TransactionReceipt> r =
        client.ptx().pollForReceipt("tx-1", Duration.ofMillis(150), Duration.ofMillis(60));
    assertThat(r).isEmpty();
  }

  private static MockResponse json(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }
}
