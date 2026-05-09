package io.lfdt.paladin.sdk.debug;

import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import java.util.List;
import java.util.Optional;

public final class DebugModule extends RpcModule {

  public DebugModule(JsonRpcTransport transport) {
    super(transport);
  }

  public Optional<JsonNode> getTransactionStatus(String txId) {
    return Optional.ofNullable(call("debug_getTransactionStatus", List.of(txId), JsonNode.class));
  }
}
