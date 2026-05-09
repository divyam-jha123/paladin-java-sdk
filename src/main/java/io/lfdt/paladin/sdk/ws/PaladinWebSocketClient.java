package io.lfdt.paladin.sdk.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lfdt.paladin.sdk.PaladinException;
import io.lfdt.paladin.sdk.rpc.JsonRpcRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaladinWebSocketClient implements AutoCloseable, WebSocketSender {

  private static final Logger log = LoggerFactory.getLogger(PaladinWebSocketClient.class);

  private final URI uri;
  private final List<Subscription> subscriptions;
  private final SubscriptionListener listener;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final String authHeader;
  private final Duration connectTimeout;
  private final long reconnectInitialMs;
  private final long reconnectMaxMs;
  private final boolean reconnectEnabled;

  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final Map<Long, String> pendingSubscribes = new ConcurrentHashMap<>();
  private final Map<String, String> activeSubscriptions = new ConcurrentHashMap<>();

  private final ScheduledExecutorService scheduler =
      new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "paladin-ws-reconnect");
        t.setDaemon(true);
        return t;
      });

  private volatile WebSocket socket;
  private volatile int reconnectAttempts;

  private PaladinWebSocketClient(Builder b) {
    this.uri = Objects.requireNonNull(b.uri, "uri");
    this.subscriptions = List.copyOf(b.subscriptions);
    this.listener = Objects.requireNonNull(b.listener, "listener");
    this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
    this.httpClient = b.httpClient != null ? b.httpClient : HttpClient.newHttpClient();
    this.authHeader = (b.username != null && b.password != null)
        ? "Basic " + Base64.getEncoder().encodeToString((b.username + ":" + b.password).getBytes(StandardCharsets.UTF_8))
        : null;
    this.connectTimeout = b.connectTimeout;
    this.reconnectInitialMs = b.reconnectInitial.toMillis();
    this.reconnectMaxMs = b.reconnectMax.toMillis();
    this.reconnectEnabled = b.reconnect;
  }

  public static Builder builder() {
    return new Builder();
  }

  public CompletableFuture<Void> connect() {
    return openSocket();
  }

  private CompletableFuture<Void> openSocket() {
    WebSocket.Builder b = httpClient.newWebSocketBuilder()
        .connectTimeout(connectTimeout);
    if (authHeader != null) {
      b.header("Authorization", authHeader);
    }
    PaladinListener handler = new PaladinListener();
    return b.buildAsync(uri, handler).thenAccept(ws -> {
      this.socket = ws;
      this.reconnectAttempts = 0;
      pendingSubscribes.clear();
      activeSubscriptions.clear();
      listener.onOpen();
      for (Subscription sub : subscriptions) {
        long id = subscribeOnWire(sub);
        pendingSubscribes.put(id, sub.name());
      }
    }).exceptionally(t -> {
      listener.onError(t);
      scheduleReconnect();
      return null;
    });
  }

  private long subscribeOnWire(Subscription sub) {
    long id = nextId.getAndIncrement();
    String method = switch (sub.type()) {
      case MESSAGES -> "pgroup_subscribe";
      default -> "ptx_subscribe";
    };
    sendRpc(id, method, List.of(sub.type(), sub.name()));
    return id;
  }

  @Override
  public void ack(String subscriptionId) {
    Objects.requireNonNull(subscriptionId, "subscriptionId");
    sendRpc(nextId.getAndIncrement(), prefixFor(subscriptionId) + "_ack", List.of(subscriptionId));
  }

  @Override
  public void nack(String subscriptionId) {
    Objects.requireNonNull(subscriptionId, "subscriptionId");
    sendRpc(nextId.getAndIncrement(), prefixFor(subscriptionId) + "_nack", List.of(subscriptionId));
  }

  @Override
  public String subscriptionName(String subscriptionId) {
    return activeSubscriptions.get(subscriptionId);
  }

  private String prefixFor(String subscriptionId) {
    String name = activeSubscriptions.get(subscriptionId);
    if (name == null) return "ptx";
    for (Subscription s : subscriptions) {
      if (s.name().equals(name)) {
        return s.type() == SubscriptionType.MESSAGES ? "pgroup" : "ptx";
      }
    }
    return "ptx";
  }

  private void sendRpc(long id, String method, List<Object> params) {
    WebSocket ws = this.socket;
    if (ws == null || ws.isOutputClosed()) {
      throw new PaladinException("WebSocket is not open; cannot send " + method);
    }
    JsonRpcRequest req = JsonRpcRequest.of(id, method, params);
    String json;
    try {
      json = mapper.writeValueAsString(req);
    } catch (IOException e) {
      throw new PaladinException("Cannot encode WS RPC " + method, e);
    }
    ws.sendText(json, true);
  }

  private void scheduleReconnect() {
    if (!reconnectEnabled || closed.get()) return;
    long delay = Math.min(
        reconnectInitialMs * (long) Math.pow(2, Math.min(reconnectAttempts, 16)),
        reconnectMaxMs);
    reconnectAttempts++;
    log.info("Paladin WS reconnect in {}ms (attempt {})", delay, reconnectAttempts);
    scheduler.schedule(this::openSocket, delay, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    scheduler.shutdownNow();
    WebSocket ws = this.socket;
    if (ws != null) {
      try {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "client closing");
      } catch (Exception ignored) {
      }
    }
  }

  private final class PaladinListener implements WebSocket.Listener {
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String message = buffer.toString();
        buffer.setLength(0);
        try {
          handleMessage(message);
        } catch (Exception e) {
          listener.onError(e);
        }
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
      webSocket.sendPong(message);
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      listener.onClose(statusCode, reason);
      if (!closed.get()) {
        scheduleReconnect();
      }
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      listener.onError(error);
      if (!closed.get()) {
        scheduleReconnect();
      }
    }
  }

  private void handleMessage(String text) throws IOException {
    JsonNode node = mapper.readTree(text);
    if (node.has("id") && node.has("result") && !node.has("method")) {
      long id = node.get("id").asLong();
      String name = pendingSubscribes.remove(id);
      if (name != null) {
        String subId = node.get("result").asText();
        activeSubscriptions.put(subId, name);
        log.debug("Subscription '{}' assigned ID {}", name, subId);
      }
      return;
    }
    if (node.has("method") && node.has("params")) {
      String method = node.get("method").asText();
      JsonNode params = node.get("params");
      String subId = params.path("subscription").asText(null);
      JsonNode result = params.path("result");
      String name = subId != null ? activeSubscriptions.get(subId) : null;
      listener.onEvent(this, new SubscriptionEvent(method, subId, name, result));
    }
  }

  public static final class Builder {
    private URI uri;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private SubscriptionListener listener;
    private ObjectMapper mapper;
    private HttpClient httpClient;
    private String username;
    private String password;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration reconnectInitial = Duration.ofSeconds(2);
    private Duration reconnectMax = Duration.ofSeconds(30);
    private boolean reconnect = true;

    public Builder url(String url) { this.uri = URI.create(url); return this; }
    public Builder url(URI url) { this.uri = url; return this; }
    public Builder subscribe(Subscription sub) { this.subscriptions.add(sub); return this; }
    public Builder subscribe(SubscriptionType type, String name) {
      this.subscriptions.add(new Subscription(type, name));
      return this;
    }
    public Builder listener(SubscriptionListener l) { this.listener = l; return this; }
    public Builder objectMapper(ObjectMapper m) { this.mapper = m; return this; }
    public Builder httpClient(HttpClient c) { this.httpClient = c; return this; }
    public Builder basicAuth(String u, String p) { this.username = u; this.password = p; return this; }
    public Builder connectTimeout(Duration t) { this.connectTimeout = t; return this; }
    public Builder reconnect(boolean enable) { this.reconnect = enable; return this; }
    public Builder reconnectInitial(Duration d) { this.reconnectInitial = d; return this; }
    public Builder reconnectMax(Duration d) { this.reconnectMax = d; return this; }

    public PaladinWebSocketClient build() {
      Objects.requireNonNull(uri, "url is required");
      Objects.requireNonNull(listener, "listener is required");
      return new PaladinWebSocketClient(this);
    }
  }
}
