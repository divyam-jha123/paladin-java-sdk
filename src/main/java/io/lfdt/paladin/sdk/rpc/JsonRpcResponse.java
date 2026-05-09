package io.lfdt.paladin.sdk.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(String jsonrpc, long id, JsonNode result, JsonRpcError error) {

  public boolean isError() {
    return error != null;
  }
}
