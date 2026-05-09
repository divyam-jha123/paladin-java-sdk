package io.lfdt.paladin.sdk.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"jsonrpc", "id", "method", "params"})
public record JsonRpcRequest(String jsonrpc, long id, String method, List<Object> params) {

  public static JsonRpcRequest of(long id, String method, List<Object> params) {
    return new JsonRpcRequest("2.0", id, method, params);
  }
}
