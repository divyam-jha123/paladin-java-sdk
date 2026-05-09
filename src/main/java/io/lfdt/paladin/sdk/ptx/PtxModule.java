package io.lfdt.paladin.sdk.ptx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.lfdt.paladin.sdk.internal.RpcModule;
import io.lfdt.paladin.sdk.query.Query;
import io.lfdt.paladin.sdk.rpc.JsonRpcTransport;
import io.lfdt.paladin.sdk.types.PreparedTransaction;
import io.lfdt.paladin.sdk.types.ReceiptListener;
import io.lfdt.paladin.sdk.types.StoredABI;
import io.lfdt.paladin.sdk.types.Transaction;
import io.lfdt.paladin.sdk.types.TransactionCall;
import io.lfdt.paladin.sdk.types.TransactionInput;
import io.lfdt.paladin.sdk.types.TransactionReceipt;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class PtxModule extends RpcModule {

  public PtxModule(JsonRpcTransport transport) {
    super(transport);
  }

  // submission

  public String sendTransaction(TransactionInput tx) {
    return call("ptx_sendTransaction", List.of(tx), String.class);
  }

  public List<String> sendTransactions(List<TransactionInput> txs) {
    return call("ptx_sendTransactions", List.of(txs), new TypeReference<List<String>>() {});
  }

  public String prepareTransaction(TransactionInput tx) {
    return call("ptx_prepareTransaction", List.of(tx), String.class);
  }

  public List<String> prepareTransactions(List<TransactionInput> txs) {
    return call("ptx_prepareTransactions", List.of(txs), new TypeReference<List<String>>() {});
  }

  public String updateTransaction(String id, TransactionInput tx) {
    return call("ptx_updateTransaction", List.of(id, tx), String.class);
  }

  public JsonNode call(TransactionCall call) {
    return call("ptx_call", List.of(call), JsonNode.class);
  }

  // queries

  public Optional<Transaction> getTransaction(String txId) {
    return Optional.ofNullable(call("ptx_getTransaction", List.of(txId), Transaction.class));
  }

  public Optional<Transaction> getTransactionFull(String txId) {
    return Optional.ofNullable(call("ptx_getTransactionFull", List.of(txId), Transaction.class));
  }

  public Optional<Transaction> getTransactionByIdempotencyKey(String key) {
    return Optional.ofNullable(call("ptx_getTransactionByIdempotencyKey", List.of(key), Transaction.class));
  }

  public List<Transaction> queryTransactions(Query query) {
    return call("ptx_queryTransactions", List.of(query), new TypeReference<List<Transaction>>() {});
  }

  public List<Transaction> queryTransactionsFull(Query query) {
    return call("ptx_queryTransactionsFull", List.of(query), new TypeReference<List<Transaction>>() {});
  }

  public List<Transaction> queryPendingTransactions(Query query, boolean full) {
    return call("ptx_queryPendingTransactions", List.of(query, full), new TypeReference<List<Transaction>>() {});
  }

  // receipts

  public Optional<TransactionReceipt> getTransactionReceipt(String txId) {
    return Optional.ofNullable(call("ptx_getTransactionReceipt", List.of(txId), TransactionReceipt.class));
  }

  public Optional<TransactionReceipt> getTransactionReceiptFull(String txId) {
    return Optional.ofNullable(call("ptx_getTransactionReceiptFull", List.of(txId), TransactionReceipt.class));
  }

  public Optional<JsonNode> getDomainReceipt(String domain, String txId) {
    return Optional.ofNullable(call("ptx_getDomainReceipt", List.of(domain, txId), JsonNode.class));
  }

  public Optional<JsonNode> getStateReceipt(String txId) {
    return Optional.ofNullable(call("ptx_getStateReceipt", List.of(txId), JsonNode.class));
  }

  public List<TransactionReceipt> queryTransactionReceipts(Query query) {
    return call("ptx_queryTransactionReceipts", List.of(query), new TypeReference<List<TransactionReceipt>>() {});
  }

  public Optional<List<String>> getTransactionDependencies(String txId) {
    return Optional.ofNullable(
        call("ptx_getTransactionDependencies", List.of(txId), new TypeReference<List<String>>() {}));
  }

  public Optional<TransactionReceipt> pollForReceipt(String txId, Duration timeout) {
    return pollForReceipt(txId, timeout, Duration.ofMillis(100));
  }

  public Optional<TransactionReceipt> pollForReceipt(String txId, Duration timeout, Duration interval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      Optional<TransactionReceipt> r = getTransactionReceipt(txId);
      if (r.isPresent()) return r;
      if (System.nanoTime() >= deadline) return Optional.empty();
      try {
        Thread.sleep(interval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
  }

  // prepared txs

  public Optional<PreparedTransaction> getPreparedTransaction(String txId) {
    return Optional.ofNullable(
        call("ptx_getPreparedTransaction", List.of(txId), PreparedTransaction.class));
  }

  public List<PreparedTransaction> queryPreparedTransactions(Query query) {
    return call("ptx_queryPreparedTransactions", List.of(query), new TypeReference<List<PreparedTransaction>>() {});
  }

  // ABI

  public void storeABI(JsonNode abi) {
    callVoid("ptx_storeABI", List.of(abi));
  }

  public StoredABI getStoredABI(String hash) {
    return call("ptx_getStoredABI", List.of(hash), StoredABI.class);
  }

  public List<StoredABI> queryStoredABIs(Query query) {
    return call("ptx_queryStoredABIs", List.of(query), new TypeReference<List<StoredABI>>() {});
  }

  public JsonNode decodeCall(String callData, String dataFormat) {
    return call("ptx_decodeCall", List.of(callData, dataFormat), JsonNode.class);
  }

  public JsonNode decodeEvent(List<String> topics, String data) {
    return call("ptx_decodeEvent", List.of(topics, data, ""), JsonNode.class);
  }

  public JsonNode decodeError(String revertError, String dataFormat) {
    return call("ptx_decodeError", List.of(revertError, dataFormat), JsonNode.class);
  }

  // key resolution

  public String resolveVerifier(String lookup, String algorithm, String verifierType) {
    return call("ptx_resolveVerifier", List.of(lookup, algorithm, verifierType), String.class);
  }

  // receipt listeners

  public boolean createReceiptListener(ReceiptListener listener) {
    return Boolean.TRUE.equals(call("ptx_createReceiptListener", List.of(listener), Boolean.class));
  }

  public List<ReceiptListener> queryReceiptListeners(Query query) {
    return call("ptx_queryReceiptListeners", List.of(query), new TypeReference<List<ReceiptListener>>() {});
  }

  public Optional<ReceiptListener> getReceiptListener(String name) {
    return Optional.ofNullable(call("ptx_getReceiptListener", List.of(name), ReceiptListener.class));
  }

  public boolean startReceiptListener(String name) {
    return Boolean.TRUE.equals(call("ptx_startReceiptListener", List.of(name), Boolean.class));
  }

  public boolean stopReceiptListener(String name) {
    return Boolean.TRUE.equals(call("ptx_stopReceiptListener", List.of(name), Boolean.class));
  }

  public boolean deleteReceiptListener(String name) {
    return Boolean.TRUE.equals(call("ptx_deleteReceiptListener", List.of(name), Boolean.class));
  }
}
