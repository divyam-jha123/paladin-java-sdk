package io.lfdt.paladin.sdk.rpc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lfdt.paladin.sdk.PaladinException;
import io.lfdt.paladin.sdk.PaladinRpcException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpJsonRpcTransport implements JsonRpcTransport {

  private static final Logger log = LoggerFactory.getLogger(HttpJsonRpcTransport.class);

  private final URI endpoint;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final String authHeader;
  private final Duration requestTimeout;
  private final AtomicLong nextId = new AtomicLong(1);

  HttpJsonRpcTransport(Builder b) {
    this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
    this.http = b.http != null ? b.http : HttpClient.newBuilder().connectTimeout(b.connectTimeout).build();
    this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
    this.requestTimeout = b.requestTimeout;
    this.authHeader = (b.username != null && b.password != null)
        ? "Basic " + Base64.getEncoder().encodeToString((b.username + ":" + b.password).getBytes(StandardCharsets.UTF_8))
        : null;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public <T> T call(String method, List<Object> params, JavaType resultType) {
    try {
      return this.<T>callAsync(method, params, resultType).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PaladinException("Interrupted calling " + method, e);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) throw re;
      throw new PaladinException("Failed calling " + method, cause);
    }
  }

  @Override
  public <T> CompletableFuture<T> callAsync(String method, List<Object> params, JavaType resultType) {
    long id = nextId.getAndIncrement();
    JsonRpcRequest body = JsonRpcRequest.of(id, method, params);
    byte[] payload;
    try {
      payload = mapper.writeValueAsBytes(body);
    } catch (IOException e) {
      return CompletableFuture.failedFuture(new PaladinException("Cannot encode RPC request " + method, e));
    }

    HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
        .timeout(requestTimeout)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
    if (authHeader != null) {
      rb.header("Authorization", authHeader);
    }

    log.debug("Paladin RPC {}", method);
    return http.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofByteArray())
        .thenApply(resp -> decode(method, resp, resultType));
  }

  public <T> T call(String method, List<Object> params, Class<T> resultType) {
    return call(method, params, mapper.getTypeFactory().constructType(resultType));
  }

  public <T> T call(String method, List<Object> params, TypeReference<T> resultType) {
    return call(method, params, mapper.getTypeFactory().constructType(resultType.getType()));
  }

  private <T> T decode(String method, HttpResponse<byte[]> resp, JavaType resultType) {
    int status = resp.statusCode();
    byte[] body = resp.body();
    if (status >= 500 && (body == null || body.length == 0)) {
      throw new PaladinException("Paladin RPC '%s' failed: HTTP %d (empty body)".formatted(method, status));
    }
    JsonRpcResponse decoded;
    try {
      decoded = mapper.readValue(body, JsonRpcResponse.class);
    } catch (IOException e) {
      String preview = new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
      throw new PaladinException(
          "Paladin RPC '%s' returned non-JSON-RPC response (HTTP %d): %s".formatted(method, status, preview), e);
    }
    if (decoded.isError()) {
      throw new PaladinRpcException(method, decoded.error());
    }
    JsonNode result = decoded.result();
    if (result == null || result.isNull()) {
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      T value = (T) mapper.treeToValue(result, resultType);
      return value;
    } catch (IOException e) {
      throw new PaladinException("Cannot decode result of '%s'".formatted(method), e);
    }
  }

  @Override
  public ObjectMapper mapper() {
    return mapper;
  }

  public static final class Builder {
    private URI endpoint;
    private HttpClient http;
    private ObjectMapper mapper;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private String username;
    private String password;

    public Builder url(String url) {
      this.endpoint = URI.create(url);
      return this;
    }

    public Builder url(URI url) {
      this.endpoint = url;
      return this;
    }

    public Builder httpClient(HttpClient client) {
      this.http = client;
      return this;
    }

    public Builder objectMapper(ObjectMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    public Builder connectTimeout(Duration timeout) {
      this.connectTimeout = timeout;
      return this;
    }

    public Builder requestTimeout(Duration timeout) {
      this.requestTimeout = timeout;
      return this;
    }

    public Builder basicAuth(String username, String password) {
      this.username = username;
      this.password = password;
      return this;
    }

    public HttpJsonRpcTransport build() {
      Objects.requireNonNull(endpoint, "endpoint URL is required");
      return new HttpJsonRpcTransport(this);
    }
  }
}
