package io.lfdt.paladin.sdk.pgroup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.types.PrivacyGroup;
import io.lfdt.paladin.sdk.types.PrivacyGroupInput;
import java.util.List;
import java.util.Optional;

public final class PgroupModule extends RpcModule {

  public PgroupModule(JsonRpcTransport transport) {
    super(transport);
  }

  public PrivacyGroup createGroup(PrivacyGroupInput input) {
    return call("pgroup_createGroup", List.of(input), PrivacyGroup.class);
  }

  public PrivacyGroup getGroupById(String domainName, String id) {
    return call("pgroup_getGroupById", List.of(domainName, id), PrivacyGroup.class);
  }

  public PrivacyGroup getGroupByAddress(String address) {
    return call("pgroup_getGroupByAddress", List.of(address), PrivacyGroup.class);
  }

  public List<PrivacyGroup> queryGroups(Query query) {
    return call("pgroup_queryGroups", List.of(query), new TypeReference<List<PrivacyGroup>>() {});
  }

  public List<PrivacyGroup> queryGroupsWithMember(String member, Query query) {
    return call("pgroup_queryGroupsWithMember", List.of(member, query), new TypeReference<List<PrivacyGroup>>() {});
  }

  public String sendTransaction(JsonNode txInput) {
    return call("pgroup_sendTransaction", List.of(txInput), String.class);
  }

  public JsonNode call(JsonNode callInput) {
    return call("pgroup_call", List.of(callInput), JsonNode.class);
  }

  public String sendMessage(JsonNode messageInput) {
    return call("pgroup_sendMessage", List.of(messageInput), String.class);
  }

  public Optional<JsonNode> getMessageById(String id) {
    return Optional.ofNullable(call("pgroup_getMessageById", List.of(id), JsonNode.class));
  }

  public List<JsonNode> queryMessages(Query query) {
    return call("pgroup_queryMessages", List.of(query), new TypeReference<List<JsonNode>>() {});
  }
}
