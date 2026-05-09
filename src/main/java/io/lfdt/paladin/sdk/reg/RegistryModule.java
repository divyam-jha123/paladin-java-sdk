package io.lfdt.paladin.sdk.reg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.types.ActiveFilter;
import java.util.List;

public final class RegistryModule extends RpcModule {

  public RegistryModule(JsonRpcTransport transport) {
    super(transport);
  }

  public List<String> registries() {
    return call("reg_registries", List.of(), new TypeReference<List<String>>() {});
  }

  public List<JsonNode> queryEntries(String registryName, Query query, ActiveFilter active) {
    return call("reg_queryEntries", List.of(registryName, query, active), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> queryEntriesWithProps(String registryName, Query query, ActiveFilter active) {
    return call("reg_queryEntriesWithProps", List.of(registryName, query, active), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> getEntryProperties(String registryName, String entryId, ActiveFilter active) {
    return call("reg_getEntryProperties", List.of(registryName, entryId, active), new TypeReference<List<JsonNode>>() {});
  }
}
