package io.lfdt.paladin.sdk.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class RpcModule {

  protected final JsonRpcTransport transport;

  protected RpcModule(JsonRpcTransport transport) {
    this.transport = transport;
  }

  protected <T> T call(String method, List<Object> params, Class<T> resultType) {
    JavaType jt = transport.mapper().getTypeFactory().constructType(resultType);
    return transport.call(method, params, jt);
  }

  protected <T> T call(String method, List<Object> params, TypeReference<T> resultType) {
    JavaType jt = transport.mapper().getTypeFactory().constructType(resultType.getType());
    return transport.call(method, params, jt);
  }

  protected <T> CompletableFuture<T> callAsync(String method, List<Object> params, Class<T> resultType) {
    JavaType jt = transport.mapper().getTypeFactory().constructType(resultType);
    return transport.callAsync(method, params, jt);
  }

  protected void callVoid(String method, List<Object> params) {
    transport.call(method, params, transport.mapper().getTypeFactory().constructType(Object.class));
  }
}
