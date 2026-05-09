package io.lfdt.paladin.sdk;

import io.lfdt.paladin.sdk.rpc.JsonRpcError;

public class PaladinRpcException extends PaladinException {

  private static final long serialVersionUID = 1L;
  private final transient JsonRpcError error;

  public PaladinRpcException(String method, JsonRpcError error) {
    super("Paladin RPC '%s' failed [%d]: %s".formatted(method, error.code(), error.message()));
    this.error = error;
  }

  public int code() {
    return error.code();
  }

  public String rpcMessage() {
    return error.message();
  }

  public JsonRpcError error() {
    return error;
  }
}
