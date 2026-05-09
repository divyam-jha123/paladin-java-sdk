package io.lfdt.paladin.sdk.rpc;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface JsonRpcTransport {

  <T> T call(String method, List<Object> params, JavaType resultType);

  <T> CompletableFuture<T> callAsync(String method, List<Object> params, JavaType resultType);

  ObjectMapper mapper();
}
