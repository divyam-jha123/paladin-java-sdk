package io.lfdt.paladin.sdk.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaladinWebSocketClientTest {

  private FakePaladinWsServer server;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    server = new FakePaladinWsServer(new InetSocketAddress("127.0.0.1", 0));
    server.start();
    server.startedLatch.await(5, TimeUnit.SECONDS);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.stop(500);
  }

  @Test
  void sendsSubscribeOnConnectAndDeliversBatchesToListener() throws Exception {
    BlockingQueue<SubscriptionEvent> events = new ArrayBlockingQueue<>(8);
    BlockingQueue<String> ackedSubs = new ArrayBlockingQueue<>(8);

    PaladinWebSocketClient client = PaladinWebSocketClient.builder()
        .url("ws://127.0.0.1:" + server.actualPort())
        .objectMapper(mapper)
        .reconnect(false)
        .subscribe(SubscriptionType.RECEIPTS, "my-receipts")
        .listener(new SubscriptionListener() {
          @Override
          public void onEvent(WebSocketSender sender, SubscriptionEvent event) {
            events.add(event);
            sender.ack(event.subscriptionId());
            ackedSubs.add(event.subscriptionId());
          }
        })
        .build();

    client.connect().get(5, TimeUnit.SECONDS);

    JsonNode subscribeReq = server.firstSubscribe.poll(5, TimeUnit.SECONDS);
    assertThat(subscribeReq).isNotNull();
    assertThat(subscribeReq.get("method").asText()).isEqualTo("ptx_subscribe");
    assertThat(subscribeReq.get("params").get(0).asText()).isEqualTo("receipts");
    assertThat(subscribeReq.get("params").get(1).asText()).isEqualTo("my-receipts");

    server.replyToSubscribe(subscribeReq.get("id").asLong(), "sub-xyz");
    server.deliverNotification(
        "ptx_subscription", "sub-xyz",
        "{\"batchId\":1,\"receipts\":[{\"id\":\"tx-9\",\"success\":true,"
        + "\"transactionHash\":\"0xabc\",\"sequence\":1,\"blockNumber\":1,\"source\":\"alice\"}]}");

    SubscriptionEvent ev = events.poll(5, TimeUnit.SECONDS);
    assertThat(ev).isNotNull();
    assertThat(ev.method()).isEqualTo("ptx_subscription");
    assertThat(ev.subscriptionId()).isEqualTo("sub-xyz");
    assertThat(ev.subscriptionName()).isEqualTo("my-receipts");
    assertThat(ev.result().get("receipts").get(0).get("transactionHash").asText()).isEqualTo("0xabc");

    JsonNode ack = server.firstAck.poll(5, TimeUnit.SECONDS);
    assertThat(ack).isNotNull();
    assertThat(ack.get("method").asText()).isEqualTo("ptx_ack");
    assertThat(ack.get("params").get(0).asText()).isEqualTo("sub-xyz");

    client.close();
  }

  private static final class FakePaladinWsServer extends WebSocketServer {
    final CountDownLatch startedLatch = new CountDownLatch(1);
    final BlockingQueue<JsonNode> firstSubscribe = new ArrayBlockingQueue<>(8);
    final BlockingQueue<JsonNode> firstAck = new ArrayBlockingQueue<>(8);
    private volatile WebSocket connection;
    private final ObjectMapper mapper = new ObjectMapper();

    FakePaladinWsServer(InetSocketAddress addr) {
      super(addr);
      setReuseAddr(true);
    }

    int actualPort() {
      return getPort();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
      this.connection = conn;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

    @Override
    public void onMessage(WebSocket conn, String message) {
      try {
        JsonNode node = mapper.readTree(message);
        String method = node.path("method").asText("");
        if (method.endsWith("_subscribe")) {
          firstSubscribe.add(node);
        } else if (method.endsWith("_ack") || method.endsWith("_nack")) {
          firstAck.add(node);
        }
      } catch (Exception ignored) {
      }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {}

    @Override
    public void onStart() {
      startedLatch.countDown();
    }

    void replyToSubscribe(long requestId, String subscriptionId) {
      connection.send("{\"jsonrpc\":\"2.0\",\"id\":" + requestId + ",\"result\":\"" + subscriptionId + "\"}");
    }

    void deliverNotification(String method, String subscriptionId, String resultJson) {
      connection.send("{\"jsonrpc\":\"2.0\",\"method\":\"" + method
          + "\",\"params\":{\"subscription\":\"" + subscriptionId + "\",\"result\":" + resultJson + "}}");
    }
  }
}
