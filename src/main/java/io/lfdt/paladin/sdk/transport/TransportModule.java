package io.lfdt.paladin.sdk.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import java.util.List;
import java.util.Optional;

public final class TransportModule extends RpcModule {

  public TransportModule(JsonRpcTransport transport) {
    super(transport);
  }

  public String nodeName() {
    return call("transport_nodeName", List.of(), String.class);
  }

  public List<String> localTransports() {
    return call("transport_localTransports", List.of(), new TypeReference<List<String>>() {});
  }

  public String localTransportDetails(String transportName) {
    return call("transport_localTransportDetails", List.of(transportName), String.class);
  }

  public List<JsonNode> peers() {
    return call("transport_peers", List.of(), new TypeReference<List<JsonNode>>() {});
  }

  public Optional<JsonNode> peerInfo(String nodeName) {
    return Optional.ofNullable(call("transport_peerInfo", List.of(nodeName), JsonNode.class));
  }

  public List<JsonNode> queryReliableMessages(Query query) {
    return call("transport_queryReliableMessages", List.of(query), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> queryReliableMessageAcks(Query query) {
    return call("transport_queryReliableMessageAcks", List.of(query), new TypeReference<List<JsonNode>>() {});
  }
}
