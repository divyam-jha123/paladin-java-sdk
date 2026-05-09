package io.lfdt.paladin.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lfdt.paladin.sdk.bidx.BlockIndexModule;
import io.lfdt.paladin.sdk.debug.DebugModule;
import io.lfdt.paladin.sdk.domain.DomainModule;
import io.lfdt.paladin.sdk.keymgr.KeyManagerModule;
import io.lfdt.paladin.sdk.pgroup.PgroupModule;
import io.lfdt.paladin.sdk.pstate.PStateModule;
import io.lfdt.paladin.sdk.ptx.PtxModule;
import io.lfdt.paladin.sdk.reg.RegistryModule;
import io.lfdt.paladin.sdk.rpc.HttpJsonRpcTransport;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.transport.TransportModule;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class PaladinClient implements AutoCloseable {

  private final JsonRpcTransport transport;
  private final PtxModule ptx;
  private final PgroupModule pgroup;
  private final KeyManagerModule keymgr;
  private final PStateModule pstate;
  private final BlockIndexModule bidx;
  private final RegistryModule reg;
  private final TransportModule transportRpc;
  private final DomainModule domain;
  private final DebugModule debug;

  private PaladinClient(JsonRpcTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.ptx = new PtxModule(transport);
    this.pgroup = new PgroupModule(transport);
    this.keymgr = new KeyManagerModule(transport);
    this.pstate = new PStateModule(transport);
    this.bidx = new BlockIndexModule(transport);
    this.reg = new RegistryModule(transport);
    this.transportRpc = new TransportModule(transport);
    this.domain = new DomainModule(transport);
    this.debug = new DebugModule(transport);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PaladinClient withTransport(JsonRpcTransport transport) {
    return new PaladinClient(transport);
  }

  public JsonRpcTransport transport() { return transport; }

  public PtxModule ptx() { return ptx; }
  public PgroupModule pgroup() { return pgroup; }
  public KeyManagerModule keymgr() { return keymgr; }
  public PStateModule pstate() { return pstate; }
  public BlockIndexModule bidx() { return bidx; }
  public RegistryModule reg() { return reg; }
  public TransportModule transportRpc() { return transportRpc; }
  public DomainModule domain() { return domain; }
  public DebugModule debug() { return debug; }

  @Override
  public void close() {
  }

  public static final class Builder {
    private String url;
    private String username;
    private String password;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);

    public Builder url(String url) { this.url = url; return this; }
    public Builder basicAuth(String username, String password) {
      this.username = username;
      this.password = password;
      return this;
    }
    public Builder httpClient(HttpClient client) { this.httpClient = client; return this; }
    public Builder objectMapper(ObjectMapper mapper) { this.objectMapper = mapper; return this; }
    public Builder connectTimeout(Duration t) { this.connectTimeout = t; return this; }
    public Builder requestTimeout(Duration t) { this.requestTimeout = t; return this; }

    public PaladinClient build() {
      if (url == null || url.isBlank()) {
        throw new IllegalStateException("PaladinClient.url is required");
      }
      ObjectMapper mapper = objectMapper != null ? objectMapper : defaultMapper();
      HttpJsonRpcTransport.Builder t = HttpJsonRpcTransport.builder()
          .url(url)
          .objectMapper(mapper)
          .connectTimeout(connectTimeout)
          .requestTimeout(requestTimeout);
      if (httpClient != null) t.httpClient(httpClient);
      if (username != null && password != null) t.basicAuth(username, password);
      return new PaladinClient(t.build());
    }

    private static ObjectMapper defaultMapper() {
      ObjectMapper m = new ObjectMapper();
      m.registerModule(new JavaTimeModule());
      m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      return m;
    }
  }
}
