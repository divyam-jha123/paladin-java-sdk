package io.lfdt.paladin.sdk.bidx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import java.util.List;
import java.util.Optional;

public final class BlockIndexModule extends RpcModule {

  public BlockIndexModule(JsonRpcTransport transport) {
    super(transport);
  }

  public Optional<JsonNode> getBlockByNumber(long number) {
    return Optional.ofNullable(call("bidx_getBlockByNumber", List.of(number), JsonNode.class));
  }

  public Optional<JsonNode> getTransactionByHash(String hash) {
    return Optional.ofNullable(call("bidx_getTransactionByHash", List.of(hash), JsonNode.class));
  }

  public Optional<JsonNode> getTransactionByNonce(String from, long nonce) {
    return Optional.ofNullable(call("bidx_getTransactionByNonce", List.of(from, nonce), JsonNode.class));
  }

  public List<JsonNode> getBlockTransactionsByNumber(long blockNumber) {
    return call("bidx_getBlockTransactionsByNumber", List.of(blockNumber), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> getTransactionEventsByHash(String hash) {
    return call("bidx_getTransactionEventsByHash", List.of(hash), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> queryIndexedBlocks(Query query) {
    return call("bidx_queryIndexedBlocks", List.of(query), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> queryIndexedTransactions(Query query) {
    return call("bidx_queryIndexedTransactions", List.of(query), new TypeReference<List<JsonNode>>() {});
  }

  public List<JsonNode> queryIndexedEvents(Query query) {
    return call("bidx_queryIndexedEvents", List.of(query), new TypeReference<List<JsonNode>>() {});
  }

  public long getConfirmedBlockHeight() {
    // Paladin returns the block height as a 0x-prefixed hex string on the wire,
    // not a JSON number, so deserialize as String and decode.
    String hex = call("bidx_getConfirmedBlockHeight", List.of(), String.class);
    return hex == null ? 0L : Long.decode(hex);
  }

  public List<JsonNode> decodeTransactionEvents(String txHash, JsonNode abi, String resultFormat) {
    return call("bidx_decodeTransactionEvents", List.of(txHash, abi, resultFormat), new TypeReference<List<JsonNode>>() {});
  }
}
