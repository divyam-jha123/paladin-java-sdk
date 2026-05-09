package io.lfdt.paladin.sdk.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import java.util.List;
import java.util.Optional;

public final class DomainModule extends RpcModule {

  public DomainModule(JsonRpcTransport transport) {
    super(transport);
  }

  public List<String> listDomains() {
    return call("domain_listDomains", List.of(), new TypeReference<List<String>>() {});
  }

  public Optional<JsonNode> getDomain(String name) {
    return Optional.ofNullable(call("domain_getDomain", List.of(name), JsonNode.class));
  }

  public Optional<JsonNode> getDomainByAddress(String address) {
    return Optional.ofNullable(call("domain_getDomainByAddress", List.of(address), JsonNode.class));
  }

  public List<JsonNode> querySmartContracts(Query query) {
    return call("domain_querySmartContracts", List.of(query), new TypeReference<List<JsonNode>>() {});
  }

  public Optional<JsonNode> getSmartContractByAddress(String address) {
    return Optional.ofNullable(call("domain_getSmartContractByAddress", List.of(address), JsonNode.class));
  }
}
