package io.lfdt.paladin.sdk.pstate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.types.Schema;
import io.lfdt.paladin.sdk.types.State;
import io.lfdt.paladin.sdk.types.StateStatus;
import java.util.List;

public final class PStateModule extends RpcModule {

  public PStateModule(JsonRpcTransport transport) {
    super(transport);
  }

  public List<Schema> listSchemas(String domain) {
    return call("pstate_listSchemas", List.of(domain), new TypeReference<List<Schema>>() {});
  }

  public Schema getSchemaById(String domain, String schemaId) {
    return call("pstate_getSchemaById", List.of(domain, schemaId), Schema.class);
  }

  public State storeState(String domain, String contractAddress, String schema, JsonNode data) {
    return call("pstate_storeState", List.of(domain, contractAddress, schema, data), State.class);
  }

  public List<State> queryStates(String domain, String schema, Query query, StateStatus status) {
    return call("pstate_queryStates", List.of(domain, schema, query, status), new TypeReference<List<State>>() {});
  }

  public List<State> queryContractStates(String domain, String contractAddress, String schema, Query query, StateStatus status) {
    return call("pstate_queryContractStates", List.of(domain, contractAddress, schema, query, status), new TypeReference<List<State>>() {});
  }

  public List<State> queryNullifiers(String domain, String schema, Query query, StateStatus status) {
    return call("pstate_queryNullifiers", List.of(domain, schema, query, status), new TypeReference<List<State>>() {});
  }

  public List<State> queryContractNullifiers(String domain, String contractAddress, String schema, Query query, StateStatus status) {
    return call("pstate_queryContractNullifiers", List.of(domain, contractAddress, schema, query, status), new TypeReference<List<State>>() {});
  }
}
